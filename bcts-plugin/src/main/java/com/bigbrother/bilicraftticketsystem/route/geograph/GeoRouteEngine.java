package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * 基于 geojson 路由图的寻路引擎（直达，不含换乘）。
 * <p>
 * 持有自己的 {@link GeoRouteGraph} 单例。寻路用 Dijkstra（有向图，边权 = geojson length）。
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
     * 单次 {@link #kShortest} 的出队总数封顶，兜底防止超大图上枚举过久（无环约束已使路径有限，此为额外保险）。
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
     * 支持起终点站相同，见 {@link #kShortest}。
     *
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @param maxResults   最多返回条数（<=0 不限制）
     * @return 路径列表（按距离升序、已去重），无解返回空列表
     */
    public static List<GeoRoutePath> findByStation(String startStation, String endStation, int maxResults) {
        // 每个起点站台各求 K 条最短路；K 取请求条数，未限制时退到安全上限
        int kPerPlatform = maxResults > 0 ? maxResults : KSP_SAFETY_CAP;
        List<GeoRoutePath> all = new ArrayList<>();
        for (GeoNode start : graph.stationNodes(startStation)) {
            all.addAll(kShortest(start.getId(), endStation, kPerPlatform));
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
        List<GeoRoutePath> paths = kShortest(startNodeId, endStation, 1);
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
     * 然后按既有图结构重建路径（与私有 {@link #buildPath} 同构的产物，下游票价 / lore / 导航逻辑零改动）。
     * <p>
     * 校验规则：
     * <ul>
     *   <li>{@code nodeIds} 至少含起点与终点两个节点，且首尾都是 station 节点（与车票语义一致）。</li>
     *   <li>逐对 {@code nodeIds[i] -> nodeIds[i+1]} 须存在一条出边（{@link GeoRouteGraph#links}）；</li>
     *   <li>两节点间存在多条平行边（共用轨道、不同 lineId）时，用 {@code lineIdSequence[i]} 消歧；
     *       {@code lineIdSequence} 为 null 时取首条匹配边。</li>
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
        if (startStation == null || endStation == null || startStation.equals(endStation)) {
            return new ArrayList<>();
        }
        GeoRouteGraph g = graph;

        // 直达最短距离 → 换乘须短于的阈值。无直达则为正无穷，此时任何换乘方案都接受。
        List<GeoRoutePath> directPaths = findByStation(startStation, endStation);
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
        Map<String, Double> fromStart = matrix.getOrDefault(startStation, Collections.emptyMap());
        List<Map.Entry<String, Double>> ranked = new ArrayList<>();
        for (Map.Entry<String, Double> e : fromStart.entrySet()) {
            String mid = e.getKey();
            if (mid.equals(startStation) || mid.equals(endStation)) {
                continue;
            }
            Double midToEnd = matrix.getOrDefault(mid, Collections.emptyMap()).get(endStation);
            if (midToEnd == null) {
                continue; // mid 到不了终点，直接跳过
            }
            double estTotal = e.getValue() + midToEnd;
            if (estTotal >= threshold) {
                continue; // 下界都不比阈值近，实体化后只会更远，无需考察
            }
            ranked.add(Map.entry(mid, estTotal));
        }
        ranked.sort(Map.Entry.comparingByValue());

        // 只对最有潜力的前若干候选做实体化（每个要跑两段 findByStation）；取需要条数的数倍作缓冲，
        // 兼顾「下界乐观导致个别候选实体化后被阈值淘汰」的情形。maxResults<=0（不限）时实体化全部候选。
        int materializeCap = maxResults > 0 ? Math.max(maxResults * MATERIALIZE_FACTOR, MATERIALIZE_MIN) : Integer.MAX_VALUE;

        // 逐候选站实体化两段真实路径，按换乘站去重（留真实总距离最短者）
        Map<String, JourneyPlan> byTransfer = new HashMap<>();
        int materialized = 0;
        for (Map.Entry<String, Double> entry : ranked) {
            if (materialized >= materializeCap) {
                break;
            }
            String mid = entry.getKey();
            List<GeoRoutePath> leg1List = findByStation(startStation, mid, 1);
            if (leg1List.isEmpty()) {
                continue;
            }
            List<GeoRoutePath> leg2List = findByStation(mid, endStation, 1);
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
            JourneyPlan plan = new JourneyPlan(List.of(leg1, leg2), List.of(mid));
            JourneyPlan old = byTransfer.get(mid);
            if (old == null || plan.getTotalDistance() < old.getTotalDistance()) {
                byTransfer.put(mid, plan);
            }
        }

        List<JourneyPlan> ret = new ArrayList<>(byTransfer.values());
        ret.sort(Comparator.comparingDouble(JourneyPlan::getTotalDistance));
        if (maxResults > 0 && ret.size() > maxResults) {
            return new ArrayList<>(ret.subList(0, maxResults));
        }
        return ret;
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
     * 从单一起点节点求最短的 K 条<b>无环</b>路线，终点为任一名为 {@code endStation} 的 station 节点。
     * <p>
     * 规则：一条路线<b>不得重复经过同一节点</b>（simple path）。采用按累计距离排序的优先队列逐条扩展，
     * 扩展时跳过「已在当前路径回溯链中」的下一节点以保证无环；每弹出一个「终点站名 station 节点且已走过
     * 至少一段」即记一条路线，直到凑满 K 条或队列耗尽。由此支持：
     * <ul>
     *   <li>同一起点站台到任一终点站台的多条无环候选（K 条）；</li>
     *   <li>起终点同站名时「绕到同名车站的<b>另一个</b>站台节点」——终点节点与沿途节点均不重复。</li>
     * </ul>
     * 无环约束 + 有限节点数使路径数有限；另设 {@link #KSP_MAX_POPS} 出队总数封顶兜底，保证终止。
     *
     * @param startNodeId 起点节点 id
     * @param endStation  终点站名
     * @param k           最多求多少条（>=1）
     * @return 按距离升序的至多 K 条无环路径；起点不存在 / 无解返回空列表
     */
    private static List<GeoRoutePath> kShortest(String startNodeId, String endStation, int k) {
        GeoRouteGraph g = graph;
        List<GeoRoutePath> results = new ArrayList<>();
        GeoNode startNode = g.getNode(startNodeId);
        if (startNode == null || endStation == null || k < 1) {
            return results;
        }
        String startStation = startNode.isStation() ? startNode.getName() : null;
        PriorityQueue<Entry> pq = new PriorityQueue<>(Comparator.comparingDouble(Entry::dist));
        pq.add(new Entry(startNodeId, 0.0, null, null));

        int pops = 0;
        while (!pq.isEmpty() && results.size() < k && pops < KSP_MAX_POPS) {
            Entry cur = pq.poll();
            pops++;

            GeoNode curNode = g.getNode(cur.nodeId());
            // 到达终点站名的 station 节点且已走过至少一段（起点零长不算）→ 记一条路线，不再从此继续扩展
            if (curNode != null && curNode.isStation() && endStation.equals(curNode.getName())
                    && cur.prevLink() != null) {
                results.add(buildPath(g, startNodeId, cur));
                continue;
            }
            for (GeoLink link : g.links(cur.nodeId())) {
                String nextId = link.getToNodeId();
                GeoNode nextNode = g.getNode(nextId);
                if (nextNode == null) {
                    continue;
                }
                // 入向面门控：同一物理方块上多块进入方向不同的 bcswitcher 塌缩为同一节点，本段边只对
                // 「从某些到达面到达该道岔」的车合法。若入边到达面不在本段允许集合内，跳过——避免读到
                // 反向牌的出边、算出物理非法路线（如从右侧来的车走了只给左侧来车准备的直行出边）。
                if (!enterFaceAllows(cur.prevLink(), link)) {
                    continue;
                }
                String curStationName = curNode != null && !curNode.isStation()
                        ? getNodeStationName(g, curNode, link.getLineId()) : null;
                boolean nextIsTerminal = nextNode.isStation() && endStation.equals(nextNode.getName());
                if (repeatsStation(g, cur, curStationName, startStation, endStation, nextIsTerminal)) {
                    continue;
                }
                String nextStationName = nextNode.isStation() ? nextNode.getName() : null;
                if (nextStationName != null && !nextStationName.equals(curStationName)
                        && repeatsStation(g, cur, nextStationName, startStation, endStation, nextIsTerminal)) {
                    continue;
                }
                // 无环约束：下一节点若已在当前路径中，跳过——避免重复经过同一节点。
                // 例外：允许最后一步回到起点节点以支持首尾节点相同的环线
                if (inPath(cur, nextId)) {
                    boolean closesLoop = nextId.equals(startNodeId)
                            && nextNode.isStation() && endStation.equals(nextNode.getName());
                    if (!closesLoop) {
                        continue;
                    }
                }
                // 下一个节点是非终点的车站节点
                if (nextNode.isStation() && !endStation.equals(nextNode.getName())) {
                    // 中途站避让正线：存在正线绕行时，放弃穿越该 station。
                    if (hasMainlineBypass(g, cur.nodeId(), nextNode)) {
                        continue;
                    }
                    // 下一个节点是折返站节点
                    if (LineInfo.isReverseStation(link.getLineId(), nextNode.getName())) {
                        continue;
                    }
                }
                pq.add(new Entry(nextId, cur.dist() + link.getDistance(), link, cur));
            }
        }
        return results;
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

    private static boolean inPath(Entry entry, String nodeId) {
        for (Entry e = entry; e != null; e = e.prev()) {
            if (e.nodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean repeatsStation(GeoRouteGraph g, Entry cur, @Nullable String stationName,
                                          @Nullable String startStation, String endStation, boolean nextIsTerminal) {
        if (stationName == null || !stationInPath(g, cur, stationName)) {
            return false;
        }
        return !nextIsTerminal || !stationName.equals(startStation) || !stationName.equals(endStation);
    }

    private static boolean stationInPath(GeoRouteGraph g, Entry entry, String stationName) {
        GeoLink outgoing = null;
        for (Entry e = entry; e != null; e = e.prev()) {
            GeoNode node = g.getNode(e.nodeId());
            String currentStation = node == null ? null
                    : (node.isStation() ? node.getName()
                    : (outgoing == null ? null : getNodeStationName(g, node, outgoing.getLineId())));
            if (stationName.equals(currentStation)) {
                return true;
            }
            outgoing = e.prevLink();
        }
        return false;
    }

    /**
     * 如果某条线路的某个道岔是进站道岔（两个出边lineId相同，一条是正线，一条是停靠线，停靠线连接station节点，结构如下），返回对应的车站名
     * 注：规定只有进站道岔可能会有两个出边的lineId相同
     *     /-->S(车站)-->\
     *   N1 ---(正线)---> N2
     *   N1 为进站道岔
     */
    private static @Nullable String getNodeStationName(GeoRouteGraph g, GeoNode node, String lineId) {
        if (node == null || lineId == null) {
            return null;
        }
        if (node.isStation()) {
            return node.getName();
        }
        return g.platformNameOfMainlineSwitch(node, lineId);
    }

    /**
     * KSP 的搜索条目：到达某节点的一条具体路径前缀（无环）。
     * 用 {@link #prev()} 串成链表回溯整条路径（不能用全局 prevLink，因不同路径前缀各异）。
     */
    private record Entry(String nodeId, double dist, GeoLink prevLink, Entry prev) {
    }


    /**
     * 结构判定某节点是否存在「正线绕行」——即该处有正线可越过停靠线车站。
     * <p>
     * 车站节点和进站道岔节点连接了同一个节点（出站道岔），即为有正线
     *
     * @param g                 路由图
     * @param nodeId            节点 id（一般是 station 前一个 switcher）
     * @param targetStationNode 进站道岔对应的车站节点
     * @return true 表示存在正线绕行
     */
    private static boolean hasMainlineBypass(GeoRouteGraph g, String nodeId, GeoNode targetStationNode) {
        List<GeoLink> stationLinks = g.links(targetStationNode.getId());
        if (stationLinks.isEmpty()) {
            return false;
        } else {
            for (GeoLink link : g.links(nodeId)) {
                GeoNode to = g.getNode(link.getToNodeId());
                if (to.coordEquals(g.getNode(stationLinks.getFirst().getToNodeId()))) {
                    // 连接了同一个坐标的出站道岔
                    // 这里用坐标不用id，是因为考虑两线共线情况
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从 KSP 的 {@link Entry} 回溯链构建有序节点列表、逐段 lineId 序列与逐段物理出向序列。
     * <p>
     * 沿 {@link Entry#prev} 从终点回溯到起点（链表自带每段所走的 {@link Entry#prevLink}，支持节点重复经过）。
     *
     * @param g           路由图
     * @param startNodeId 起点节点 id
     * @param endEntry    终点条目（回溯链尾）
     * @return 路径对象（距离换算为 km）
     */
    private static GeoRoutePath buildPath(GeoRouteGraph g, String startNodeId, Entry endEntry) {
        List<GeoNode> nodes = new ArrayList<>();
        List<String> lineIds = new ArrayList<>();
        List<String> departDirs = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        Entry cur = endEntry;
        // 回溯到起点条目（prev == null 即起点，其 prevLink 也为 null）
        while (cur != null && cur.prev != null) {
            nodes.add(g.getNode(cur.nodeId));
            lineIds.add(cur.prevLink == null ? null : cur.prevLink.getLineId());
            departDirs.add(cur.prevLink == null ? null : cur.prevLink.getDepartDirection());
            // 段长换算为 km，与总距离单位一致
            distances.add(cur.prevLink == null ? 0.0 : cur.prevLink.getDistance() / 1000);
            cur = cur.prev;
        }
        nodes.add(g.getNode(startNodeId));
        Collections.reverse(nodes);
        Collections.reverse(lineIds);
        Collections.reverse(departDirs);
        Collections.reverse(distances);
        return new GeoRoutePath(nodes, lineIds, departDirs, distances, (endEntry != null ? endEntry.dist : 0) / 1000);
    }
}
