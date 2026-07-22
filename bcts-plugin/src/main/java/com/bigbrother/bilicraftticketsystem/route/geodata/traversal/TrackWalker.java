package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import lombok.Setter;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单段轨道行走器：基于 traincarts 的 {@link TrackWalkingPoint} + 一节不动的矿车，从给定铁轨 + 方向
 * 出发沿轨道前进，逐格记录坐标，直到遇到下一个节点控制牌（platform / bcswitcher）或轨道结束。
 * <p>
 * 关键设计（为什么用矿车而不是无矿车的自定义 Navigator）：
 * <ul>
 *   <li>通过 {@link TrackWalkingPoint#setFollowPredictedPath(MinecartMember)} 让 TC 自己的寻路
 *       预测驱动行走。这样所有 switcher（包括 <b>原版 TC switcher</b> 和本插件 bcswitcher）都按
 *       矿车携带的 tag 决定走向，无需自己复刻 TC 的方向选择逻辑（计数器、from-direction 等）。</li>
 *   <li>沿途的 {@code addtag} / {@code remtag} 控制牌会真实改写矿车 tag，使原版 switcher 的
 *       按-tag 转折逻辑正常生效。</li>
 *   <li>要让本段沿某条线路走，只需给矿车加上该线路 id 作为 tag（{@link #setLineTag}）：
 *       bcswitcher 在预测寻路时读到该 tag 即导向对应方向。</li>
 * </ul>
 * <p>
 * 矿车不会移动（速度上限 0），仅作为 walking point 的 tag 载体。使用完必须调用 {@link #destroy}。
 * <p>
 * 注意：行走依赖 TC 实时寻路物理，必须在主线程执行，离线无法完整测试。
 */
public class TrackWalker {
    /**
     * 一步行走的停止原因。
     */
    public enum StopReason {
        /**
         * 到达一个 platform 控制牌（车站节点）。
         */
        PLATFORM,
        /**
         * 到达一个 bcswitcher 控制牌（道岔节点）。
         */
        SWITCHER,
        /**
         * 轨道结束（断轨 / 死路 / 检测到环路）。
         */
        END
    }

    /**
     * 单段行走结果。
     *
     * @param direction 到达停止位置时的行走方向（供下一段行走作为出发方向）。
     * @param length    本段沿轨道的真实长度（{@link TrackWalkingPoint#movedTotal} 段首段尾之差，
     *                  按 RailPath 实际移动距离计，曲线/斜坡/对角轨道均精确，优于数铁轨方块数）。
     */
        public record WalkResult(StopReason reason, Block railBlock, RailLookup.TrackedSign sign, Vector direction,
                                 double length) {
    }

    private final TrackWalkingPoint wp;
    private final MinecartMember<?> member;
    /**
     * 当前用于导向 bcswitcher 的线路 id tag（每段可不同）。
     */
    private String lineTag;
    /**
     * 当前强制出向 tag（{@link com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher#FORCE_DIR_TAG_PREFIX}
     * 前缀）。遍历器在道岔节点 fork 时设置，使本段离开该道岔时按指定方向走（绕过 lineId 选向）。
     */
    private String forceDirTag;
    /**
     * 单段行走最多前进的铁轨格数（兜底）。loopFilter 已关闭（环线闭合段会重走起点附近铁轨，
     * 不能当成环路截断），改由此上限防止真正无控制牌的物理环路造成死循环。
     */
    @Setter
    private int maxStepsPerSegment = 100000;
    /**
     * TCC 云轨曲线采样步长（方块）。仅在当前轨为 TCCoasters 云轨时生效：按此距离小步采样真实浮点位置
     * 以还原弧线。{@code <=0} 表示关闭云轨密采（云轨也退回逐段整点采样）。普通铁轨采样与此无关。
     */
    @Setter
    private double sampleStep = 0.5;

    /**
     * 创建行走器（会在 startRail 处生成一节不动的矿车）。必须在主线程调用。
     *
     * @param startRail      起始铁轨方块
     * @param startDirection 起始方向向量
     */
    public TrackWalker(Block startRail, Vector startDirection) {
        this.member = MinecartMemberStore.spawn(TrainCarts.plugin, startRail.getLocation(), EntityType.MINECART);
        this.member.getGroup().getProperties().setDefault();
        this.member.getGroup().getProperties().setKeepChunksLoaded(true);
        this.member.getGroup().getProperties().setSpeedLimit(0);
        this.member.setOrientation(Quaternion.fromLookDirection(startDirection));
        this.member.getGroup().getProperties().setTrainName("rail_geo_" + UUID.randomUUID());

        this.wp = new TrackWalkingPoint(startRail.getLocation(), startDirection);
        // 不开 loopFilter：环线闭合时会重新经过起点附近已访问的铁轨，loopFilter 会把它误判为
        // 断轨并提前停止，导致环线最后一段（回到起始节点）走不完、环闭不上。改用步数上限兜底。
        this.wp.setLoopFilter(false);
        this.wp.setFollowPredictedPath(this.member);
        this.wp.skipFirst();
    }

    /**
     * 设置本段行走用于导向 bcswitcher 的线路 id（替换上一个线路 tag）。
     * <p>
     * bcswitcher 预测寻路时读取矿车 tag 选向，因此设置该 tag 即让本段沿对应线路走。
     *
     * @param lineId 线路 id（null 或空表示不强制，按默认路径 / 原版 switcher 逻辑）
     */
    public void setLineTag(String lineId) {
        if (this.lineTag != null) {
            this.member.getProperties().removeTags(this.lineTag);
        }
        this.lineTag = (lineId == null || lineId.isEmpty()) ? null : lineId;
        if (this.lineTag != null) {
            this.member.getProperties().addTags(this.lineTag);
        }
        refreshPredictedPath();
    }

    /**
     * 设置本段离开起始 bcswitcher 时的<b>强制出向</b>。
     * <p>
     * 遍历器在道岔节点 fork 时调用：给矿车打 {@code bcsw_force_dir:<dir>} tag，
     * {@link com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher#predictPathFinding}
     * 读到后强制把道岔切到该方向（优先级高于 lineId 选向）。两个相邻节点之间不会再有 bcswitcher，
     * 故该 tag 只影响正在离开的这个道岔。
     *
     * @param directionStr 出向字符串（如 "e"、"l"、"f"；null 或空表示清除强制出向）
     */
    public void setForcedDirection(String directionStr) {
        if (this.forceDirTag != null) {
            this.member.getProperties().removeTags(this.forceDirTag);
            this.forceDirTag = null;
        }
        if (directionStr != null && !directionStr.isEmpty()) {
            this.forceDirTag = SignActionBcswitcher.FORCE_DIR_TAG_PREFIX + directionStr;
            this.member.getProperties().addTags(this.forceDirTag);
        }
        refreshPredictedPath();
    }

    /**
     * 沿轨道前进，逐格把坐标交给 collector，直到遇到节点控制牌或轨道结束。
     * <p>
     * 起点所在铁轨上的控制牌不触发停止（避免在出发点立刻停下）；只检测前进途中新铁轨上的牌。
     * 沿途处理 addtag / remtag，使原版 switcher 的按-tag 转折逻辑正常生效。
     * <p>
     * <b>采样精度按轨道类型区分</b>：
     * <ul>
     *   <li><b>普通铁轨</b>（原版轨等）：与历史行为完全一致——每格 {@link TrackWalkingPoint#moveFull()}
     *       跨过整段 rail path，取铁轨方块的<b>整数</b>坐标采样一次。逻辑、拓扑、里程一字不变。</li>
     *   <li><b>TCC 云轨</b>（{@link #isCoasterRail}，且 {@link #sampleStep} &gt; 0）：改用
     *       {@link TrackWalkingPoint#moveStep(double)} 按 {@code sampleStep} 距离小步前进，取列车
     *       <b>真实浮点位置</b>（{@link RailState#positionLocation()}）采样，使弧线在 geojson 里
     *       画成弧而非直线。控制流（节点检测 / 断轨 / 里程 / tag）与普通轨完全共用。</li>
     * </ul>
     * 两种前进都经由 TC 的 {@code loadNextRail}（在轨道边界加载下一段、遵循预测寻路），故云轨密采
     * 不改变走向选择；节点坐标一律锚在铁轨方块整数坐标（见下方节点采样），与 NodeId / 起点登记对齐。
     *
     * @param collector 坐标收集器，每采样一次调用一次（普通轨每格一次、云轨每小步一次）
     * @return 停止原因及位置
     */
    public WalkResult walkToNextNode(CoordCollector collector) {
        // 记录段首累计里程，段尾相减即为本段沿轨道的真实长度
        double startMoved = wp.movedTotal;
        int steps = 0;
        // 上一次做过「节点牌检测 + tag 处理」的铁轨方块。云轨密采时一步可能仍停在同一格铁轨内，
        // 只有铁轨方块变化（跨过 rail piece）才需要重新检测——避免同格重复处理、也保证不漏格。
        Block lastProcessedRail = null;
        while (true) {
            RailState state = wp.state;
            Block railBlock = state.railBlock();
            boolean coaster = sampleStep > 0 && isCoasterRail(state);

            // 采样当前点：云轨取真实浮点位置（还原弧线），普通轨取铁轨方块整数坐标（历史行为不变）
            if (coaster) {
                org.bukkit.Location pos = state.positionLocation();
                collector.accept(pos.getX(), pos.getY(), pos.getZ(), true);
            } else {
                collector.accept(railBlock.getX(), railBlock.getY(), railBlock.getZ(), false);
            }

            // 处理当前铁轨的 addtag / remtag（影响原版 switcher 后续选向）。云轨密采时同一格只处理一次。
            if (!sameBlock(lastProcessedRail, railBlock)) {
                applyTagSigns(state.railPiece().signs());
                lastProcessedRail = railBlock;
            }

            // 前进：普通轨一次跨完当前段；云轨按采样步长小步推进（两者都经 loadNextRail 处理轨道边界与预测寻路）。
            // 关键：云轨采样步长不能大于 1 格，否则一步可能跨过整格铁轨、漏掉其上的节点牌 / addtag。
            // 故这里对云轨步长再夹一个 1.0 上限（采样更密无害，只影响顶点数），保证逐格都会被下方检测覆盖。
            boolean moved = coaster ? wp.moveStep(Math.min(sampleStep, 1.0)) : wp.moveFull();
            if (!moved) {
                return new WalkResult(StopReason.END, railBlock, null, state.enterDirection(), wp.movedTotal - startMoved);
            }

            if (++steps >= maxStepsPerSegment) {
                // 兜底：疑似无控制牌物理环路，按断轨处理避免死循环
                return new WalkResult(StopReason.END, wp.state.railBlock(), null, wp.state.enterDirection(), wp.movedTotal - startMoved);
            }

            // 检查新铁轨上的节点控制牌。云轨密采时可能多步仍在同格（还没跨到有牌的新格），
            // 只有铁轨方块变化后才需要检测——同格重复检测无意义，且不会因步长大而跳过任何一格。
            Block newRail = wp.state.railBlock();
            if (!sameBlock(lastProcessedRail, newRail)) {
                RailLookup.TrackedSign nodeSign = findNodeSign(wp.state.railPiece());
                if (nodeSign != null) {
                    // 节点坐标一律用铁轨方块整数坐标（锚定 NodeId / 起点登记 / 去重），按普通轨精度处理
                    collector.accept(newRail.getX(), newRail.getY(), newRail.getZ(), false);
                    StopReason reason = signType(nodeSign);
                    return new WalkResult(reason, newRail, nodeSign, wp.state.enterDirection(), wp.movedTotal - startMoved);
                }
            }
        }
    }

    /**
     * 判断两个铁轨方块是否为同一格（含 null 处理）。用于云轨密采时避免同格重复检测节点牌 / tag。
     *
     * @param a 铁轨方块（可为 null）
     * @param b 铁轨方块（可为 null）
     * @return 同一格返回 true
     */
    private boolean sameBlock(Block a, Block b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ()
                && a.getWorld().equals(b.getWorld());
    }

    /**
     * 判断当前所在铁轨是否为 TCCoasters 的云轨（虚拟曲线轨）。
     * <p>
     * 用 {@link RailType} 的类名字符串判断，避免对 TCCoasters 产生编译期硬依赖（它是可选的运行时插件）。
     *
     * @param state 当前行走状态
     * @return 当前轨为 TCCoasters 云轨返回 true
     */
    private boolean isCoasterRail(RailState state) {
        RailType type = state.railType();
        return type != null && "CoasterRailType".equals(type.getClass().getSimpleName());
    }

    /**
     * 处理一个铁轨片上的 addtag / remtag 控制牌，改写矿车 tag。
     * <p>
     * 只处理 always-on（被动常开）的 addtag / remtag，与旧实现保持一致（避免红石态干扰）。
     *
     * @param signs 铁轨片上的控制牌
     */
    private void applyTagSigns(RailLookup.TrackedSign[] signs) {
        if (signs == null) {
            return;
        }
        boolean changed = false;
        for (RailLookup.TrackedSign sign : signs) {
            String line2 = sign.getLine(2).trim().toLowerCase();
            String tag = sign.getLine(3).trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (sign.getHeader().isAlwaysOn() && line2.startsWith("addtag")) {
                member.getProperties().addTags(tag);
                changed = true;
            } else if (line2.startsWith("remtag")) {
                member.getProperties().removeTags(tag);
                changed = true;
            }
        }
        if (changed) {
            refreshPredictedPath();
        }
    }

    /**
     * tag 改变后刷新预测路径，使 walking point 立即按新 tag 选向。
     */
    private void refreshPredictedPath() {
        member.getProperties().getHolder().onPropertiesChanged();
        wp.setFollowPredictedPath(member);
    }

    /**
     * 在一个铁轨片上查找节点控制牌（platform 或 bcswitcher）。
     *
     * @param railPiece 铁轨片
     * @return 找到的控制牌，无则 null
     */
    private RailLookup.TrackedSign findNodeSign(com.bergerkiller.bukkit.tc.controller.components.RailPiece railPiece) {
        if (railPiece == null) {
            return null;
        }
        RailLookup.TrackedSign[] signs = railPiece.signs();
        if (signs == null) {
            return null;
        }
        for (RailLookup.TrackedSign sign : signs) {
            if (signType(sign) != null) {
                return sign;
            }
        }
        return null;
    }

    /**
     * 判断控制牌类型（看第二行）。
     *
     * @param sign 控制牌
     * @return PLATFORM / SWITCHER，非节点牌返回 null
     */
    private StopReason signType(RailLookup.TrackedSign sign) {
        String line1 = sign.getLine(1).trim().toLowerCase();
        if (line1.startsWith("platform")) {
            return StopReason.PLATFORM;
        }
        if (line1.startsWith("bcswitcher")) {
            return StopReason.SWITCHER;
        }
        return null;
    }

    /**
     * 收集当前停止位置铁轨上、其进入方向匹配本次<b>到达方向</b>的所有 bcswitcher 出向分支。
     * <p>
     * 一格铁轨下可能有多块 bcswitcher 牌（依次处理，不合并）：每块牌的牌头声明了它适用的进入方向
     * （如 {@code [+train:lf]}）。只有牌头进入方向匹配本次到达方向的牌，其出向才纳入；不匹配的牌
     * （为反方向来车准备）跳过。这样有向图的「入边方向 → 出边」关系才正确。
     *
     * @param nodeRailPiece 节点所在铁轨片
     * @return 匹配的出向分支（跨多块牌合并收集，按声明顺序）
     */
    public List<BcSwitcherBranch> collectSwitcherBranches(RailPiece nodeRailPiece) {
        List<BcSwitcherBranch> result = new ArrayList<>();
        if (nodeRailPiece == null) {
            return result;
        }
        RailLookup.TrackedSign[] signs = nodeRailPiece.signs();
        if (signs == null) {
            return result;
        }
        Vector arrival = wp.state.motionVector();
        for (RailLookup.TrackedSign sign : signs) {
            if (signType(sign) != StopReason.SWITCHER) {
                continue;
            }
            SignActionEvent event = new SignActionEvent(sign, member);
            // 牌头声明了进入方向时，只在到达方向匹配时纳入；未声明则视为不限方向（兼容，但建牌已强制声明）
            if (event.isWatchedDirectionsDefined() && !event.isWatchedDirection(arrival)) {
                continue;
            }
            for (int i = 2; i <= 3; i++) {
                BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(sign.getLine(i));
                if (branch != null) {
                    result.add(branch);
                }
            }
        }
        return result;
    }

    /**
     * 销毁行走用的矿车。使用完必须调用。
     */
    public void destroy() {
        if (member != null && !member.isUnloaded()) {
            member.getGroup().destroy();
        }
    }

    /**
     * 坐标收集回调。
     */
    public interface CoordCollector {
        /**
         * 收到一个采样点的世界坐标。
         * <p>
         * 普通铁轨传入铁轨方块的整数坐标（每格一次）；TCC 云轨传入列车真实浮点位置（每采样步长一次）。
         *
         * @param x       世界 x 坐标
         * @param y       世界 y 坐标
         * @param z       世界 z 坐标
         * @param coaster 该采样点是否取自 TCC 云轨（true 表示真实浮点位置，需按曲线精度简化）
         */
        void accept(double x, double y, double z, boolean coaster);
    }
}
