package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

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
     * 从 geojson 目录加载 / 重载路由图，整体替换当前图。
     *
     * @param geodataDir geojson 目录
     * @param logger     日志（可为 null）
     */
    public static void load(File geodataDir, Logger logger) {
        graph = new GeoGraphLoader(logger).loadDir(geodataDir);
    }

    /**
     * 按起点站名 + 终点站名寻路：枚举起点站名下所有站台节点，各跑一次最短路到任一终点站台，
     * 按距离升序返回（去重相同站台起点的重复结果）。
     *
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @return 路径列表（按距离升序），无解返回空列表
     */
    public static List<GeoRoutePath> findByStation(String startStation, String endStation) {
        List<GeoRoutePath> results = new ArrayList<>();
        for (GeoNode start : graph.stationNodes(startStation)) {
            GeoRoutePath path = dijkstra(start.getId(), endStation);
            if (path != null) {
                results.add(path);
            }
        }
        results.sort(Comparator.comparingDouble(GeoRoutePath::getDistance));
        return results;
    }

    /**
     * 按起点站台节点 id + 终点站名寻路（交通卡：玩家在任意站台上车，按当前站台算最近路径）。
     *
     * @param startNodeId 起点站台节点 id
     * @param endStation  终点站名
     * @return 最短路径，无解返回 null
     */
    public static GeoRoutePath findFromNode(String startNodeId, String endStation) {
        return dijkstra(startNodeId, endStation);
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
     * 从单一起点节点跑 Dijkstra，到第一个（距离最近的）名为 endStation 的 station 节点。
     *
     * @param startNodeId 起点节点 id
     * @param endStation  终点站名
     * @return 路径，无解 / 起点不存在返回 null
     */
    private static GeoRoutePath dijkstra(String startNodeId, String endStation) {
        GeoRouteGraph g = graph;
        if (g.getNode(startNodeId) == null || endStation == null) {
            return null;
        }
        Map<String, Double> dist = new HashMap<>();
        Map<String, GeoLink> prevLink = new HashMap<>();
        Set<String> settled = new HashSet<>();
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist.getOrDefault(a, Double.MAX_VALUE)));
        dist.put(startNodeId, 0.0);
        pq.add(startNodeId);

        String endNodeId = null;
        while (!pq.isEmpty()) {
            String cur = pq.poll();
            if (!settled.add(cur)) {
                // 已确定最短距离的旧队列条目，跳过
                continue;
            }
            double curDist = dist.getOrDefault(cur, Double.MAX_VALUE);
            GeoNode curNode = g.getNode(cur);
            // 到达终点站名的 station 节点即停（起点本身不算终点，避免起终同站零长路径）
            if (curNode != null && curNode.isStation() && endStation.equals(curNode.getName())
                    && !cur.equals(startNodeId)) {
                endNodeId = cur;
                break;
            }
            for (GeoLink link : g.links(cur)) {
                String next = link.getToNodeId();
                GeoNode nextNode = g.getNode(next);
                if (nextNode == null || settled.contains(next)) {
                    continue;
                }
                // 中途站避让正线：若 next 是非终点的 station 节点，且当前节点 cur 存在一条 default
                // 出边（说明本站有正线可绕行），则放弃穿越该 station，让寻路改走正线跨站。
                // 终点 station 不避让（要在此停车）；本站无正线（cur 无 default 出边）时正常进站。
                if (nextNode.isStation() && !endStation.equals(nextNode.getName()) && hasDefaultBypass(g, cur)) {
                    continue;
                }
                double nd = curDist + link.getDistance();
                if (nd < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, nd);
                    prevLink.put(next, link);
                    pq.add(next);
                }
            }
        }
        if (endNodeId == null) {
            return null;
        }
        return buildPath(g, startNodeId, endNodeId, dist.get(endNodeId), prevLink);
    }

    /**
     * 判断某节点是否存在一条 {@code default}（正线）出边——即该处有正线可绕过站台。
     *
     * @param g      路由图
     * @param nodeId 节点 id（一般是 station 前一个 switcher）
     * @return true 表示存在正线绕行边
     */
    private static boolean hasDefaultBypass(GeoRouteGraph g, String nodeId) {
        for (GeoLink link : g.links(nodeId)) {
            if (LineInfo.DEFAULT_ID.equalsIgnoreCase(link.getLineId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 Dijkstra 的前驱链回溯，构建有序节点列表与逐段 lineId 序列。
     */
    private static GeoRoutePath buildPath(GeoRouteGraph g, String startNodeId, String endNodeId,
                                          double distance, Map<String, GeoLink> prevLink) {
        List<GeoNode> nodes = new ArrayList<>();
        List<String> lineIds = new ArrayList<>();
        String cur = endNodeId;
        while (cur != null && !cur.equals(startNodeId)) {
            nodes.add(g.getNode(cur));
            GeoLink link = prevLink.get(cur);
            lineIds.add(link == null ? null : link.getLineId());
            cur = link == null ? null : link.getFromNodeId();
        }
        nodes.add(g.getNode(startNodeId));
        Collections.reverse(nodes);
        Collections.reverse(lineIds);
        return new GeoRoutePath(nodes, lineIds, distance / 1000);
    }
}
