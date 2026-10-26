package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.StationGroupConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.File;
import java.util.*;

/**
 * 基于 geojson 路由图的寻路引擎（直达，不含换乘）。
 * <p>
 * 持有自己的 {@link GeoRouteGraph} 单例。寻路用 A* 引导的 K-最短无环路枚举（有向图，边权 = geojson length），
 * 实现见 {@link Searcher}。
 * 提供两种入口：
 * <ul>
 *   <li>{@link #findByStation(String, String)}：起点站名 + 终点站名（车票系统用法，枚举起点站各站台节点）。</li>
 *   <li>{@link #findFromNode(String, String)}：起点站台节点 id + 终点站名（交通卡"任意站台上车"用法）。</li>
 * </ul>
 */
public class GeoRouteEngine {
    /**
     * 当前路由图（启动加载 / reload 时整体替换）。
     */
    @Setter
    @Getter
    private static volatile GeoRouteGraph graph = new GeoRouteGraph();

    /**
     * 未限制条数时，每个起点站台 K-最短路的安全上限（防止复杂图上候选过多）。
     */
    private static final int KSP_SAFETY_CAP = 16;

    /**
     * 单次 {@link Searcher#kShortest} 的出队总数封顶，兜底防止超大图上枚举过久（无环约束已使路径有限，此为额外保险）。
     */
    private static final int KSP_MAX_POPS = 200_000;

    /**
     * 从 geojson 目录加载 / 重载路由图，整体替换当前图。
     *
     * @param geodataDir geojson 目录
     * @param logger     日志（可为 null）
     */
    public static void load(File geodataDir, ComponentLogger logger) {
        graph = new GeoGraphLoader(logger).loadDir(geodataDir);
    }

    /**
     * 按起点站名 + 终点站名寻路：枚举起点站名下所有站台节点，各求最短的 K 条路线，汇总后按距离升序、
     * 按 {@code departDirectionSequence} 去重，最终取最短的前 {@code maxResults} 条。
     * <p>
     * {@code maxResults <= 0} 表示不限制条数（仍受每站台 {@link #KSP_SAFETY_CAP} 安全上限约束）。
     * 支持起终点站相同，见 {@link Searcher#kShortest}。
     *
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @param maxResults   最多返回条数（<=0 不限制）
     * @return 路径列表（按距离升序、已去重），无解返回空列表
     */
    public static List<GeoRoutePath> findByStation(String startStation, String endStation, int maxResults) {
        return findByStation(startStation, endStation, maxResults, 0L);
    }

    /**
     * 同 {@link #findByStation(String, String, int)}，附加计算耗时上限。
     *
     * @param deadlineNanos {@code System.nanoTime()} 的截止时间；{@code <=0} 表示不限时。超时则停止枚举
     *                      剩余站台 / 剩余候选，返回目前已找到的部分结果（非错误）
     */
    public static List<GeoRoutePath> findByStation(String startStation, String endStation, int maxResults,
                                                    long deadlineNanos) {
        return new Searcher(deadlineNanos).findByStation(startStation, endStation, maxResults);
    }

    /**
     * 去重时的择优规则：转线次数少者优先；转线次数相同则距离短者优先。
     *
     * @param candidate 待比较的新路径
     * @param current   已保留的路径
     * @return {@code candidate} 应取代 {@code current} 时返回 true
     */
    private static boolean isBetterRoute(GeoRoutePath candidate, GeoRoutePath current) {
        int candTransfers = getLineTransferCnt(candidate.getLineIdSequence());
        int curTransfers = getLineTransferCnt(current.getLineIdSequence());
        if (candTransfers != curTransfers) {
            return candTransfers < curTransfers;
        }
        return candidate.getDistance() < current.getDistance();
    }

    /**
     * 根据lineId列表获取过转线次数（列表相邻两元素不同的数量）
     *
     * @param lineIdSeq 路线每段的lineId
     * @return 转线次数
     */
    private static int getLineTransferCnt(List<String> lineIdSeq) {
        if (lineIdSeq.size() < 2) {
            return 0;
        }
        int cnt = 0;
        for (int i = 0; i < lineIdSeq.size() - 1; i++) {
            if (!lineIdSeq.get(i).equals(lineIdSeq.get(i + 1))) {
                cnt += 1;
            }
        }
        return cnt;
    }

    /**
     * 按起点站名 + 终点站名寻路（不限条数，等价 {@code findByStation(start, end, 0)}）。
     *
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @return 路径列表（按距离升序、已去重），无解返回空列表
     */
    public static List<GeoRoutePath> findByStation(String startStation, String endStation) {
        return findByStation(startStation, endStation, 0);
    }

    /**
     * 按起点站台节点 id + 终点站名寻路，取最短一条（交通卡：玩家在任意站台上车，按当前站台算最近路径）。
     * 起终点同站名时支持绕环线一圈回到同名车站。
     *
     * @param startNodeId 起点站台节点 id
     * @param endStation  终点站名
     * @return 最短路径，无解返回 null
     */
    public static GeoRoutePath findFromNode(String startNodeId, String endStation) {
        List<GeoRoutePath> paths = new Searcher(0L).kShortest(startNodeId, endStation, 1);
        return paths.isEmpty() ? null : paths.getFirst();
    }

