package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirection;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirectionFromJunction;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirectionToFace;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * @param sign      代表性节点控制牌：{@link StopReason#PLATFORM} 为首块 platform 牌（取站名用），
     *                  {@link StopReason#SWITCHER} 为首块 bcswitcher 牌。轨道结束时为 null。
     * @param nodeSigns 停止位置铁轨上的<b>全部</b>节点控制牌（platform + bcswitcher）。一格铁轨可挂多块
     *                  牌，出向必须跨全部牌收集——只看 {@link #sign} 会漏掉同格其它道岔牌的出向。
     * @param mixedNode 停止位置同格<b>既有 bcswitcher 又有 platform</b>（不符合标准车站格式，按道岔处理
     *                  并告警，见 {@link #findNodeSigns}）。
     * @param direction 到达停止位置时的行走方向（供下一段行走作为出发方向）。
     * @param length    本段沿轨道的真实长度（{@link TrackWalkingPoint#movedTotal} 段首段尾之差，
     *                  按 RailPath 实际移动距离计，曲线/斜坡/对角轨道均精确，优于数铁轨方块数）。
     * @param arrivalState 到达停止位置时的铁轨状态快照（已复制）。道岔 fork 时用它起步
     *                  （{@link #TrackWalker(RailState)}），保证离开道岔时的进入方向判定与到达时一致。
     *                  轨道结束时为 null。
     */
    public record WalkResult(StopReason reason, Block railBlock, RailLookup.TrackedSign sign,
                             List<RailLookup.TrackedSign> nodeSigns, boolean mixedNode, Vector direction,
                             double length, RailState arrivalState) {
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
     * 是否从<b>节点到达状态</b>起步（{@link #TrackWalker(RailState)}，道岔 fork）。
     * <p>
     * TCC 云轨上，到达状态位于节点铁轨片（节点所在连接的近端半段）的<b>入口</b>，离节点还有一段距离；
     * 若照常从该处采样，出边会先把入边已走过的这半段再画一遍，且起点不在节点上——前端表现为道岔附近
     * 线路断开、像分叉。故从到达状态起步时，云轨上跳过起始铁轨片内的采样、以节点坐标作为首点，
     * 里程也从离开起始铁轨片处算起（见 {@link #walkToNextNode}）。
     */
    private final boolean startFromArrival;

    /**
     * 创建行走器（会在 startRail 处生成一节不动的矿车）。必须在主线程调用。
     *
     * @param startRail      起始铁轨方块
     * @param startDirection 起始方向向量
     */
    public TrackWalker(Block startRail, Vector startDirection) {
        this(startRail, startDirection, new TrackWalkingPoint(startRail.getLocation(), startDirection), false);
    }

    /**
     * 从一个<b>真实的到达状态</b>创建行走器（会在该状态所在铁轨处生成一节不动的矿车）。必须在主线程调用。
     * <p>
     * 用于道岔 fork：沿用上一段到达道岔时的 {@link RailState}（见 {@link WalkResult#arrivalState()}）起步。
     * 若改用「铁轨方块 + 方向向量」合成起步状态，进入位置会落在方块角点，斜向轨道上
     * {@link RailState#enterFace()} 会算错，导致 bcswitcher 的 {@code isEnterActivated} 判定失败、
     * 强制出向不生效（列车按默认走向弹回）。真实到达状态的进入位置就是实际入口，判定与到达时一致。
     *
     * @param startState 起始铁轨状态（内部会复制，不修改传入对象）
     */
    public TrackWalker(RailState startState) {
        this(startState.railBlock(), startState.enterDirection(), new TrackWalkingPoint(startState), true);
    }

    /**
     * 公共初始化：生成载 tag 的矿车并配置行走点。
     *
     * @param startRail        起始铁轨方块（矿车生成位置）
     * @param startDirection   起始方向向量（矿车朝向）
     * @param wp               已定位到起点的行走点
     * @param startFromArrival 是否从节点到达状态起步（见 {@link #startFromArrival}）
     */
    private TrackWalker(Block startRail, Vector startDirection, TrackWalkingPoint wp, boolean startFromArrival) {
        this.startFromArrival = startFromArrival;
        this.member = MinecartMemberStore.spawn(TrainCarts.plugin, startRail.getLocation(), EntityType.MINECART);
        this.member.getGroup().getProperties().setDefault();
        this.member.getGroup().getProperties().setKeepChunksLoaded(true);
        this.member.getGroup().getProperties().setSpeedLimit(0);
        this.member.setOrientation(Quaternion.fromLookDirection(startDirection));
        this.member.getGroup().getProperties().setTrainName("rail_geo_" + UUID.randomUUID());

        this.wp = wp;
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
        // 上一次做过 tag 处理（addtag / remtag）的铁轨方块。云轨密采时一步可能仍停在同一格铁轨内，
        // 只有铁轨方块变化（跨过 rail piece）才需要重新处理——避免同格重复加减 tag。
        Block lastTaggedRail = null;
        // 已检测过节点牌的控制牌集合。出发点所在铁轨的节点牌先行记入，使本段不会在出发点立刻停下；
        // 之后逐牌判断而非整格跳过——云轨可能在同格内连续推进多步、或离开后又回到同一格，
        // 按格去重会漏掉该格上的节点牌。
        // 用 TrackedSign#getUniqueKey() 而非对象身份作为 key：TrackedSign 的 equals 是身份比较，
        // 而 TC 的铁轨缓存可能在行走途中刷新、同一块牌换成新的 TrackedSign 实例，按身份去重会失效
        // （出发点的牌再次出现就会被当成新牌、让本段在原地停下）。getUniqueKey 对真实牌返回按
        // 牌坐标 + 正反面比较的值对象，跨实例稳定。
        Set<Object> checkedSigns = new HashSet<>();
        // 出发点铁轨同样可能命中 TC 缓存漏扫控制牌的问题（见 ensureSignsDiscovered），先补扫再登记
        ensureSignsDiscovered();
        collectSignsInto(wp.state.railPiece(), checkedSigns);
        // 从节点到达状态起步且起点为云轨：起始铁轨片内的采样先缓冲，离开该铁轨片时裁到节点处再输出
        // （见 startFromArrival）。普通铁轨的起始铁轨片就是节点所在格，无需裁剪。
        Block startBlock = wp.state.railBlock();
        List<double[]> trimBuffer = startFromArrival && sampleStep > 0 && isCoasterRail(wp.state)
                ? new ArrayList<>() : null;
        while (true) {
            RailState state = wp.state;
            Block railBlock = state.railBlock();
            boolean coaster = sampleStep > 0 && isCoasterRail(state);

            if (trimBuffer != null && !sameBlock(startBlock, railBlock)) {
                startMoved = flushTrimmed(trimBuffer, startBlock, collector, startMoved);
                trimBuffer = null;
            }

            // 采样当前点：云轨取真实浮点位置（还原弧线），普通轨取铁轨方块整数坐标（历史行为不变）
            if (trimBuffer != null) {
                Location pos = state.positionLocation();
                trimBuffer.add(new double[]{pos.getX(), pos.getY(), pos.getZ(), wp.movedTotal});
            } else if (coaster) {
                Location pos = state.positionLocation();
                collector.accept(pos.getX(), pos.getY(), pos.getZ(), true);
            } else {
                collector.accept(railBlock.getX(), railBlock.getY(), railBlock.getZ(), false);
            }

            // 处理当前铁轨的 addtag / remtag（影响原版 switcher 后续选向）。云轨密采时同一格只处理一次。
            if (!sameBlock(lastTaggedRail, railBlock)) {
                applyTagSigns(state.railPiece().signs());
                lastTaggedRail = railBlock;
            }

            // 前进：普通轨一次跨完当前段；云轨按采样步长小步推进（两者都经 loadNextRail 处理轨道边界与预测寻路）。
            // 关键：云轨采样步长不能大于 1 格，否则一步可能跨过整格铁轨、漏掉其上的节点牌 / addtag。
            // 故这里对云轨步长再夹一个 1.0 上限（采样更密无害，只影响顶点数），保证逐格都会被下方检测覆盖。
            boolean moved = coaster ? wp.moveStep(Math.min(sampleStep, 1.0)) : wp.moveFull();
            if (!moved) {
                // moveFull() 走 MAX_VALUE，只有真正到轨尾（NO_RAIL）才返回 false；但 moveStep(limit)
                // 每次「走满 limit 距离」就会返回 false 并置 LIMIT_REACHED——这是云轨密采每一小步的
                // 正常结果，不是断轨。若在此把它当 END 直接停，云轨段会在第一小步（约 sampleStep 格）
                // 就中断，导致后续车站遍历不到（报「未在遍历中到达」）。故：LIMIT_REACHED 视为一步走完，
                // 继续采样；只有 NO_RAIL / LOOP_DETECTED / CYCLIC_PATH / 寻路中止等真实失败才结束本段。
                if (wp.failReason == TrackWalkingPoint.FailReason.LIMIT_REACHED) {
                    wp.failReason = TrackWalkingPoint.FailReason.NONE;
                } else {
                    if (trimBuffer != null) {
                        startMoved = flushTrimmed(trimBuffer, startBlock, collector, startMoved);
                    }
                    return endResult(railBlock, state.enterDirection(), startMoved);
                }
            }

            if (++steps >= maxStepsPerSegment) {
                // 兜底：疑似无控制牌物理环路，按断轨处理避免死循环
                if (trimBuffer != null) {
                    startMoved = flushTrimmed(trimBuffer, startBlock, collector, startMoved);
                }
                return endResult(wp.state.railBlock(), wp.state.enterDirection(), startMoved);
            }

            // 检查新位置铁轨上「尚未检测过」的节点控制牌：逐牌去重而非整格跳过。一格可挂多块节点牌，
            // 且云轨可能在同格内连续推进多步或离开后回到同格，按格去重会漏牌。
            Block newRail = wp.state.railBlock();
            // 进入新铁轨格：补扫可能被 TC 缓存漏掉的控制牌，必要时重新预测本格道岔走向
            if (!sameBlock(railBlock, newRail)) {
                ensureSignsDiscovered();
            }
            NodeSigns found = findNodeSigns(wp.state.railPiece(), checkedSigns);
            if (found != null) {
                if (trimBuffer != null) {
                    startMoved = flushTrimmed(trimBuffer, startBlock, collector, startMoved);
                }
                double length = wp.movedTotal - startMoved;
                // 云轨：到达节点铁轨片时列车还在该片入口（节点所在连接的近端半段起点），补采到节点处，
                // 否则这半段只剩一条直弦、且与出边错开（出边从节点起画）。行走点本身不动，到达状态不变。
                if (sampleStep > 0 && isCoasterRail(wp.state)) {
                    length += sampleToNode(newRail, collector);
                }
                // 节点坐标一律用铁轨方块整数坐标（锚定 NodeId / 起点登记 / 去重），按普通轨精度处理
                collector.accept(newRail.getX(), newRail.getY(), newRail.getZ(), false);
                return new WalkResult(found.reason(), newRail, found.primary(), found.all(), found.mixed(),
                        wp.state.enterDirection(), length, wp.state.clone());
            }
        }
    }

    /**
     * 云轨到达节点时，从当前位置沿当前铁轨片的路径补采样到<b>离节点最近处</b>，返回补走的长度。
     * <p>
     * TCC 把每条连接按中点拆成两半，分别归到近端节点的铁轨方块上；同一节点两侧的半段还会连成一条路径。
     * 所以行走点刚进入节点铁轨片时，离节点还有约半条连接；而这条路径走到尽头可能已越过节点、进入出向半段。
     * 故沿路径采样到尽头，再截在离节点铁轨方块中心最近的点。用的是状态副本，不推进行走点。
     *
     * @param nodeRail  节点铁轨方块
     * @param collector 坐标收集器
     * @return 从当前位置到截断点的沿轨长度
     */
    private double sampleToNode(Block nodeRail, CoordCollector collector) {
        List<double[]> samples = samplePath(wp.state, wp.currentRailPath, Math.min(sampleStep, 1.0));
        int nearest = nearestIndex(samples, nodeRail);
        for (int i = 0; i <= nearest; i++) {
            double[] s = samples.get(i);
            collector.accept(s[0], s[1], s[2], true);
        }
        return samples.get(nearest)[3];
    }

    /**
     * 把起始铁轨片内缓冲的采样裁到节点处输出：先输出节点整数坐标（与入边终点一致），再输出越过节点之后的采样。
     *
     * @param buffer     起始铁轨片内的采样（{@code [x, y, z, movedTotal]}）
     * @param nodeRail   起点节点铁轨方块
     * @param collector  坐标收集器
     * @param startMoved 原段首累计里程
     * @return 新的段首累计里程（截断点处），本段长度从节点算起
     */
    private double flushTrimmed(List<double[]> buffer, Block nodeRail, CoordCollector collector, double startMoved) {
        collector.accept(nodeRail.getX(), nodeRail.getY(), nodeRail.getZ(), false);
        if (buffer.isEmpty()) {
            return startMoved;
        }
        int nearest = nearestIndex(buffer, nodeRail);
        for (int i = nearest + 1; i < buffer.size(); i++) {
            double[] s = buffer.get(i);
            collector.accept(s[0], s[1], s[2], true);
        }
        return buffer.get(nearest)[3];
    }

    /**
     * 用状态副本沿一条铁轨路径按步长采样到路径尽头。
     *
     * @param from 起始状态（不修改）
     * @param path 要走的路径
     * @param step 采样步长
     * @return 采样点 {@code [x, y, z, 距起点的沿轨距离]}，首点为起始位置
     */
    private List<double[]> samplePath(RailState from, RailPath path, double step) {
        RailState probe = from.clone();
        List<double[]> samples = new ArrayList<>();
        double moved = 0;
        Location pos = probe.positionLocation();
        samples.add(new double[]{pos.getX(), pos.getY(), pos.getZ(), 0});
        // 上限兜底：路径总长有限，正常远到不了
        for (int i = 0; i < 10000; i++) {
            double d = path.move(probe, step);
            if (d <= 1e-9) {
                break;
            }
            moved += d;
            pos = probe.positionLocation();
            samples.add(new double[]{pos.getX(), pos.getY(), pos.getZ(), moved});
        }
        return samples;
    }

    /**
     * 找出离铁轨方块中心最近的采样点下标。
     *
     * @param samples  采样点（{@code [x, y, z, ...]}，非空）
     * @param railBlock 铁轨方块
     * @return 最近点下标
     */
    private int nearestIndex(List<double[]> samples, Block railBlock) {
        double cx = railBlock.getX() + 0.5;
        double cy = railBlock.getY() + 0.5;
        double cz = railBlock.getZ() + 0.5;
        int best = 0;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            double[] s = samples.get(i);
            double dx = s[0] - cx;
            double dy = s[1] - cy;
            double dz = s[2] - cz;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = i;
            }
        }
        return best;
    }

    /**
     * 构造一个「轨道结束」结果（断轨 / 死路 / 步数上限）。
     *
     * @param railBlock  停止位置铁轨方块
     * @param direction  停止时的行走方向
     * @param startMoved 段首累计里程（用于算本段长度）
     * @return 轨道结束的行走结果
     */
    private WalkResult endResult(Block railBlock, Vector direction, double startMoved) {
        return new WalkResult(StopReason.END, railBlock, null, List.of(), false, direction,
                wp.movedTotal - startMoved, null);
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
     * 确保当前铁轨的控制牌已被 TC 铁轨缓存扫描到；补扫出控制牌时重新预测本格走向。
     * <p>
     * <b>为什么需要</b>（TC 缓存缺陷，见 TC {@code WorldRailLookupImpl}）：detector 区域加载时
     * （{@code DetectorRegion} 构造 → {@code storeDetectorRegions} → {@code getOrCreateAtCoordinates}）
     * 会为区域内每格铁轨预建 {@code RailType.NONE} 的缓存条目。首次有行走点经过时，
     * {@code computeRailsAtPosition} 用 {@code swapOutNoneType} 把它换成真实铁轨类型的新条目，
     * 但<b>不扫描控制牌</b>（signs 留在空的 {@code MISSING_RAILS_NO_SIGNS}）；而 {@code Bucket.verify()}
     * 在同一 tick 内（{@code rail_life >= lifeTimer}）直接返回 true，不会补扫。遍历在一个 tick 内同步完成，
     * 于是整段遍历都看不到这格的控制牌：bcswitcher 的预测寻路不被调用、道岔按默认走向（正线）通过，
     * 到发线上的 platform 遍历不到。下一 tick 起缓存自愈，所以「重启后第一次遍历必失败、第二次成功」。
     * <p>
     * 处理：本格读到空控制牌时调 {@link RailPiece#forceCacheVerification()} 强制下次访问重扫；
     * 重扫出控制牌说明刚才的预测是在「看不到牌」的状态下做的，需重新预测本格走向
     * （{@link #refreshPredictedPath} 会对当前状态重新跑一次 navigate）。真正无牌的铁轨只多一次扫描。
     */
    private void ensureSignsDiscovered() {
        RailPiece piece = wp.state.railPiece();
        if (piece == null || piece.signs().length > 0) {
            return;
        }
        piece.forceCacheVerification();
        if (piece.signs().length > 0) {
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
     * 一格铁轨上找到的节点控制牌集合。
     *
     * @param reason  本次停止的节点类型：同格<b>有任一 bcswitcher 即判为道岔</b>
     *                （道岔的出向决定后继走向，不能被同格的 platform 掩盖），否则为车站
     * @param primary 代表性控制牌：道岔取首块 bcswitcher，车站取首块 platform（用于取站名）
     * @param all     同格全部节点控制牌（出向须跨全部 bcswitcher 牌收集）
     * @param mixed   同格既有 bcswitcher 又有 platform（不符合标准车站格式，调用方应告警）
     */
    private record NodeSigns(StopReason reason, RailLookup.TrackedSign primary,
                             List<RailLookup.TrackedSign> all, boolean mixed) {
    }

    /**
     * 在一个铁轨片上查找<b>全部</b>尚未检测过的节点控制牌（platform / bcswitcher），并把它们记入
     * {@code checkedSigns} 以免重复触发。
     * <p>
     * <b>为什么必须收全部而不是第一块</b>：一格铁轨下可以挂多块 bcswitcher（各声明不同进入方向），
     * 旧实现只返回 {@code signs()} 的第一块，于是：
     * <ul>
     *   <li>若第一块牌的进入方向与本次到达方向不匹配，该道岔的出向会被整块跳过——表现为
     *       「bcswitcher 识别不到」。<b>普通铁轨同样会触发</b>，只要一格上放了多块 bcswitcher。</li>
     *   <li>若同格既有 platform 又有 bcswitcher，{@code signs()} 的顺序决定认哪个；认成 platform
     *       时该道岔的全部出向都会丢失。</li>
     * </ul>
     * 故此处返回同格所有节点牌，由 {@link #collectSwitcherBranches} 跨全部牌收集出向；类型判定上
     * <b>bcswitcher 优先于 platform</b>（出向决定走向，不可被掩盖）。
     *
     * @param railPiece    铁轨片
     * @param checkedSigns 已检测过的控制牌集合（原地加入本次新发现的牌）
     * @return 找到的节点牌集合；本格无未检测过的节点牌时返回 null
     */
    private NodeSigns findNodeSigns(RailPiece railPiece, Set<Object> checkedSigns) {
        if (railPiece == null) {
            return null;
        }
        RailLookup.TrackedSign[] signs = railPiece.signs();
        if (signs == null) {
            return null;
        }
        List<RailLookup.TrackedSign> all = new ArrayList<>();
        RailLookup.TrackedSign firstSwitcher = null;
        RailLookup.TrackedSign firstPlatform = null;
        for (RailLookup.TrackedSign sign : signs) {
            StopReason type = signType(sign);
            if (type == null) {
                continue;
            }
            // 已检测过（出发点所在格的牌 / 同格上一次已触发的牌）不再触发，但仍计入 all：
            // 出向收集需要同格全部 bcswitcher。
            boolean fresh = checkedSigns.add(sign.getUniqueKey());
            all.add(sign);
            if (type == StopReason.SWITCHER) {
                if (firstSwitcher == null && fresh) {
                    firstSwitcher = sign;
                }
            } else if (firstPlatform == null && fresh) {
                firstPlatform = sign;
            }
        }
        if (firstSwitcher == null && firstPlatform == null) {
            return null;
        }
        boolean mixed = firstSwitcher != null && firstPlatform != null;
        return firstSwitcher != null
                ? new NodeSigns(StopReason.SWITCHER, firstSwitcher, all, mixed)
                : new NodeSigns(StopReason.PLATFORM, firstPlatform, all, mixed);
    }

    /**
     * 把一个铁轨片上的全部节点控制牌记入集合（不触发停止）。用于把<b>出发点</b>所在铁轨的节点牌
     * 预先标记为已检测，使本段不会在出发点立刻停下。
     *
     * @param railPiece 铁轨片（可为 null）
     * @param sink      接收集合
     */
    private void collectSignsInto(RailPiece railPiece, Set<Object> sink) {
        if (railPiece == null) {
            return;
        }
        RailLookup.TrackedSign[] signs = railPiece.signs();
        if (signs == null) {
            return;
        }
        for (RailLookup.TrackedSign sign : signs) {
            if (signType(sign) != null) {
                sink.add(sign.getUniqueKey());
            }
        }
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
     * 收集给定节点控制牌中、其进入方向匹配本次<b>到达方向</b>的所有 bcswitcher 出向分支，
     * 并<b>按物理分叉合并</b>。
     * <p>
     * 分两步：
     * <ol>
     *   <li><b>逐牌筛选</b>：一格铁轨下可能有多块 bcswitcher 牌，每块牌的牌头声明了它适用的进入方向
     *       （如 {@code [+train:lf]}）。只有牌头进入方向匹配本次到达方向的牌，其出向才纳入；不匹配的牌
     *       （为反方向来车准备）跳过。进入方向是<b>每块牌各自</b>的属性，故必须在合并之前判定。</li>
     *   <li><b>按分叉合并</b>：把每个出向规整为本铁轨上的物理分叉名（{@link #normalizeDirection}），
     *       同一分叉的出向合成一个分支、线路 id 取并集（{@link GeoUtils#mergeBranchesByDirection}）。
     *       这样一块牌写 {@code r@pr-cw;pr-s3} 与两块牌分写 {@code r@pr-s3}、{@code r@pr-cw} 得到
     *       完全相同的结果，下游的共线段判定、联络线判定只需处理一种形态。</li>
     * </ol>
     * 按分叉而不是按出向文本合并：相对方向（f/b/l/r）以<b>牌子朝向</b>为基准，贴在不同面的两块牌上
     * 同一个 {@code r} 可能是两条不同的轨道，而一块写 {@code r}、一块写 {@code e} 又可能是同一条。
     * <p>
     * <b>方向匹配用 {@link SignActionEvent#isEnterActivated()}，不能用
     * {@code isWatchedDirection(Vector)}</b>：后者对每个进入方向求 {@code motionDot(向量) > 0}，
     * 而 <b>TCC 云轨</b>的牌头写的是数字节点名，解析成 {@code RailEnterDirectionFromJunction}，
     * 其 {@code motionDot} 是「对该 junction 终点位置向量取反再点积」——在曲线上这个符号并不可靠，
     * 导致该匹配的匹配不上（整块牌的出向被跳过，表现为道岔识别不到）、不该匹配的也可能匹配上。
     * {@code isEnterActivated} 走的是 {@code RailEnterDirection.match(RailState)}：把 RailState 沿路径
     * <b>往回走</b>到端点再比位置与方向，对节点名与方向字符都精确。traincarts 原版 switcher 牌用的
     * 也正是它（见 {@code SignActionSwitcher}）。
     * <p>
     * 判据需要一个 {@link RailState}。行走器用 {@code member} 构造事件时，TC 会去
     * {@code member.getGroup().getRailTracker()} 里找匹配的 rail——遍历矿车停在起点不动，那里没有
     * 本节点的记录，故必须用 {@link SignActionEvent#overrideCartEnterState} 显式传入行走器当前的
     * {@code wp.state}（TC 自己在路径预测里也是这么做的，见 {@code PathProvider.predict}）。
     *
     * @param nodeSigns 节点所在铁轨片上的全部节点控制牌（见 {@link WalkResult#nodeSigns()}）
     * @return 匹配的出向分支（跨多块牌、按物理分叉合并，按首次声明顺序）
     */
    public List<BcSwitcherBranch> collectSwitcherBranches(List<RailLookup.TrackedSign> nodeSigns) {
        List<BcSwitcherBranch> result = new ArrayList<>();
        if (nodeSigns == null) {
            return result;
        }
        for (RailLookup.TrackedSign sign : nodeSigns) {
            if (signType(sign) != StopReason.SWITCHER) {
                continue;
            }
            // TCC 虚拟牌的牌头进入方向缓存可能过期，必须在构造事件（事件构造时会取 header）之前清掉
            SignActionBcswitcher.refreshEnterDirections(sign);
            SignActionEvent event = new SignActionEvent(sign, member);
            // 用行走器当前的 RailState 作为「到达状态」，使 isEnterActivated 能精确判定进入方向
            event.overrideCartEnterState(wp.state);
            // 牌头声明了进入方向时，只在到达方向匹配时纳入；未声明则视为不限方向（兼容，但建牌已强制声明）
            if (event.isWatchedDirectionsDefined() && !event.isEnterActivated()) {
                continue;
            }
            for (int i = 2; i <= 3; i++) {
                BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(sign.getLine(i));
                if (branch != null) {
                    // 出向规整为物理分叉名后再合并：消除牌子朝向对相对方向的影响
                    result.add(new BcSwitcherBranch(normalizeDirection(event, branch.getDirectionStr()),
                            branch.getLineIds()));
                }
            }
        }
        return GeoUtils.mergeBranchesByDirection(result);
    }

    /**
     * 生成本次到达道岔的排查日志：到达方向，以及同格每块 bcswitcher 的牌头入向、是否匹配、各出向。
     * 必须在行走器停在该道岔（{@link #walkToNextNode} 刚返回 SWITCHER）时调用，用的是当前到达状态。
     * <p>
     * 方向一律写成列车<b>行进方向</b>的绝对方向（n/e/s/w，斜向为 ne/nw/se/sw），与牌上写的相对方向 /
     * 数字节点名并列给出，便于对照：
     * <ul>
     *   <li>到达：到达状态的进入方向向量。</li>
     *   <li>牌头入向：方向字符（{@code RailEnterDirectionToFace}）本身就是驶入时的行进方向；数字节点名
     *       （{@code RailEnterDirectionFromJunction}）表示从该分叉驶入，行进方向取分叉朝外方向的反向。</li>
     *   <li>出向：解析到的分叉（{@link SignActionEvent#findJunction(String)}）的朝外方向；解析不到写「无此分叉」。</li>
     * </ul>
     *
     * @param nodeSigns 节点所在铁轨片上的全部节点控制牌
     * @return 多行日志文本（首行为到达方向，之后每块 bcswitcher 一行）
     */
    public String describeSwitcher(List<RailLookup.TrackedSign> nodeSigns) {
        StringBuilder sb = new StringBuilder();
        Vector arrival = wp.state.enterDirection();
        sb.append("到达行进方向 ").append(absDir(arrival.getX(), arrival.getZ()));
        if (nodeSigns == null) {
            return sb.toString();
        }
        for (RailLookup.TrackedSign sign : nodeSigns) {
            if (signType(sign) != StopReason.SWITCHER) {
                continue;
            }
            SignActionBcswitcher.refreshEnterDirections(sign);
            SignActionEvent event = new SignActionEvent(sign, member);
            event.overrideCartEnterState(wp.state);
            Block signBlock = sign.signBlock;
            sb.append("\n    bcswitcher(%d,%d,%d%s) 牌头入向 ".formatted(signBlock.getX(), signBlock.getY(),
                    signBlock.getZ(), sign.isRealSign() ? "" : ",TCC"));
            if (event.isWatchedDirectionsDefined()) {
                List<String> enters = new ArrayList<>();
                for (RailEnterDirection dir : event.getEnterDirections()) {
                    enters.add(describeEnterDirection(dir));
                }
                sb.append(enters.isEmpty() ? "[解析为空]" : enters.toString());
                sb.append(event.isEnterActivated() ? " 匹配" : " 不匹配（本牌出向不展开）");
            } else {
                sb.append("未声明");
            }
            List<String> outs = new ArrayList<>();
            for (int i = 2; i <= 3; i++) {
                BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(sign.getLine(i));
                if (branch == null) {
                    continue;
                }
                RailJunction junction = event.findJunction(branch.getDirectionStr());
                String target = junction == null
                        ? "无此分叉"
                        : junction.name() + "/" + absDir(junction.position().motX, junction.position().motZ);
                outs.add("%s→%s@%s".formatted(branch.getDirectionStr(), target,
                        String.join(";", branch.getLineIds())));
            }
            sb.append(" 出向 ").append(outs);
        }
        return sb.toString();
    }

    /**
     * 牌头进入方向的排查文本：方向字符原样输出；数字节点名附上驶入行进方向（分叉朝外方向的反向）。
     *
     * @param dir 进入方向
     * @return 如 {@code e}、{@code 1/s}
     */
    private String describeEnterDirection(RailEnterDirection dir) {
        if (dir instanceof RailEnterDirectionFromJunction fromJunction) {
            RailJunction junction = fromJunction.getJunction();
            return junction.name() + "/" + absDir(-junction.position().motX, -junction.position().motZ);
        }
        if (dir instanceof RailEnterDirectionToFace toFace) {
            return absDir(toFace.getFace().getModX(), toFace.getFace().getModZ());
        }
        return dir.name();
    }

    /**
     * 把水平方向向量转成绝对方向（n/e/s/w，偏离坐标轴超过 22.5° 时为 ne/nw/se/sw）。
     *
     * @param x 方向 x 分量（east 为正）
     * @param z 方向 z 分量（south 为正）
     * @return 绝对方向；水平分量为 0（纯竖直）时为 {@code ?}
     */
    private static String absDir(double x, double z) {
        double ax = Math.abs(x);
        double az = Math.abs(z);
        if (ax < 1e-9 && az < 1e-9) {
            return "?";
        }
        String ew = x > 0 ? "e" : "w";
        String ns = z > 0 ? "s" : "n";
        // tan(67.5°) ≈ 2.414：一个分量是另一个的 2.414 倍以上即视为正方向
        if (ax > az * 2.414) {
            return ew;
        }
        if (az > ax * 2.414) {
            return ns;
        }
        return ns + ew;
    }

    /**
     * 把一块牌上写的出向规整为本铁轨上的<b>物理分叉名</b>（{@link RailJunction#name()}）。
     * <p>
     * 解析委托 traincarts 的 {@link SignActionEvent#findJunction(String)}（先按分叉名、再按方向，
     * 相对方向以该牌朝向为基准），与运行时 bcswitcher 切道岔的解析完全一致。规整后的分叉名对任意一块
     * 牌再次 {@code findJunction} 都能精确命中同一分叉，故可直接用作强制出向与 geojson 的 {@code departDir}。
     * <p>
     * 无法解析（该铁轨上没有这个方向的分叉）时原样返回，交由后续行走按原逻辑处理。
     *
     * @param event 该牌的控制牌事件（提供牌子朝向与所在铁轨）
     * @param dir   牌上写的出向字符串
     * @return 物理分叉名；无法解析时为原字符串
     */
    private String normalizeDirection(SignActionEvent event, String dir) {
        RailJunction junction = event.findJunction(dir);
        return junction == null ? dir : junction.name();
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
