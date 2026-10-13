package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.geojson.LngLatAlt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * 一个<b>遍历单元</b>的行走驱动器：从某条营运线路的登记起点出发，走出该线的全线 + 其联络线分支，
 * 产出该线独占的一个 geojson 文件。
 * <p>
 * <b>单元是调度、验证、保存、失败的唯一边界</b>：每个单元有自己的队列、去重集合、预算、中止标记、
 * 结果收集器与路径记录。一个单元失败只影响它自己的文件（其余线路照常写出），这使大型路网不会因为
 * 一条线配置写错而整体回退。
 * <p>
 * 行走分两个阶段，<b>阶段 1 排空后才开始阶段 2</b>：
 * <ol>
 *   <li><b>本线主干</b>：从登记起点沿本线一路向前。</li>
 *   <li><b>联络线分支</b>：阶段 1 中遇到的 {@code contact} 出向在此集中展开（联络线可链式接联络线）。
 *       联络线没有自己的登记起点，只能被营运线带出来——这正是它必须属于某个单元的原因。</li>
 * </ol>
 * 各类节点的处理：
 * <table>
 *   <tr><th>到达</th><th>处理</th></tr>
 *   <tr><td>platform</td><td>非折返站沿进入方向续行、折返站（{@code :RV}）反向驶出、终点站停住
 *       （配了转线则按越界边处理）</td></tr>
 *   <tr><td>bcswitcher，出向含本线</td><td>按该出向续行（同一道岔多个属本线的出边逐个 fork，
 *       如正线 + 到发线）</td></tr>
 *   <tr><td>bcswitcher，出向含 {@code contact}</td><td>记录为待办联络线分支，压入阶段 2 队列</td></tr>
 *   <tr><td>bcswitcher，出向是另一条营运线</td><td><b>走一跳越界边</b>：走到下一个节点、记录这条边，
 *       就此停住，不流入对端路网（见 {@link #isCrossBoundary}）</td></tr>
 *   <tr><td>bcswitcher，无匹配到达方向的出向</td><td>本分支结束</td></tr>
 *   <tr><td>断轨</td><td>本分支结束</td></tr>
 * </table>
 * 去重 key = {@code (节点, 入向, 出向, lineId)}，<b>仅在本单元内</b>共享：跨单元不再互相短路，
 * 保证每个单元的产出完整、与其它单元的成败无关。
 * <p>
 * 仍用实体矿车行走（{@link TrackWalker}）以正确触发沿途原版 switcher 的 addtag / remtag。
 */
public class UnitWalk {
    /**
     * 本单元的 id：即该线路的 lineId，同时是输出文件名与所有区间的 {@code owner}。
     */
    @Getter
    private final String unitLineId;
    @Getter
    private final TraversalCollector collector = new TraversalCollector();
    private final GeoTraversalLogger log;
    /**
     * 本单元最多展开的段数（兜底防环）。超出即本单元失败，不影响其它单元。
     */
    private final int maxEdges;
    private final int maxEdgesPerWalk;
    /**
     * 忽略的线路 id 集合（{@code walkAll --ignore}）：出向声明为这些线的分支不展开、不记越界边。
     */
    private final Set<String> ignoreLineIds;
    private final double coasterSampleStep;

    /**
     * 本单元已展开的 {@code (节点,入向,出向,lineId)} 状态。仅本单元内去重。
     */
    private final Set<String> visited = new HashSet<>();
    /**
     * 阶段 1 队列：本线主干。
     */
    private final Deque<WalkState> mainQueue = new ArrayDeque<>();
    /**
     * 阶段 2 队列：联络线分支。阶段 1 排空后才开始消费。
     */
    private final Deque<WalkState> contactQueue = new ArrayDeque<>();
    /**
     * 本单元实际到达的车站名（按首次到达顺序），供事后与配置站序比对。
     * <p>
     * 按 lineId 分组：单元主体是本线的车站，但越界边 / 转线的落点可能是别的线的车站。
     */
    @Getter
    private final Map<String, Set<String>> visitedStationsByLine = new LinkedHashMap<>();
    /**
     * 本单元走出的越界边落点：目标线 lineId -> 落点节点 id 集合。
     * <p>
     * 供跨单元校验：这些落点应当出现在目标线单元的到达节点集中，否则说明出向声明的线路写错、
     * 或对端轨道未铺到。
     */
    @Getter
    private final Map<String, Set<String>> crossBoundaryLandings = new LinkedHashMap<>();
    @Getter
    private final TraversalTrace trace = new TraversalTrace();
    /**
     * 本单元累计处理的段数。
     */
    @Getter
    private int processed = 0;
    @Getter
    private boolean aborted = false;
    @Getter
    private String abortReason = null;

    /**
     * @param unitLineId        本单元线路 id（= 输出文件名 = 所有区间的 owner）
     * @param log               日志
     * @param maxEdges          本单元最多展开段数
     * @param maxEdgesPerWalk   单段行走最多采样的坐标点数
     * @param ignoreLineIds     忽略的线路 id 集合；null / 空表示不忽略
     * @param coasterSampleStep TCC 云轨曲线采样步长（方块）；{@code <=0} 关闭云轨密采
     */
    public UnitWalk(String unitLineId, GeoTraversalLogger log, int maxEdges, int maxEdgesPerWalk,
                    Set<String> ignoreLineIds, double coasterSampleStep) {
        this.unitLineId = unitLineId;
        this.log = log;
        this.maxEdges = maxEdges;
        this.maxEdgesPerWalk = maxEdgesPerWalk;
        this.ignoreLineIds = ignoreLineIds == null ? Collections.emptySet() : ignoreLineIds;
        this.coasterSampleStep = coasterSampleStep;
    }

    /**
     * 一段行走的类型：决定走到下一个节点、记下这条边之后怎么处理。
     */
    private enum Hop {
        /**
         * 普通段：按节点类型照常展开后继。
         */
        NORMAL,
        /**
         * <b>越界边</b>（道岔出向声明了别的营运线 / 终点站转线）：记下这条边后在落点处做一次落点展开
         * （见 {@link #expandLanding}），不流入对端路网。
         */
        CROSS,
        /**
         * <b>落点补边</b>：从越界边落点道岔沿目标线走出的一段。记下这条边即停。
         */
        FINAL
    }

    /**
     * 待展开的一段行走状态。
     *
     * @param prevNodeId    上一节点 id（起点首段为 null，无入边可记）
     * @param rail          本段起始铁轨
     * @param direction     本段起始方向
     * @param forcedDir     离开起始 bcswitcher 时的强制出向（platform 续行 / 起点首段为 null）
     * @param lineId        本段携带的线路 id（决定本段边归属的 lineId 及矿车导向 tag）
     * @param fromEnterFace 到达起点节点（道岔）时的到达面 key（起点首段为 null）
     * @param hop           本段类型（见 {@link Hop}）
     * @param targetLineId  越界边 / 落点补边要接入的目标营运线（{@link Hop#NORMAL} 为 null）。单独记录而不从
     *                      {@code lineId} 推断：终点站转线那一跳的 lineId 是 {@code contact}，但要接入的是下一条线
     * @param traceParent   本段出发节点在 {@link #trace 路径图}上的节点
     * @param traceStateKey 本段对应的去重状态 key（起点首段为 seed key）
     * @param startState    本段起步的真实铁轨状态（道岔 fork 时为到达该道岔的状态；起点首段 / platform 续行为
     *                      null，按 {@code rail} + {@code direction} 起步）。道岔 fork 必须用真实状态起步：
     *                      合成状态在斜向轨道上算不对进入面，bcswitcher 进入方向判定失败、强制出向不生效
     */
    private record WalkState(String prevNodeId, Block rail, Vector direction, String forcedDir, String lineId,
                             String fromEnterFace, Hop hop, String targetLineId,
                             TraversalTrace.TraceNode traceParent, String traceStateKey, RailState startState) {
    }

    /**
     * 压入本单元的起点首段。
     *
     * @param startRail      起点铁轨
     * @param startDirection 起点方向
     */
    public void seed(Block startRail, Vector startDirection) {
        TraversalTrace.TraceNode root = trace.seedRoot(unitLineId, startRail);
        // 首段不经过道岔决策、没有 (节点,入向,出向,lineId) 状态，但路径图仍需一个 key 把「根节点 ->
        // 首段走到的节点」接起来，否则根节点没有后继、取路第一步就断。
        String key = "seed|" + unitLineId;
        trace.link(root, key);
        mainQueue.add(new WalkState(null, startRail, startDirection, null, unitLineId, null, Hop.NORMAL, null,
                root, key, null));
    }

    /**
     * 分片展开：最多弹出 {@code batchSize} 段处理。必须在主线程调用。
     * <p>
     * 阶段 1（本线主干）排空后才开始阶段 2（联络线分支）。
     *
     * @param batchSize 本批最多展开的段数（{@code <=0} 视为 1）
     * @return 实际处理的段数
     */
    public int stepBatch(int batchSize) {
        int limit = Math.max(1, batchSize);
        int done = 0;
        for (int i = 0; i < limit; i++) {
            if (aborted) {
                return done;
            }
            // 阶段 1 优先：主干排空后才走联络线分支
            WalkState st = mainQueue.poll();
            if (st == null) {
                st = contactQueue.poll();
            }
            if (st == null) {
                return done;
            }
            if (processed >= maxEdges) {
                abort("线路 %s 达到单元段数上限 %d，可能存在配置缺失或异常环路。停止位置 %s"
                        .formatted(unitLineId, maxEdges, st.rail().getLocation()));
                return done;
            }
            processed++;
            done++;
            walkSegment(st);
        }
        return done;
    }

    /**
     * 本单元是否还有待展开的段。
     *
     * @return 还有待展开段且未中止时返回 true
     */
    public boolean hasPending() {
        return !aborted && (!mainQueue.isEmpty() || !contactQueue.isEmpty());
    }

    /**
     * 标记本单元中止（达到段数上限 / 用户停止 / 起点异常）。中止后本单元不写文件，其它单元不受影响。
     *
     * @param reason 中止原因（含调试信息）
     */
    public void abort(String reason) {
        if (this.aborted) {
            return;
        }
        this.aborted = true;
        this.abortReason = reason;
        log.error("单元 " + unitLineId + " 遍历中止：" + reason, null);
    }

    /**
     * 走一段：从 state 起步走到下一个节点，记录区间，并按节点类型把后继状态入队。
     *
     * @param st 当前段状态
     */
    private void walkSegment(WalkState st) {
        String lineId = st.lineId();
        String color = LineConfig.getColor(lineId);
        String railwaySystemId = LineConfig.getSystemId(lineId);
        String logPrefix = " [%s/%s] ".formatted(railwaySystemId, lineId);
        TrackWalker walker = st.startState() != null
                ? new TrackWalker(st.startState())
                : new TrackWalker(st.rail(), st.direction());
        walker.setLineTag(lineId);
        walker.setForcedDirection(st.forcedDir());
        walker.setSampleStep(coasterSampleStep);
        // 从节点出发：告诉行走器起点节点位置，云轨上据此把出边首点对齐到节点（与入边终点、Point 一致）
        RailNode prevNode = st.prevNodeId() == null ? null : collector.node(st.prevNodeId());
        if (prevNode != null) {
            walker.setStartNodePosition(prevNode.getPosition());
        }
        try {
            List<LngLatAlt> coords = new ArrayList<>();
            int[] count = {0};
            // 本段是否含 TCC 云轨采样点：含则用云轨曲线精度简化（保留弧线），否则用普通轨精度（压平阶梯斜线）
            boolean[] hasCoaster = {false};
            TrackWalker.WalkResult result = walker.walkToNextNode((x, y, z, coaster) -> {
                if (count[0] < maxEdgesPerWalk) {
                    coords.add(new LngLatAlt(x, z, y));
                    count[0]++;
                    if (coaster) {
                        hasCoaster[0] = true;
                    }
                }
            });

            if (result.reason() == TrackWalker.StopReason.END) {
                log.info(logPrefix + "线路 " + lineId + " 轨道结束（断轨/死路 @ " + result.railBlock().getLocation() + "）");
                trace.arrive(st.traceParent(), st.traceStateKey(), TraversalTrace.Kind.END, null,
                        result.railBlock(), lineId);
                return;
            }

            // 同格既有 bcswitcher 又有 platform：不符合标准车站格式，已按道岔处理（出向决定走向，
            // 不能被 platform 掩盖），但要告警——否则该站的 platform 永远不会被记为车站节点。
            if (result.mixedNode()) {
                log.warn(logPrefix + "铁轨 " + result.railBlock().getLocation()
                        + " 上同时存在 bcswitcher 与 platform 控制牌，已按道岔处理。"
                        + "请按标准车站格式把两者分开放置，否则该 platform 不会被记为车站节点");
            }

            Vector arrival = result.direction();
            String arrivalFace = faceKey(arrival);

            RailNode node;
            TraversalTrace.TraceNode traceNode;
            if (result.reason() == TrackWalker.StopReason.PLATFORM) {
                String stationName = result.sign().getLine(2).trim();
                node = collector.resolveNode(RailNode.Type.STATION, result.railBlock(), stationName);
                visitedStationsByLine.computeIfAbsent(lineId, k -> new LinkedHashSet<>()).add(stationName);
                log.info(logPrefix + "到达车站 " + stationName + " @ " + node.getId());
                traceNode = trace.arrive(st.traceParent(), st.traceStateKey(), TraversalTrace.Kind.STATION,
                        stationName, result.railBlock(), lineId);
            } else {
                node = collector.resolveNode(RailNode.Type.SWITCH, result.railBlock(), null);
                // 行走器此刻停在道岔上，到达状态即本次到达：输出入向 / 各牌出向供排查
                log.info(logPrefix + "经过道岔 @ " + node.getId() + "，" + walker.describeSwitcher(result.nodeSigns()));
                traceNode = trace.arrive(st.traceParent(), st.traceStateKey(), TraversalTrace.Kind.SWITCH, null,
                        result.railBlock(), lineId);
            }
            node.addLineId(lineId);
            node.addRailwaySystemId(railwaySystemId);
            node.setCoasterPosition(result.nodePosition());

            // 记录入边（起点首段 prevNodeId 为 null，无边可记）。st.forcedDir() 即离开上一道岔所用出向，
            // 作为本段物理出向写入，供运行时道岔对带导航的列车直接选向。
            // owner 恒为本单元 id：本单元走出的一切都写进本单元的文件。
            if (st.prevNodeId() != null && !st.prevNodeId().equals(node.getId())) {
                double tolerance = hasCoaster[0] ? GeoUtils.COASTER_HORIZONTAL_TOLERANCE : GeoUtils.HORIZONTAL_TOLERANCE;
                collector.recordEdge(st.prevNodeId(), node.getId(), lineId, railwaySystemId, color,
                        GeoUtils.simplifyLineString(coords, tolerance), result.length(), st.forcedDir(),
                        node.getRailBlock().getWorld().getName(), st.fromEnterFace(), arrivalFace, unitLineId);
            }

            // 落点补边：已沿目标线从落点道岔走出一段并记下，到此为止
            if (st.hop() == Hop.FINAL) {
                trace.note(traceNode, "落点补边终点（线路 %s 由其自己的遍历单元继续）".formatted(st.targetLineId()));
                return;
            }
            // 越界边：这条边已记下（它是本线列车切到对端线的物理连接，寻路做换乘要用）。
            // 不流入对端路网——继续走下去会把整条对端线复制进本单元的文件；只在落点做一次落点展开。
            if (st.hop() == Hop.CROSS) {
                expandLanding(node, st.targetLineId(), walker, result, arrival, arrivalFace, logPrefix, traceNode);
                return;
            }

            if (result.reason() == TrackWalker.StopReason.PLATFORM) {
                expandPlatform(node, lineId, arrival, arrivalFace, logPrefix, traceNode);
            } else {
                expandSwitcher(node, lineId, walker, result, arrival, arrivalFace, logPrefix, traceNode);
            }
        } finally {
            walker.destroy();
        }
    }

    /**
     * platform 节点展开：一进一出。非折返站沿进入方向续行；折返站（{@code :RV}）反向驶出。
     * 出向不强制，forcedDir 传 null。
     * <p>
     * <b>终点站到此为止</b>：本站是当前线路配置站序的最后一站（{@link LineInfo#isTerminalStation}）时，
     * 本线不再向前展开——否则终点站之后若还接着轨道与 platform（如 {@code A->B->C} 之后物理上还连着 D），
     * 矿车会带着本线 lineId 继续走出 {@code C->D}，把不属于本线的区间写进本单元文件。
     * 环线（站序首尾同名）恒不判定为终点站，故环线闭合段不受影响。
     * <p>
     * 终点站配置了<b>转线</b>（{@code bossbar-stations} 末项写成 {@code <线路id>[:<进入站名>]}，见
     * {@link LineInfo#getNextLineId()}）时，转线后的第一段按<b>越界边</b>处理：记下这条边
     * （lineId = 下一条线）然后停住，由下一条线自己的单元继续。与运行时一致——
     * {@code SignActionPlatform} 正是在终点站<b>出站时</b>改写列车所属线路。
     *
     * @param node        platform 节点
     * @param lineId      本段携带的线路 id
     * @param arrival     到达方向
     * @param arrivalFace 到达方向的面 key（入向）
     * @param traceNode   本节点在路径树上的节点
     */
    private void expandPlatform(RailNode node, String lineId, Vector arrival, String arrivalFace,
                                String logPrefix, TraversalTrace.TraceNode traceNode) {
        LineInfo lineInfo = LineConfig.get(lineId);
        String stationName = node.getStationName();
        boolean reverse = lineInfo != null && lineInfo.isReverseStationByName(stationName);
        Vector outDir = reverse ? arrival.clone().multiply(-1) : arrival.clone();
        if (reverse) {
            log.info(logPrefix + "折返站 " + stationName + "，反向驶出");
        }
        String outFace = faceKey(outDir);

        // 后继段携带的线路 id：默认原样带过去（platform 不提供 lineId）；终点站转线时切换为联络线
        String outLineId = lineId;
        Hop hop = Hop.NORMAL;
        String targetLineId = null;
        if (lineInfo != null && lineInfo.isTerminalStation(stationName)) {
            String nextLineId = resolveTransferLine(lineInfo, logPrefix);
            if (nextLineId == null) {
                // 本线在此结束：不登记后继、不入队，终点之后的轨道不再归入本线
                log.info(logPrefix + "到达线路 " + lineId + " 的终点站 " + stationName + "，本线不再向前展开");
                trace.note(traceNode, "线路 %s 的终点站（%s），本线到此为止".formatted(lineId, stationName));
                return;
            }
            log.info(logPrefix + "终点站 " + stationName + " 转入线路 " + nextLineId + "，记一跳越界边后由该线单元继续");
            // 转入下一段是contact；要接入的目标线单独记在 targetLineId，供落点展开使用
            outLineId = RailwaySystemConfig.CONTACT_ID;
            hop = Hop.CROSS;
            targetLineId = nextLineId;
        }

        // 无论本次是否入队（可能已被本单元别的分支展开过）都登记这条后继：取路时按 key 解析即可缝合续行，
        // 不会把「已遍历过」误报成停止原因。
        String key = stateKey(node, arrivalFace, outFace, outLineId);
        trace.link(traceNode, key);
        tryEnqueue(key, new WalkState(node.getId(), node.getRailBlock(), outDir, null, outLineId, arrivalFace,
                hop, targetLineId, traceNode, key, null), false);
    }

    /**
     * 解析终点站的转线目标线路：{@code bossbar-stations} 末项声明的 {@code nextLineId}，
     * 经「线路存在 + 不在忽略名单」校验后返回。
     * <p>
     * <b>不按本次遍历的目标线集合过滤</b>：转线后的第一段是越界边，写进<b>本单元自己</b>的文件
     * （owner = 本单元），少记它就等于本线文件里缺了一条出边。{@code walk <lineIds>} 只决定
     * 「重建哪些文件」，不应改变单个文件的内容——否则 {@code walk L} 与 {@code walkAll} 对
     * {@code L.geojson} 的产出会不一致。
     *
     * @param lineInfo  当前线路配置
     * @param logPrefix 日志前缀
     * @return 可跟进的转线目标线路 id；无转线 / 校验不通过返回 null
     */
    private String resolveTransferLine(LineInfo lineInfo, String logPrefix) {
        String nextLineId = lineInfo.getNextLineId();
        if (nextLineId == null || nextLineId.isEmpty()) {
            return null;
        }
        if (!LineConfig.contains(nextLineId)) {
            log.info(logPrefix + "转线目标线路 " + nextLineId + " 不存在，终点站不再向前展开");
            return null;
        }
        if (ignoreLineIds.contains(nextLineId)) {
            log.info(logPrefix + "转线目标线路 " + nextLineId + " 在忽略名单中，终点站不再向前展开");
            return null;
        }
        return nextLineId;
    }

    /**
     * bcswitcher 节点展开：按进入方向取出该牌所有匹配出向，按出向声明的线路分三类处理
     * （见类注释的处理表）：本线续行、{@code contact} 入阶段 2 队列、其它营运线走一跳越界边。
     *
     * @param node        道岔节点
     * @param lineId      到达本道岔时携带的线路 id
     * @param walker      当前行走器（提供 member 上下文以判断牌头进入方向匹配）
     * @param result      到达本道岔的行走结果
     * @param arrival     到达方向
     * @param arrivalFace 到达方向的面 key（入向）
     * @param traceNode   本道岔在路径树上的节点
     */
    private void expandSwitcher(RailNode node, String lineId, TrackWalker walker, TrackWalker.WalkResult result,
                                Vector arrival, String arrivalFace, String logPrefix,
                                TraversalTrace.TraceNode traceNode) {
        RailPiece rail = result.sign().getRail();
        // 出向跨同格<b>全部</b>节点牌收集：一格可挂多块 bcswitcher，只看首块会漏掉其余牌的出向
        List<BcSwitcherBranch> branches = walker.collectSwitcherBranches(result.nodeSigns());
        // 统计各出向的去处：一个出向都没留下时据此写明原因（无匹配出向 / 全被过滤）。
        // 注意「已被本单元别处展开」不算终止——那条后继照样 link，取路时会缝合，不写终止说明。
        int candidates = 0;
        int linked = 0;
        int filtered = 0;
        for (BcSwitcherBranch branch : branches) {
            for (String outLineId : branch.getLineIds()) {
                candidates++;
                if (!LineConfig.contains(outLineId)) {
                    log.info(logPrefix + "bcswitcher(%s)的道岔lineId %s 不存在，跳过该分支"
                            .formatted(rail.block().getLocation(), outLineId));
                    filtered++;
                    continue;
                }
                // 忽略名单：出向声明为被忽略线路的分支不展开（walkAll --ignore）
                if (ignoreLineIds.contains(outLineId)) {
                    log.info(logPrefix + "bcswitcher(%s)的出向 %s 在忽略名单中，跳过该分支"
                            .formatted(rail.block().getLocation(), outLineId));
                    filtered++;
                    continue;
                }
                boolean cross = isCrossBoundary(outLineId);
                // 共用轨道：同一出向同时声明了当前线路与别的营运线，说明这段轨道是共线段而不是通往别线的
                // 连接。别线的边由它自己的单元产出，这里不再记越界边——否则共线段每个道岔都会往本单元文件里
                // 塞一条别线的区间（并让节点 prev/next 重复）。branches 已按物理分叉合并
                // （TrackWalker#collectSwitcherBranches），故一块牌写 r@pr-cw;pr-s3 与两块牌分写
                // r@pr-s3 / r@pr-cw 在这里是同一个分支，判定结果一致。
                if (cross && branch.hasLineId(lineId)) {
                    filtered++;
                    continue;
                }
                // 出向 key 用 (方向, 出向lineId)：共用出向按线拆 fork，各挂单一 tag 各走各记。
                // 无论本次是否入队都 link：被去重的后继在取路时按 key 解析即可缝合出完整路径。
                String key = stateKey(node, arrivalFace, branch.getDirectionStr(), outLineId);
                trace.link(traceNode, key);
                linked++;
                // contact 出向进阶段 2 队列（联络线在本线主干走完后再展开）
                boolean contact = RailwaySystemConfig.CONTACT_ID.equals(outLineId);
                tryEnqueue(key, new WalkState(node.getId(), node.getRailBlock(), arrival.clone(),
                        branch.getDirectionStr(), outLineId, arrivalFace, cross ? Hop.CROSS : Hop.NORMAL,
                        cross ? outLineId : null, traceNode, key, result.arrivalState()), contact);
            }
        }
        if (linked == 0) {
            trace.note(traceNode, switchStopNote(candidates, filtered));
        }
    }

    /**
     * 越界边（道岔出向声明了别的营运线 / 终点站转线）的<b>落点展开</b>。
     * <p>
     * 落点是车站：它就在对端线的轨道上，直接停住，由对端线自己的单元继续。
     * <p>
     * 落点是道岔：用遍历车<b>实际的到达状态</b>匹配该道岔的牌头进入方向，按匹配到的出向分三种情况：
     * <ol>
     *   <li><b>有目标线出向</b>：已到对端线轨道上。沿这些出向各走<b>一段落点补边</b>（{@link Hop#FINAL}），
     *       记下后停住。<br>
     *       为什么不能在落点直接停：对端线单元经过这个道岔时是从<b>它自己的方向</b>来的，落点道岔出边的
     *       {@code enterFrom} 只记了那个到达面。本线列车从转线 / 越界方向（如从站台弯进道岔）到达时，
     *       到达面不在其中，寻路的入向门控会拒绝续接——表现为「先在终点站停站、再转线」的路线查不到。
     *       落点补边与对端线自己产出的那条边 id 相同，读取侧按 id 合并、{@code enterFrom} 取并集，
     *       门控便同时接受两个方向。</li>
     *   <li><b>只有 contact 出向</b>：还没到对端线轨道上（经一段联络线才接到对端线）。把这些 contact 出向
     *       压入阶段 2 队列继续走；联络线末端撞上声明对端线的道岔时，会再按越界边走一跳并落点展开。</li>
     *   <li><b>没有匹配此到达方向的出向</b>：该道岔的牌头进入方向不包含转线 / 越界的来车方向，寻路从这个方向
     *       接不上对端线。告警并停住。</li>
     * </ol>
     *
     * @param node        落点节点
     * @param targetLine  要接入的目标营运线
     * @param walker      当前行走器（提供实际到达状态以判定落点道岔牌的进入方向）
     * @param result      到达落点的行走结果
     * @param arrival     到达方向
     * @param arrivalFace 到达方向的面 key
     * @param logPrefix   日志前缀
     * @param traceNode   落点在路径树上的节点
     */
    private void expandLanding(RailNode node, String targetLine, TrackWalker walker, TrackWalker.WalkResult result,
                               Vector arrival, String arrivalFace, String logPrefix,
                               TraversalTrace.TraceNode traceNode) {
        if (result.reason() != TrackWalker.StopReason.SWITCHER) {
            stopAtLanding(node, targetLine, logPrefix, traceNode);
            return;
        }
        List<BcSwitcherBranch> targetBranches = new ArrayList<>();
        List<BcSwitcherBranch> contactBranches = new ArrayList<>();
        for (BcSwitcherBranch branch : walker.collectSwitcherBranches(result.nodeSigns())) {
            if (branch.hasLineId(targetLine)) {
                targetBranches.add(branch);
            } else if (branch.hasLineId(RailwaySystemConfig.CONTACT_ID)) {
                contactBranches.add(branch);
            }
        }

        // 情况 1：已到对端线轨道上，沿目标线出向各补一段边后停住
        if (!targetBranches.isEmpty()) {
            stopAtLanding(node, targetLine, logPrefix, traceNode);
            for (BcSwitcherBranch branch : targetBranches) {
                String key = stateKey(node, arrivalFace, branch.getDirectionStr(), targetLine);
                trace.link(traceNode, key);
                tryEnqueue(key, new WalkState(node.getId(), node.getRailBlock(), arrival.clone(),
                                branch.getDirectionStr(), targetLine, arrivalFace, Hop.FINAL, targetLine, traceNode, key,
                                result.arrivalState()),
                        false);
            }
            return;
        }

        // 情况 2：经联络线才接到对端线，沿联络线继续
        if (!contactBranches.isEmpty() && !ignoreLineIds.contains(RailwaySystemConfig.CONTACT_ID)) {
            log.info(logPrefix + "越界边落在联络线道岔 " + node.getId() + "，沿联络线继续接到线路 " + targetLine);
            trace.note(traceNode, "联络线道岔（经联络线接到线路 %s）".formatted(targetLine));
            node.addLineId(RailwaySystemConfig.CONTACT_ID);
            for (BcSwitcherBranch branch : contactBranches) {
                String key = stateKey(node, arrivalFace, branch.getDirectionStr(), RailwaySystemConfig.CONTACT_ID);
                trace.link(traceNode, key);
                tryEnqueue(key, new WalkState(node.getId(), node.getRailBlock(), arrival.clone(),
                        branch.getDirectionStr(), RailwaySystemConfig.CONTACT_ID, arrivalFace, Hop.NORMAL, null,
                        traceNode, key, result.arrivalState()), true);
            }
            return;
        }

        // 情况 3：落点道岔没有匹配此到达方向的出向，寻路从这个方向接不上对端线
        stopAtLanding(node, targetLine, logPrefix, traceNode);
        log.warn(("线路 %s 接入线路 %s 时到达道岔 %s（到达面 %s），但该道岔没有匹配此到达方向的 %s 出向，"
                + "寻路从这个方向接不上 %s。请检查该道岔 bcswitcher 牌头的进入方向")
                .formatted(unitLineId, targetLine, node.getId(), arrivalFace, targetLine, targetLine));
    }

    /**
     * 在越界边落点停住：记下落点供跨单元校验（应出现在目标线单元的遍历结果中）。
     *
     * @param node       落点节点
     * @param targetLine 目标营运线
     * @param logPrefix  日志前缀
     * @param traceNode  落点在路径树上的节点
     */
    private void stopAtLanding(RailNode node, String targetLine, String logPrefix, TraversalTrace.TraceNode traceNode) {
        crossBoundaryLandings.computeIfAbsent(targetLine, k -> new LinkedHashSet<>()).add(node.getId());
        log.info(logPrefix + "越界边到达 " + node.getId() + "，本单元到此为止（由线路 " + targetLine + " 自己的单元继续）");
        trace.note(traceNode, "越界边终点（线路 %s 由其自己的遍历单元继续）".formatted(targetLine));
    }

    /**
     * 判断一个出向声明的线路是否构成<b>越界</b>（即该出向通往别的营运线，本单元只走一跳就停）。
     * <p>
     * 本线自身与 {@code contact}（联络线属于本单元、要走到底）都不越界；其余营运线都越界。
     *
     * @param outLineId 出向声明的线路 id
     * @return true 表示越界（只走一跳记下边即停）
     */
    private boolean isCrossBoundary(String outLineId) {
        return !unitLineId.equals(outLineId) && !RailwaySystemConfig.CONTACT_ID.equals(outLineId);
    }

    /**
     * 道岔一个出向都没留下时的终止说明（供路径报告指出卡在哪、为什么）。
     *
     * @param candidates 本次到达方向匹配到的出向声明总数（含被过滤的）
     * @param filtered   其中被过滤掉的数量（线路不存在 / 在忽略名单）
     * @return 终止说明
     */
    private String switchStopNote(int candidates, int filtered) {
        if (candidates == 0) {
            return "该道岔没有匹配此到达方向的出向";
        }
        if (filtered >= candidates) {
            return "该道岔的出向均被跳过（线路不存在或在忽略名单中）";
        }
        return "该道岔没有可展开的出向";
    }

    /**
     * 按 {@code (节点,入向,出向,lineId)} 状态去重后入队（仅本单元内去重）。
     *
     * @param key     状态 key（见 {@link #stateKey}）
     * @param next    后继状态
     * @param contact true 表示进阶段 2（联络线）队列，false 表示进阶段 1（本线主干）队列
     * @return 实际入队返回 true；已展开过（被去重跳过）返回 false
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean tryEnqueue(String key, WalkState next, boolean contact) {
        if (visited.add(key)) {
            (contact ? contactQueue : mainQueue).add(next);
            return true;
        }
        return false;
    }

    /**
     * 生成一段行走的去重状态 key：{@code (节点,入向,出向,lineId)}。
     * <p>
     * 同时用作 {@link #trace 路径图}里「这条后继到达了哪个节点」的索引键，使跨分支缝合完整路径成为可能。
     *
     * @param node    出发节点
     * @param inFace  入向 key
     * @param outFace 出向 key
     * @param lineId  本后继段携带的线路 id
     * @return 状态 key
     */
    private String stateKey(RailNode node, String inFace, String outFace, String lineId) {
        return node.getId() + "|" + inFace + "|" + outFace + "|" + lineId;
    }

    /**
     * 把方向向量规整为离散面 key（仅取水平主轴的符号），用于去重与入向门控。
     *
     * @param dir 方向向量
     * @return 形如 "1_0" / "0_-1" 的面 key
     */
    private String faceKey(Vector dir) {
        int x = (int) Math.signum(dir.getX());
        int z = (int) Math.signum(dir.getZ());
        return x + "_" + z;
    }
}