    /**
     * 按「起点站名 + 列车所属 lineId」定位<b>确切的上车站台节点</b>，再寻路到终点站，取最短一条。
     * <p>
     * 用于交通卡「只指定终点」上车：此时起点站台在玩家上车那一刻已固定为列车当前所在线路的站台，
     * 不能按站名枚举所有站台（那会算出别的站台的路径）。借 {@link GeoRouteGraph#getNode(String, String)}
     * 用站名 + lineId 唯一确定该站台节点（一条线路只经过某车站一次）。
     *
     * @param startStation 起点站名（列车 {@code BcStartNodeProperty}）
     * @param lineId       列车所属营运线 id（列车 {@code BcLineIdProperty}）
     * @param endStation   终点站名
     * @return 最短路径；站台节点定位不到 / 无解返回 null
     */
    public static GeoRoutePath findFromStationNode(String startStation, String lineId, String endStation) {
        GeoNode startNode = graph.getNode(startStation, lineId);
        if (startNode == null) {
            return null;
        }
        return findFromNode(startNode.getId(), endStation);
    }

    /**
     * 返回当前图中所有车站节点（用于"最近车站"等按坐标检索的功能）。
     *
     * @return 车站节点列表（含坐标与站名）
     */
    public static List<GeoNode> allStationNodes() {
        List<GeoNode> result = new ArrayList<>();
        for (GeoNode node : graph.allNodes()) {
            if (node.isStation() && node.getName() != null) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * 按起点站名 + 终点站名寻路，并返回距离最接近 {@code targetDistance} 的一条路径。
     * <p>
     * 用于车票上车：车票 NBT 只存 起点站名 / 终点站名 / 购买时距离，上车时按最新图重新寻路，
     * 在多条候选里挑距离与购买时最接近的一条（图未变时即原路径）。
     *
     * @param startStation   起点站名
     * @param endStation     终点站名
     * @param targetDistance 目标距离（购买时记录的距离）
     * @return 距离最接近的路径，无解返回 null
     */
    public static GeoRoutePath findClosestByDistance(String startStation, String endStation, double targetDistance) {
        if (startStation == null || startStation.isEmpty() || endStation == null || endStation.isEmpty() || targetDistance <= 0) {
            return null;
        }

        GeoRoutePath best = null;
        double bestDiff = Double.MAX_VALUE;
        for (GeoRoutePath path : findByStation(startStation, endStation)) {
            double diff = Math.abs(path.getDistance() - targetDistance);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = path;
            }
        }
        return best;
    }

    /**
     * 校验给定的有序节点序列是否构成图上一条合法路线，合法则重建 {@link GeoRoutePath}。
     * <p>
     * 用于<b>网页在线购票</b>：路线在前端已选定，插件不重新寻路，只逐对校验相邻节点间存在对应出边，
     * 然后按既有图结构重建路径（与私有 {@code Searcher.buildPath} 同构的产物，下游票价 / lore / 导航逻辑零改动）。
     * <p>
     * 校验规则：
     * <ul>
     *   <li>{@code nodeIds} 至少含起点与终点两个节点，且首尾都是 station 节点（与车票语义一致）。</li>
     *   <li>逐对 {@code nodeIds[i] -> nodeIds[i+1]} 须存在一条出边（{@link GeoRouteGraph#links}）；</li>
     *   <li>两节点间存在多条平行边（共用轨道、不同 lineId）时，用 {@code lineIdSequence[i]} 消歧；
     *       {@code lineIdSequence} 为 null 时取首条匹配边。</li>
     *   <li>仅展示线路（{@link LineConfig#isDisplayOnly}）的边不可用。</li>
     * </ul>
     * 任一步无匹配边 / 节点不存在 / 起终点非车站 → 返回 null（非法）。
     *
     * @param nodeIds        有序节点 id 列表（含起点与终点站台）
     * @param lineIdSequence 逐段 lineId（size 应为 nodeIds.size()-1），平行边消歧用；可为 null
     * @return 合法时返回重建的路径；非法返回 null
     */
    public static GeoRoutePath validatePath(List<String> nodeIds, List<String> lineIdSequence) {
        GeoRouteGraph g = graph;
        if (nodeIds == null || nodeIds.size() < 2) {
            return null;
        }
        GeoNode startNode = g.getNode(nodeIds.getFirst());
        GeoNode endNode = g.getNode(nodeIds.getLast());
        if (startNode == null || endNode == null || !startNode.isStation() || !endNode.isStation()) {
            return null;
        }

        List<GeoNode> nodes = new ArrayList<>();
        List<String> lineIds = new ArrayList<>();
        List<String> departDirs = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        nodes.add(startNode);
        double total = 0.0;

        GeoLink prevLink = null;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            String fromId = nodeIds.get(i);
            String toId = nodeIds.get(i + 1);
            String wantLine = lineIdSequence != null && i < lineIdSequence.size() ? lineIdSequence.get(i) : null;
            GeoLink matched = null;
            for (GeoLink link : g.links(fromId)) {
                if (!link.getToNodeId().equals(toId)) {
                    continue;
                }
                if (wantLine != null && !wantLine.equals(link.getLineId())) {
                    continue;
                }
                // 仅展示线路不参与购票，与 kShortest 一致
                if (LineConfig.isDisplayOnly(link.getLineId())) {
                    continue;
                }
                // 入向面门控：与 kShortest 一致，拒绝「从错误到达面接反向牌出边」的非法接续。
                if (!enterFaceAllows(prevLink, link)) {
                    continue;
                }
                matched = link;
                break;
            }
            if (matched == null) {
                return null;
            }
            prevLink = matched;
            GeoNode toNode = g.getNode(toId);
            if (toNode == null) {
                return null;
            }
            nodes.add(toNode);
            lineIds.add(matched.getLineId());
            departDirs.add(matched.getDepartDirection());
            // 段长换算为 km，与 buildPath 一致
            distances.add(matched.getDistance() / 1000);
            total += matched.getDistance();
        }
        return new GeoRoutePath(nodes, lineIds, departDirs, distances, total / 1000);
    }

