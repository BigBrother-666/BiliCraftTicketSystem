package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

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

        // 去重：departDirectionSequence 相同视为重复路线，保留先出现（即最短）的一条
        Map<List<String>, GeoRoutePath> deduped = new HashMap<>();
        for (GeoRoutePath path : all) {
            List<String> departDirectionSequence = path.getDepartDirectionSequence();
            if (!deduped.containsKey(departDirectionSequence)) {
                deduped.put(departDirectionSequence, path);
            } else {
                // departDirectionSequence 相同 则 优先保留转线次数少的
                List<String> oldLineIdSeq = deduped.get(departDirectionSequence).getLineIdSequence();
                if (getLineTransferCnt(oldLineIdSeq) > getLineTransferCnt(path.getLineIdSequence())) {
                    deduped.put(departDirectionSequence, path);
                }
            }
        }

        List<GeoRoutePath> ret = new ArrayList<>(deduped.values().stream().toList());
        ret.sort(Comparator.comparingDouble(GeoRoutePath::getDistance));
        if (maxResults > 0 && deduped.size() > maxResults) {
            return new ArrayList<>(ret.subList(0, maxResults));
        }
        return ret;
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
                matched = link;
                break;
            }
            if (matched == null) {
                return null;
            }
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
        if (g.getNode(startNodeId) == null || endStation == null || k < 1) {
            return results;
        }
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
     * 判断节点 {@code nodeId} 是否已出现在 {@code entry} 的回溯链（当前路径前缀）中。
     * 用于强制无环：沿 {@link Entry#prev()} 向起点回溯逐一比对。调用方对「回到起点闭合环线」单独放行。
     *
     * @param entry  当前路径条目
     * @param nodeId 待加入的下一节点 id
     * @return true 表示已在路径中
     */
    private static boolean inPath(Entry entry, String nodeId) {
        for (Entry e = entry; e != null; e = e.prev()) {
            if (e.nodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
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
