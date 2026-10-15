package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 一条行驶路线的几何轨迹（不可变）：按经过顺序的节点序列，相邻两节点之间为一「跳」（hop），
 * 每跳挂该节点对之间的所有候选边（平行边：不同 lineId / 正线与停靠线），用于把列车实时位置投影到
 * 路线上、求「从起点沿线已行驶距离」，驱动 bossbar 实时进度。
 * <p>
 * 累计距离以边的 {@code length}（{@link GeoLink#getDistance()}）为准，与购票里程口径一致；
 * 折线几何（{@link GeoPolyline}）只用来求跳内比例。纯逻辑，不依赖 Bukkit。
 */
public class GeoRouteTrack {
    /**
     * 构建普通车整线轨迹时，单段（站到站）Dijkstra 出队上限，兜底防止异常数据下搜索过久。
     */
    private static final int MAX_POPS = 50_000;
    /**
     * 构建普通车整线轨迹时，非本线 lineId 的边的代价倍率（优先沿本线走，必要时才借用联络线等）。
     */
    private static final double FOREIGN_LINE_PENALTY = 4.0;
    /**
     * 游标局部投影距离平方上限（格²）：列车在游标所指边上（离折线 1 格内）就不再全量扫描范围内其它边。
     */
    private static final double LOCAL_MAX_DIST_SQ = 1.0;

    /**
     * 每跳的候选边（至少一条）。
     */
    private final List<GeoLink[]> hops;
    /**
     * 第 i 个节点到起点的累计距离（size = hops.size() + 1）。
     */
    private final double[] cum;
    /**
     * 轨迹所在世界名（取首节点 id 解析），列车不在此世界时不投影。
     */
    @Getter
    private final String world;

    private GeoRouteTrack(List<GeoLink[]> hops, String world) {
        this.hops = hops;
        this.world = world;
        this.cum = new double[hops.size() + 1];
        for (int i = 0; i < hops.size(); i++) {
            cum[i + 1] = cum[i] + hopLength(hops.get(i)[0]);
        }
    }

    /**
     * 按有序节点 id 序列构建轨迹（直达车导航序列）。相邻节点间取图中所有 from→to 边作候选，
     * 按长度升序排列（首个为基准长度）。
     *
     * @param graph   路由图
     * @param nodeIds 有序节点 id（至少 2 个）
     * @return 轨迹；节点不足 / 任一相邻对之间无边时返回 null
     */
    @Nullable
    public static GeoRouteTrack ofNodePath(GeoRouteGraph graph, List<String> nodeIds) {
        if (graph == null || nodeIds == null || nodeIds.size() < 2) {
            return null;
        }
        List<GeoLink[]> hops = new ArrayList<>(nodeIds.size() - 1);
        for (int i = 0; i + 1 < nodeIds.size(); i++) {
            GeoLink[] candidates = candidates(graph, nodeIds.get(i), nodeIds.get(i + 1));
            if (candidates == null) {
                return null;
            }
            hops.add(candidates);
        }
        return new GeoRouteTrack(hops, NodeId.worldOf(nodeIds.getFirst()));
    }

    /**
     * 按线路车站序列构建整线轨迹（普通车）：每站取该线的站台节点（{@link GeoRouteGraph#getNode(String, String)}），
     * 相邻两站之间用 Dijkstra 求沿线最短路（优先本线边），拼成整条节点序列。
     *
     * @param graph    路由图
     * @param stations 车站名序列（按行驶顺序）
     * @param lineId   线路 id
     * @return 轨迹与每站在节点序列中的下标；任一站定位不到 / 相邻站间无路时返回 null
     */
    @Nullable
    public static StationTrack ofLineStations(GeoRouteGraph graph, List<String> stations, String lineId) {
        if (graph == null || stations == null || stations.size() < 2) {
            return null;
        }
        List<String> stationNodes = new ArrayList<>(stations.size());
        for (String station : stations) {
            GeoNode node = graph.getNode(station, lineId);
            if (node == null) {
                return null;
            }
            stationNodes.add(node.getId());
        }
        List<String> path = new ArrayList<>();
        int[] stationNodeIndex = new int[stations.size()];
        path.add(stationNodes.getFirst());
        for (int i = 0; i + 1 < stationNodes.size(); i++) {
            List<String> seg = shortestPath(graph, stationNodes.get(i), stationNodes.get(i + 1), lineId);
            if (seg == null) {
                return null;
            }
            // seg 首节点即上一站，已在 path 中
            path.addAll(seg.subList(1, seg.size()));
            stationNodeIndex[i + 1] = path.size() - 1;
        }
        GeoRouteTrack track = ofNodePath(graph, path);
        return track == null ? null : new StationTrack(track, stationNodeIndex);
    }

    /**
     * 普通车整线轨迹：轨迹本体 + 每站对应的节点下标。
     *
     * @param track            轨迹
     * @param stationNodeIndex 第 i 站在轨迹节点序列中的下标
     */
    public record StationTrack(GeoRouteTrack track, int[] stationNodeIndex) {
        /**
         * 第 i 站的累计距离。
         *
         * @param stationIdx 车站下标
         * @return 累计距离
         */
        public double stationDistance(int stationIdx) {
            return track.distanceAtNode(stationNodeIndex[stationIdx]);
        }
    }

    /**
     * 轨迹总长。
     *
     * @return 总距离
     */
    public double totalDistance() {
        return cum[cum.length - 1];
    }

    /**
     * 跳数（节点数 - 1）。
     *
     * @return 跳数
     */
    public int hopCount() {
        return hops.size();
    }

    /**
     * 第 i 个节点的累计距离（下标越界时夹到两端）。
     *
     * @param nodeIdx 节点下标
     * @return 累计距离
     */
    public double distanceAtNode(int nodeIdx) {
        return cum[Math.max(0, Math.min(nodeIdx, cum.length - 1))];
    }

    /**
     * 投影游标：记住上次命中的跳 / 候选边 / 线段，下次优先在其附近搜索（每列车一个，非线程安全）。
     */
    public static final class Cursor {
        private int hop = -1;
        private int candidate = -1;
        private int segment = -1;
    }

    /**
     * 把点投影到 {@code [fromHop, toHop]} 范围内的轨迹上，返回沿线累计距离。
     * <p>
     * 先只在游标所指跳 / 边附近局部搜索，离折线 {@link #LOCAL_MAX_DIST_SQ} 内直接采用；
     * 否则扫描范围内全部跳的全部候选边，取最近者。范围内没有任何折线几何时返回 {@link Double#NaN}。
     *
     * @param fromHop 搜索起始跳（含）
     * @param toHop   搜索结束跳（含）
     * @param x       点 x
     * @param y       点 y
     * @param z       点 z
     * @param cursor  投影游标（会被更新）
     * @return 沿线累计距离；无几何返回 NaN
     */
    public double locate(int fromHop, int toHop, double x, double y, double z, Cursor cursor) {
        fromHop = Math.max(0, fromHop);
        toHop = Math.min(hops.size() - 1, toHop);
        if (fromHop > toHop) {
            return Double.NaN;
        }
        // 游标命中范围内：先局部投影
        if (cursor.hop >= fromHop && cursor.hop <= toHop && cursor.candidate >= 0
                && cursor.candidate < hops.get(cursor.hop).length) {
            GeoPolyline line = hops.get(cursor.hop)[cursor.candidate].getPolyline();
            if (line != null) {
                GeoPolyline.Projection p = line.project(x, y, z, cursor.segment);
                if (p.distSq() <= LOCAL_MAX_DIST_SQ) {
                    cursor.segment = p.segment();
                    return along(cursor.hop, p.fraction());
                }
            }
        }
        double bestDistSq = Double.MAX_VALUE;
        double bestAlong = Double.NaN;
        for (int h = fromHop; h <= toHop; h++) {
            GeoLink[] candidates = hops.get(h);
            for (int c = 0; c < candidates.length; c++) {
                GeoPolyline line = candidates[c].getPolyline();
                if (line == null) {
                    continue;
                }
                int hint = (h == cursor.hop && c == cursor.candidate) ? cursor.segment : -1;
                GeoPolyline.Projection p = line.project(x, y, z, hint);
                if (p.distSq() < bestDistSq) {
                    bestDistSq = p.distSq();
                    bestAlong = along(h, p.fraction());
                    cursor.hop = h;
                    cursor.candidate = c;
                    cursor.segment = p.segment();
                }
            }
        }
        return bestAlong;
    }

    private double along(int hop, double fraction) {
        return cum[hop] + (cum[hop + 1] - cum[hop]) * Math.max(0, Math.min(1, fraction));
    }

    private static double hopLength(GeoLink link) {
        if (link.getDistance() > 0) {
            return link.getDistance();
        }
        return link.getPolyline() == null ? 0 : link.getPolyline().getLength();
    }

    @Nullable
    private static GeoLink[] candidates(GeoRouteGraph graph, String from, String to) {
        List<GeoLink> result = new ArrayList<>(1);
        for (GeoLink link : graph.links(from)) {
            if (to.equals(link.getToNodeId())) {
                result.add(link);
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        result.sort((a, b) -> Double.compare(hopLength(a), hopLength(b)));
        return result.toArray(new GeoLink[0]);
    }

    /**
     * 节点到节点的 Dijkstra 最短路（非本线边加罚），返回含首尾的节点 id 序列；无路返回 null。
     */
    @Nullable
    private static List<String> shortestPath(GeoRouteGraph graph, String start, String end, String lineId) {
        if (start.equals(end)) {
            return null;
        }
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> done = new HashSet<>();
        PriorityQueue<Object[]> queue = new PriorityQueue<>((a, b) -> Double.compare((double) a[1], (double) b[1]));
        dist.put(start, 0.0);
        queue.add(new Object[]{start, 0.0});
        int pops = 0;
        while (!queue.isEmpty() && pops++ < MAX_POPS) {
            Object[] top = queue.poll();
            String node = (String) top[0];
            if (!done.add(node)) {
                continue;
            }
            if (node.equals(end)) {
                List<String> path = new ArrayList<>();
                for (String cur = end; cur != null; cur = prev.get(cur)) {
                    path.add(cur);
                }
                Collections.reverse(path);
                return path;
            }
            double d = (double) top[1];
            for (GeoLink link : graph.links(node)) {
                String to = link.getToNodeId();
                if (done.contains(to)) {
                    continue;
                }
                double w = Math.max(hopLength(link), 1e-6) * (lineId.equals(link.getLineId()) ? 1.0 : FOREIGN_LINE_PENALTY);
                double nd = d + w;
                Double old = dist.get(to);
                if (old == null || nd < old) {
                    dist.put(to, nd);
                    prev.put(to, node);
                    queue.add(new Object[]{to, nd});
                }
            }
        }
        return null;
    }
}