    /**
     * 启发式寻找「一次换乘」的行程方案（两段直达票）：起点站 → 换乘站 → 终点站。
     * <p>
     * 用于两站没有便宜直达、但「中途某站下车换乘另一条线路」更近的场景（如 L1 绕远、在中途站 M 换 L2 更短）。
     * <b>完全复用 {@link #findByStation}</b> 作为单段求解器，不改动图与既有寻路：
     * <ol>
     *   <li>候选换乘站集合（启发式）：起终点若干条直达候选路径上的所有经停站
     *       （{@link GeoRoutePath#stationSequence()}）∪ 终点站所属线路经停的车站；</li>
     *   <li>对每个候选站 M（排除起点 / 终点自身）：分别求 {@code start→M} 与 {@code M→end} 的最短一条；</li>
     *   <li>两段都存在，且换乘总距离 {@code < 最短直达 ×(1 - minImprovement)}（无直达时无条件接受）→ 记一个 {@link JourneyPlan}；</li>
     *   <li>按换乘站去重（只留总距离最短者），按总距离升序，取前 {@code maxResults} 条。</li>
     * </ol>
     * 车站组（{@link StationGroupConfig}）：在 M 下车后，也可在与 M 同组的不同名车站 M' 上车，
     * 即第二段为 {@code M'→end}，此时按 (M, M') 去重。
     * {@code minImprovement} 是<b>最低改善门槛</b>：直达 A→C 若本就经过换乘站 B，则 A→B + B→C 的最短距离之和
     * 几乎总 ≤ 该直达距离（只省一点点也算「更短」），会产生「没必要下车换乘」的噪音。要求换乘至少比直达短
     * {@code minImprovement} 比例（如 0.2 = 短 20%）才显示，可滤掉这类噪音，同时保留「换乘大幅更近」的有用方案。
     * <p>
     * 每段仍是一趟独立直达车（到换乘站停车、下车换乘再上另一段），下游车票 / 导航 / 计价逻辑零改动。
     *
     * @param startStation   起点站名
     * @param endStation     终点站名
     * @param maxResults     最多返回方案数（{@code <=0} 不限制）
     * @param minImprovement 最低改善比例 [0,1)：换乘总距离须 {@code < 最短直达 ×(1 - minImprovement)}；
     *                       {@code <=0} 等价「严格短于直达」。两站无直达时此门槛不生效。
     * @return 换乘方案列表（按总距离升序、按换乘站去重），无可行方案返回空列表
     */
    public static List<JourneyPlan> findTransferJourneys(String startStation, String endStation,
                                                         int maxResults, double minImprovement) {
        return findTransferJourneys(startStation, endStation, maxResults, minImprovement, 0L);
    }

    /**
     * 同 {@link #findTransferJourneys(String, String, int, double)}，附加计算耗时上限。
     *
     * @param deadlineNanos {@code System.nanoTime()} 的截止时间；{@code <=0} 表示不限时。超时则停止实体化
     *                      剩余候选换乘站，返回目前已找到的部分方案（非错误）
     */
    public static List<JourneyPlan> findTransferJourneys(String startStation, String endStation,
                                                         int maxResults, double minImprovement,
                                                         long deadlineNanos) {
        return new Searcher(deadlineNanos).findTransferJourneys(startStation, endStation, maxResults, minImprovement);
    }

    /**
     * 换乘候选：在 {@code alightStation} 下车，在 {@code boardStation} 上车（同名或同一车站组），
     * {@code estTotal} 为按站名距离矩阵估计的总距离下界。
     */
    private record TransferCandidate(String alightStation, String boardStation, double estTotal) {
    }

    /**
     * 换乘候选实体化倍数：最终要 {@code maxResults} 条，实体化前 {@code maxResults × 此值} 个下界最优候选，
     * 给「下界乐观、实体化后被阈值淘汰」留缓冲。
     */
    private static final int MATERIALIZE_FACTOR = 3;
    /**
     * 换乘候选实体化的最小个数下限（{@code maxResults} 很小时也至少考察这么多候选）。
     */
    private static final int MATERIALIZE_MIN = 8;

