package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
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
     *
     * @param collector 坐标收集器，每经过一格铁轨调用一次
     * @return 停止原因及位置
     */
    public WalkResult walkToNextNode(CoordCollector collector) {
        // 记录段首累计里程，段尾相减即为本段沿轨道的真实长度
        double startMoved = wp.movedTotal;
        int steps = 0;
        while (true) {
            RailState state = wp.state;
            Block railBlock = state.railBlock();
            collector.accept(railBlock);

            // 处理当前铁轨的 addtag / remtag（影响原版 switcher 后续选向）
            applyTagSigns(state.railPiece().signs());

            // 前进一格
            if (!wp.moveFull()) {
                return new WalkResult(StopReason.END, railBlock, null, state.enterDirection(), wp.movedTotal - startMoved);
            }

            if (++steps >= maxStepsPerSegment) {
                // 兜底：疑似无控制牌物理环路，按断轨处理避免死循环
                return new WalkResult(StopReason.END, wp.state.railBlock(), null, wp.state.enterDirection(), wp.movedTotal - startMoved);
            }

            // 检查新铁轨上的节点控制牌
            RailLookup.TrackedSign nodeSign = findNodeSign(wp.state.railPiece());
            if (nodeSign != null) {
                Block nodeRail = wp.state.railBlock();
                collector.accept(nodeRail);
                StopReason reason = signType(nodeSign);
                return new WalkResult(reason, nodeRail, nodeSign, wp.state.enterDirection(), wp.movedTotal - startMoved);
            }
        }
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
         * 收到一格铁轨坐标。
         *
         * @param railBlock 铁轨方块
         */
        void accept(Block railBlock);
    }
}