    /**
     * 换乘寻路（门槛=严格短于直达，等价 {@code findTransferJourneys(start, end, maxResults, 0)}）。
     *
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @param maxResults   最多返回方案数（{@code <=0} 不限制）
     * @return 换乘方案列表
     */
    public static List<JourneyPlan> findTransferJourneys(String startStation, String endStation, int maxResults) {
        return findTransferJourneys(startStation, endStation, maxResults, 0.0);
    }

    /**
     * 入向面门控：判断沿 {@code inLink} 到达当前节点后，是否允许接着走 {@code outLink}。
     * <p>
     * 只有当 {@code outLink} 声明了允许到达面集合（{@link GeoLink#getEnterFacesFrom()} 非空）、
     * {@code inLink} 也带到达面（{@link GeoLink#getEnterFaceTo()} 非 null），且该到达面不在允许集合内时，
     * 才拒绝。任一信息缺失（起点首段无入边、旧 geojson 无门控字段）都放行，保证向后兼容与起点正常展开。
     *
     * @param inLink  到达当前节点所走的入边（起点条目为 null）
     * @param outLink 待扩展的出边
     * @return 允许接续返回 true
     */
    private static boolean enterFaceAllows(GeoLink inLink, GeoLink outLink) {
        if (inLink == null) {
            return true;
        }
        if (outLink.getEnterFacesFrom().isEmpty()) {
            return true;
        }
        String arrivedFace = inLink.getEnterFaceTo();
        if (arrivedFace == null) {
            return true;
        }
        return outLink.getEnterFacesFrom().contains(arrivedFace);
    }

    /**
     * 同 {@code fTieEpsilon}：f = g + h 中 h 是反向累加的，同一长度的两条路线算出的 f 可能差几个 ulp，
     * 差值小于此值（米）视为等长，再按转线次数决胜。
     */
    private static final double F_TIE_EPSILON = 1e-6;

    /**
     * 终点站名在一次查询内的预处理结果，供 {@link Searcher#kShortest} 做 A* 剪枝。
     * <p>
     * {@code h[l]} 是「沿边 l 到达其终点节点之后」到任一终点站节点的最短剩余距离（米）：套用 kShortest 除
     * 「不可重复经过同一节点 / 同名站」之外的全部边约束（入向面门控 / 停靠线中途站跳过 / 折返站跳过 /
     * 仅展示线路跳过 / 终点站不再穿越），不可达为 +Inf。状态取「到达所经的边」，因为入向面门控只取决于它的到达面。
     * <p>
     * 忽略重复经过约束只会让剩余距离更短，故 h 是真实剩余距离的下界（可采纳）；又对每个合法接续 l→l'
     * 有 {@code h[l] <= w(l') + h[l']}（一致），A* 按 g+h 出队时终点仍按真实距离非降序出队，找到的 K 条路线
     * 与按已走距离出队的朴素枚举一致（仅等长路线之间的先后可能不同）。h=+Inf 的边永远到不了终点，直接剪掉——
     * 这同时取代了原来「先跑一遍可达性 Dijkstra」的短路判断。
     */
    private static final class Target {
        final int endName;
        final double[] h;

        Target(int endName, double[] h) {
            this.endName = endName;
            this.h = h;
        }

        boolean isEnd(GeoSearchIndex ix, int node) {
            return endName >= 0 && ix.isStation[node] && ix.nodeName[node] == endName;
        }

        /**
         * 从起点（尚未经过任何边，入向面门控对第一跳恒放行）出发的剩余距离下界。
         */
        double startH(GeoSearchIndex ix, int start) {
            double best = Double.POSITIVE_INFINITY;
            for (int l : ix.out[start]) {
                best = Math.min(best, ix.links[l].getDistance() + h[l]);
            }
            return best;
        }
    }

    /**
     * 在边状态图上从全部终点站反向跑 Dijkstra 得到 {@link Target}。超时返回 null。
     */
    private static Target computeTarget(GeoSearchIndex ix, String endStation, long deadlineNanos) {
        int endName = ix.nameIdOf(endStation);
        int m = ix.links.length;
        double[] h = new double[m];
        Arrays.fill(h, Double.POSITIVE_INFINITY);
        Target t = new Target(endName, h);
        // blocked：kShortest 必然剪掉的边——目标节点不存在、属于仅展示线路，或是「非终点」中途车站且经停靠线进入 / 属于折返站
        boolean[] blocked = new boolean[m];
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
        for (int l = 0; l < m; l++) {
            int to = ix.linkTo[l];
            GeoNode toNode = ix.nodes[to];
            if (toNode == null || LineConfig.isDisplayOnly(ix.links[l].getLineId())) {
                blocked[l] = true;
                continue;
            }
            if (t.isEnd(ix, to)) {
                h[l] = 0;
                pq.add(new double[]{0, l});
                continue;
            }
            if (toNode.isStation()) {
                GeoLink link = ix.links[l];
                if (link.isSiding() || LineInfo.isReverseStation(link.getLineId(), toNode.getName())) {
                    blocked[l] = true;
                }
            }
        }

        int pops = 0;
        while (!pq.isEmpty()) {
            if (deadlineNanos > 0 && pops % 4096 == 0 && System.nanoTime() > deadlineNanos) {
                return null;
            }
            double[] cur = pq.poll();
            pops++;
            int l = (int) cur[1];
            if (cur[0] > h[l]) {
                continue; // 过期条目
            }
            int u = ix.linkFrom[l];
            if (t.isEnd(ix, u)) {
                continue; // 到达终点即停止，不会从终点站接着走 l
            }
            GeoLink next = ix.links[l];
            double nd = cur[0] + next.getDistance();
            for (int p : ix.in[u]) {
                if (blocked[p] || nd >= h[p] || !enterFaceAllows(ix.links[p], next)) {
                    continue;
                }
                h[p] = nd;
                pq.add(new double[]{nd, p});
            }
        }
        return t;
    }

    /**
     * KSP 搜索树上的一个条目：到达某节点的一条具体路径前缀（无环），用 {@link #prev} 串成链表回溯整条路径。
     * {@code nodeMask} / {@code nameMask} 是路径上节点 / 已确定站名（严格祖先贡献的 crossName）的 64 位
     * 布隆过滤器：对应位未置位即可 O(1) 判定「不在路径上」，置位时才回溯精确确认。
     */
    private record Entry(int node, int link, double g, double f, int transfers, Entry prev,
                         long nodeMask, long nameMask) {
    }

    /**
     * 出队顺序：f（已走 + 剩余下界）小者优先；f 视为相等时转线次数少者优先。平行轨道上常有多条等长路线，
     * 先出队的会被选作该站台的结果，与 {@link #isBetterRoute}「转线少者优先」一致。
     */
    private static int compareEntry(Entry a, Entry b) {
        double d = a.f() - b.f();
        if (d < -F_TIE_EPSILON || d > F_TIE_EPSILON) {
            return d < 0 ? -1 : 1;
        }
        if (a.transfers() != b.transfers()) {
            return Integer.compare(a.transfers(), b.transfers());
        }
        return Double.compare(a.f(), b.f());
    }

    private static long bloomBit(int i) {
        return 1L << (i & 63);
    }

    /**
     * 一次查询内共享的寻路上下文：整数化图索引 + 按终点站名缓存的 A* 启发式 + K-最短路结果缓存。
     * 同一次查询里多次以同一站为终点寻路（如联程票的所有第二段都以终点站为终点）时，启发式只算一次。
     * 不跨线程共享，每次查询各建一个。包可见仅供同包单测直接验证 {@link #kShortest}。
     */
    static final class Searcher {
        private final GeoRouteGraph g = graph;
        private final GeoSearchIndex ix = g.searchIndex();
        private final long deadlineNanos;
        private final Map<String, Target> targets = new HashMap<>();
        /**
         * (起点节点 id, 终点站名) → kShortest 结果。kShortest 是确定性的，且只在出队前检查条数，故 k 条的结果
         * 恰是更大 k 结果的前缀：已算过更大 k、或已穷尽（结果不足 k 条且未超时）时直接取前缀。
         */
        private final Map<List<String>, KspResult> ksp = new HashMap<>();

        private record KspResult(List<GeoRoutePath> paths, int k, boolean exhausted) {
        }

        Searcher(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        private boolean expired() {
            return deadlineNanos > 0 && System.nanoTime() > deadlineNanos;
        }

        private Target target(String endStation) {
            Target t = targets.get(endStation);
            if (t == null) {
                t = computeTarget(ix, endStation, deadlineNanos);
                if (t != null) {
                    targets.put(endStation, t);
                }
            }
            return t;
        }

        /**
         * 见 {@link GeoRouteEngine#findByStation(String, String, int, long)}。
         */
        List<GeoRoutePath> findByStation(String startStation, String endStation, int maxResults) {
            // 每个起点站台各求 K 条最短路；K 取请求条数，未限制时退到安全上限
            int kPerPlatform = maxResults > 0 ? maxResults : KSP_SAFETY_CAP;
            List<GeoRoutePath> all = new ArrayList<>();
            for (GeoNode start : g.stationNodes(startStation)) {
                all.addAll(kShortestCached(start.getId(), endStation, kPerPlatform));
                if (expired()) {
                    break; // 超时：不再枚举剩余站台，已累积的部分结果仍走下面的去重/排序/截断逻辑返回
                }
            }

            // 一级去重：departDirectionSequence 相同视为重复路线，保留转线次数少的一条
            Map<List<String>, GeoRoutePath> deduped = new HashMap<>();
            for (GeoRoutePath path : all) {
                List<String> departDirectionSequence = path.getDepartDirectionSequence();
                GeoRoutePath old = deduped.get(departDirectionSequence);
                if (old == null || isBetterRoute(path, old)) {
                    deduped.put(departDirectionSequence, path);
                }
            }

            // 二级去重：stationSequence（经过的车站序列）相同也视为同一条路线，保留转线次数少者、次数相同保留距离短者。
            Map<List<String>, GeoRoutePath> byStations = new HashMap<>();
            for (GeoRoutePath path : deduped.values()) {
                List<String> stationSequence = path.stationSequence();
                GeoRoutePath old = byStations.get(stationSequence);
                if (old == null || isBetterRoute(path, old)) {
                    byStations.put(stationSequence, path);
                }
            }

            List<GeoRoutePath> ret = new ArrayList<>(byStations.values());
            ret.sort(Comparator.comparingDouble(GeoRoutePath::getDistance));
            if (maxResults > 0 && ret.size() > maxResults) {
                return new ArrayList<>(ret.subList(0, maxResults));
            }
            return ret;
        }

        /**
         * 带查询内缓存的 {@link #kShortest}，返回的列表只读。
         */
        private List<GeoRoutePath> kShortestCached(String startNodeId, String endStation, int k) {
            List<String> key = List.of(startNodeId, endStation);
            KspResult c = ksp.get(key);
            if (c != null && (c.k() >= k || c.exhausted())) {
                return c.paths().size() > k ? c.paths().subList(0, k) : c.paths();
            }
            List<GeoRoutePath> paths = kShortest(startNodeId, endStation, k);
            if (!expired()) {
                ksp.put(key, new KspResult(paths, k, paths.size() < k));
                return paths;
            }
            // 超时：已找到的几条仍是完整结果的前缀，按实际条数缓存（不标记穷尽），之后同一查询里
            // 再要不超过这么多条时直接复用，不会因为已超时而一条都拿不到
            if (c != null && c.paths().size() >= paths.size()) {
                return c.paths().size() > k ? c.paths().subList(0, k) : c.paths();
            }
            ksp.put(key, new KspResult(paths, paths.size(), false));
            return paths;
        }

        /**
         * 从单一起点节点求最短的 K 条<b>无环</b>路线，终点为任一名为 {@code endStation} 的 station 节点。
         * <p>
         * 规则：一条路线<b>不得重复经过同一节点</b>（simple path），也不得重复经过同名车站。按「已走距离 +
         * 剩余距离下界」（A*，见 {@link Target}）排序的优先队列逐条扩展，扩展时跳过「已在当前路径回溯链中」的
         * 下一节点以保证无环；每弹出一个「终点站名 station 节点且已走过至少一段」即记一条路线，直到凑满 K 条
         * 或队列耗尽。由此支持：
         * <ul>
         *   <li>同一起点站台到任一终点站台的多条无环候选（K 条）；</li>
         *   <li>起终点同站名时「绕到同名车站的<b>另一个</b>站台节点」——终点节点与沿途节点均不重复。</li>
         * </ul>
         * 剩余距离为 +Inf（到不了终点）的分支直接剪掉，两站根本无解时一条都不展开；另设
         * {@link #KSP_MAX_POPS} 出队总数封顶兜底，保证终止。超时则停止枚举，返回目前已找到的部分结果
         * （非错误，语义同「凑不满 K 条」）。
         *
         * @param startNodeId 起点节点 id
         * @param endStation  终点站名
         * @param k           最多求多少条（>=1）
         * @return 按距离升序的至多 K 条无环路径；起点不存在 / 无解返回空列表
         */
        List<GeoRoutePath> kShortest(String startNodeId, String endStation, int k) {
            List<GeoRoutePath> results = new ArrayList<>();
            Integer startIdx = ix.nodeIdx.get(startNodeId);
            if (startIdx == null || ix.nodes[startIdx] == null || endStation == null || k < 1) {
                return results;
            }
            int start = startIdx;
            Target t = target(endStation);
            if (t == null) {
                return results; // 计算启发式时已超时
            }
            double startH = t.startH(ix, start);
            if (Double.isInfinite(startH)) {
                return results;
            }
            int startName = ix.nodeName[start]; // 起点站名下标（起点非车站时为 -1）

            PriorityQueue<Entry> pq = new PriorityQueue<>(GeoRouteEngine::compareEntry);
            pq.add(new Entry(start, -1, 0.0, startH, 0, null, bloomBit(start), 0L));

            int pops = 0;
            while (!pq.isEmpty() && results.size() < k && pops < KSP_MAX_POPS) {
                if (deadlineNanos > 0 && pops % 4096 == 0 && System.nanoTime() > deadlineNanos) {
                    break; // 超时：返回已找到的部分结果（非空时是可用的真实路径，语义同「凑不满 K 条」）
                }
                Entry cur = pq.poll();
                pops++;

                int u = cur.node();
                // 到达终点站名的 station 节点且已走过至少一段（起点零长不算）→ 记一条路线，不再从此继续扩展
                if (cur.link() >= 0 && t.isEnd(ix, u)) {
                    results.add(buildPath(cur));
                    continue;
                }
                GeoLink inLink = cur.link() >= 0 ? ix.links[cur.link()] : null;
                boolean uIsSwitch = !ix.isStation[u];
                for (int l : ix.out[u]) {
                    // h=+Inf 已涵盖：目标节点不存在、仅展示线路、经停靠线进入中途站（该站有正线可跨越）、折返站，
                    // 以及之后无论如何到不了终点
                    double h = t.h[l];
                    if (Double.isInfinite(h)) {
                        continue;
                    }
                    GeoLink link = ix.links[l];
                    // 入向面门控：同一物理方块上多块进入方向不同的 bcswitcher 塌缩为同一节点，本段边只对
                    // 「从某些到达面到达该道岔」的车合法。若入边到达面不在本段允许集合内，跳过——避免读到
                    // 反向牌的出边、算出物理非法路线（如从右侧来的车走了只给左侧来车准备的直行出边）。
                    if (!enterFaceAllows(inLink, link)) {
                        continue;
                    }
                    int v = ix.linkTo[l];
                    boolean nextIsTerminal = t.isEnd(ix, v);
                    // 进站道岔走正线时代表被跨越的车站
                    int curName = uIsSwitch ? ix.crossName[l] : -1;
                    if (curName >= 0 && repeatsStation(cur, curName, startName, t.endName, nextIsTerminal)) {
                        continue;
                    }
                    int nextName = ix.nodeName[v];
                    if (nextName >= 0 && nextName != curName
                            && repeatsStation(cur, nextName, startName, t.endName, nextIsTerminal)) {
                        continue;
                    }
                    // 无环约束：下一节点若已在当前路径中，跳过——避免重复经过同一节点。
                    // 例外：允许最后一步回到起点节点以支持首尾节点相同的环线
                    if (inPath(cur, v) && !(v == start && nextIsTerminal)) {
                        continue;
                    }
                    double g = cur.g() + link.getDistance();
                    int transfers = cur.transfers();
                    if (inLink != null && !Objects.equals(inLink.getLineId(), link.getLineId())) {
                        transfers++;
                    }
                    int cross = ix.crossName[l];
                    pq.add(new Entry(v, l, g, g + h, transfers, cur, cur.nodeMask() | bloomBit(v),
                            cross >= 0 ? cur.nameMask() | bloomBit(cross) : cur.nameMask()));
                }
            }
            return results;
        }

        private static boolean inPath(Entry entry, int node) {
            if ((entry.nodeMask() & bloomBit(node)) == 0) {
                return false;
            }
            for (Entry e = entry; e != null; e = e.prev()) {
                if (e.node() == node) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 车站 {@code name} 已在路径上出现过，且不是「回到同名起点站闭合环线」这一特例。
         */
        private boolean repeatsStation(Entry cur, int name, int startName, int endName, boolean nextIsTerminal) {
            if (!stationInPath(cur, name)) {
                return false;
            }
            return !nextIsTerminal || name != startName || name != endName;
        }

        /**
         * 路径上各节点按「车站节点取站名、进站道岔沿下一条出边走正线取被跨越的站名」后是否出现过 {@code name}。
         * 末端节点尚无出边，只计其自身站名。
         */
        private boolean stationInPath(Entry entry, int name) {
            if (ix.nodeName[entry.node()] == name) {
                return true;
            }
            if ((entry.nameMask() & bloomBit(name)) == 0) {
                return false;
            }
            for (Entry e = entry; e.prev() != null; e = e.prev()) {
                if (ix.crossName[e.link()] == name) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 从 {@link Entry} 回溯链构建有序节点列表、逐段 lineId 序列与逐段物理出向序列（距离换算为 km）。
         */
        private GeoRoutePath buildPath(Entry endEntry) {
            List<GeoNode> nodes = new ArrayList<>();
            List<String> lineIds = new ArrayList<>();
            List<String> departDirs = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            Entry cur = endEntry;
            // 回溯到起点条目（prev == null 即起点）
            while (cur.prev() != null) {
                GeoLink link = ix.links[cur.link()];
                nodes.add(ix.nodes[cur.node()]);
                lineIds.add(link.getLineId());
                departDirs.add(link.getDepartDirection());
                // 段长换算为 km，与总距离单位一致
                distances.add(link.getDistance() / 1000);
                cur = cur.prev();
            }
            nodes.add(ix.nodes[cur.node()]);
            Collections.reverse(nodes);
            Collections.reverse(lineIds);
            Collections.reverse(departDirs);
            Collections.reverse(distances);
            return new GeoRoutePath(nodes, lineIds, departDirs, distances, endEntry.g() / 1000);
        }

        /**
         * 见 {@link GeoRouteEngine#findTransferJourneys(String, String, int, double, long)}。
         */
        List<JourneyPlan> findTransferJourneys(String startStation, String endStation, int maxResults,
                                               double minImprovement) {
            if (startStation == null || endStation == null || startStation.equals(endStation)) {
                return new ArrayList<>();
            }

            // 直达最短距离 → 换乘须短于的阈值。无直达则为正无穷，此时任何换乘方案都接受。
            List<GeoRoutePath> directPaths = findByStation(startStation, endStation, 0);
            double bestDirect = Double.POSITIVE_INFINITY;
            for (GeoRoutePath p : directPaths) {
                bestDirect = Math.min(bestDirect, p.getDistance());
            }
            // 有直达时套用最低改善门槛：换乘总距离须严格小于此阈值
            double threshold = bestDirect;
            if (!Double.isInfinite(bestDirect) && minImprovement > 0) {
                threshold = bestDirect * (1.0 - minImprovement);
            }

            // 用站名级缩合距离矩阵（~78 个站名，非 ~639 个物理节点）枚举<b>全部</b>换乘站，按估计总距离
            // （下界）预筛：凡「start 一趟直达 mid」且「mid 一趟直达 end」均可达，且估计总距离比阈值更近的
            // mid 都是候选。缩合距离忽略了 enterFace/折返等约束是乐观下界，只用于选候选并排序，最终每段仍由
            // findByStation 权威实体化施加全部约束。据估计总距离升序排序，只对最有潜力的前若干个做实体化。
            Map<String, Map<String, Double>> matrix = g.stationDirectDistances();
            // 车站组：leg1 在 mid1 下车后，可在同组任一车站 mid2（含 mid1 自身）上车继续 leg2。
            Map<String, Double> fromStart = matrix.getOrDefault(startStation, Collections.emptyMap());
            List<TransferCandidate> ranked = new ArrayList<>();
            for (Map.Entry<String, Double> e : fromStart.entrySet()) {
                String mid1 = e.getKey();
                if (mid1.equals(startStation) || mid1.equals(endStation)) {
                    continue;
                }
                for (String mid2 : StationGroupConfig.groupMembers(mid1)) {
                    if (mid2.equals(startStation) || mid2.equals(endStation)) {
                        continue;
                    }
                    Double midToEnd = matrix.getOrDefault(mid2, Collections.emptyMap()).get(endStation);
                    if (midToEnd == null) {
                        continue; // mid2 到不了终点，直接跳过
                    }
                    double estTotal = e.getValue() + midToEnd;
                    if (estTotal >= threshold) {
                        continue; // 下界都不比阈值近，实体化后只会更远，无需考察
                    }
                    ranked.add(new TransferCandidate(mid1, mid2, estTotal));
                }
            }
            ranked.sort(Comparator.comparingDouble(TransferCandidate::estTotal));

            // 只对最有潜力的前若干候选做实体化（每个要跑两段 findByStation）；取需要条数的数倍作缓冲，
            // 兼顾「下界乐观导致个别候选实体化后被阈值淘汰」的情形。maxResults<=0（不限）时实体化全部候选。
            int materializeCap = maxResults > 0 ? Math.max(maxResults * MATERIALIZE_FACTOR, MATERIALIZE_MIN) : Integer.MAX_VALUE;

            // 逐候选实体化两段真实路径，按 (下车站, 上车站) 去重（留真实总距离最短者）
            Map<List<String>, JourneyPlan> byTransfer = new HashMap<>();
            // 首段只取决于 mid1、次段只取决于 mid2，分别缓存，避免同一车站出现在多个候选里时重复寻路；
            // 所有次段终点相同，A* 启发式由 Searcher 按终点缓存只算一次。
            Map<String, List<GeoRoutePath>> leg1Cache = new HashMap<>();
            Map<String, List<GeoRoutePath>> leg2Cache = new HashMap<>();
            // 已接受方案的总距离（升序），用于分支定界提前结束
            List<Double> kept = new ArrayList<>();
            int materialized = 0;
            for (TransferCandidate cand : ranked) {
                if (materialized >= materializeCap) {
                    break;
                }
                // 分支定界：候选按下界升序，已凑够 maxResults 个方案且第 maxResults 名不比本候选下界差时，
                // 后面的候选真实总距离都 >= 下界，不可能挤进前 maxResults，结果与继续实体化到上限相同。
                if (maxResults > 0 && kept.size() >= maxResults && kept.get(maxResults - 1) <= cand.estTotal()) {
                    break;
                }
                if (expired()) {
                    // 超时：不再实体化剩余候选站，已实体化成功的换乘方案（如果有）仍是真实完整的方案，
                    // 继续走下面的排序/截断逻辑返回它们，而不是一并丢弃。
                    break;
                }
                String mid1 = cand.alightStation();
                String mid2 = cand.boardStation();
                List<GeoRoutePath> leg1List = leg1Cache.computeIfAbsent(mid1,
                        m -> findByStation(startStation, m, 1));
                if (leg1List.isEmpty()) {
                    continue;
                }
                List<GeoRoutePath> leg2List = leg2Cache.computeIfAbsent(mid2,
                        m -> findByStation(m, endStation, 1));
                if (leg2List.isEmpty()) {
                    continue;
                }
                materialized++;
                GeoRoutePath leg1 = leg1List.getFirst();
                GeoRoutePath leg2 = leg2List.getFirst();
                double total = leg1.getDistance() + leg2.getDistance();
                // 实体化后的真实总距离仍须比阈值更近（下界乐观，实体化后可能变远被淘汰）
                if (total >= threshold) {
                    continue;
                }
                JourneyPlan plan = new JourneyPlan(List.of(leg1, leg2), List.of(mid1));
                List<String> key = List.of(mid1, mid2);
                JourneyPlan old = byTransfer.get(key);
                if (old == null || plan.getTotalDistance() < old.getTotalDistance()) {
                    byTransfer.put(key, plan);
                    int pos = Collections.binarySearch(kept, plan.getTotalDistance());
                    kept.add(pos < 0 ? -pos - 1 : pos, plan.getTotalDistance());
                }
            }

            List<JourneyPlan> ret = new ArrayList<>(byTransfer.values());
            ret.sort(Comparator.comparingDouble(JourneyPlan::getTotalDistance));
            if (maxResults > 0 && ret.size() > maxResults) {
                return new ArrayList<>(ret.subList(0, maxResults));
            }
            return ret;
        }
    }
}
