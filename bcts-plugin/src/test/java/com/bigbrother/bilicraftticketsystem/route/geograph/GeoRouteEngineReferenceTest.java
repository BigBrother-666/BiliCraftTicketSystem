package com.bigbrother.bilicraftticketsystem.route.geograph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保留优化前的朴素 K-最短路（按已走距离出队、字符串回溯判重）作为参考实现，在随机图上与 A* + 整数索引版本
 * {@link GeoRouteEngine.Searcher#kShortest} 逐条对比，防止剪枝 / 预计算改坏语义。与后端
 * internal/routing/pathfind_ref_test.go 同一套随机图生成规则。
 */
public class GeoRouteEngineReferenceTest {

    private record RefEntry(String nodeId, double dist, GeoLink link, RefEntry prev) {
    }

    private record RefPath(List<String> ids, double dist) {
    }

    private static String refName(GeoRouteGraph g, String nodeId, GeoLink outgoing) {
        GeoNode node = g.getNode(nodeId);
        if (node == null) {
            return null;
        }
        if (node.isStation()) {
            return node.getName();
        }
        if (outgoing == null || outgoing.isSiding()) {
            return null;
        }
        return g.platformNameOfMainlineSwitch(node, outgoing.getLineId());
    }

    private static boolean refStationIn(GeoRouteGraph g, RefEntry e, String name) {
        GeoLink outgoing = null;
        for (RefEntry cur = e; cur != null; cur = cur.prev()) {
            if (name.equals(refName(g, cur.nodeId(), outgoing))) {
                return true;
            }
            outgoing = cur.link();
        }
        return false;
    }

    private static boolean refRepeats(GeoRouteGraph g, RefEntry cur, String name, String startStation,
                                      String endStation, boolean nextIsTerminal) {
        if (name == null || !refStationIn(g, cur, name)) {
            return false;
        }
        return !nextIsTerminal || !name.equals(startStation) || !name.equals(endStation);
    }

    private static boolean refInPath(RefEntry e, String id) {
        for (RefEntry cur = e; cur != null; cur = cur.prev()) {
            if (cur.nodeId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 优化前的朴素实现（不含折返站：LineConfig 在单测里为空，isReverseStation 恒为 false）。
     */
    private static List<RefPath> refKShortest(GeoRouteGraph g, String startId, String endStation, int k) {
        List<RefPath> results = new ArrayList<>();
        GeoNode startNode = g.getNode(startId);
        if (startNode == null || k < 1) {
            return results;
        }
        String startStation = startNode.isStation() ? startNode.getName() : null;
        // 线性扫描优先队列（取最小 dist，相同取最早入队），图很小无所谓性能
        List<RefEntry> queue = new ArrayList<>();
        queue.add(new RefEntry(startId, 0, null, null));
        int pops = 0;
        while (!queue.isEmpty() && results.size() < k && pops < 20000) {
            int mi = 0;
            for (int i = 1; i < queue.size(); i++) {
                if (queue.get(i).dist() < queue.get(mi).dist()) {
                    mi = i;
                }
            }
            RefEntry cur = queue.remove(mi);
            pops++;
            GeoNode curNode = g.getNode(cur.nodeId());
            if (curNode != null && curNode.isStation() && endStation.equals(curNode.getName()) && cur.link() != null) {
                List<String> ids = new ArrayList<>();
                for (RefEntry e = cur; e != null; e = e.prev()) {
                    ids.add(e.nodeId());
                }
                Collections.reverse(ids);
                results.add(new RefPath(ids, cur.dist()));
                continue;
            }
            for (GeoLink link : g.links(cur.nodeId())) {
                GeoNode nextNode = g.getNode(link.getToNodeId());
                if (nextNode == null || !refEnterFaceAllows(cur.link(), link)) {
                    continue;
                }
                String curName = curNode != null && !curNode.isStation() ? refName(g, cur.nodeId(), link) : null;
                boolean nextIsTerminal = nextNode.isStation() && endStation.equals(nextNode.getName());
                if (refRepeats(g, cur, curName, startStation, endStation, nextIsTerminal)) {
                    continue;
                }
                String nextName = nextNode.isStation() ? nextNode.getName() : null;
                if (nextName != null && !nextName.equals(curName)
                        && refRepeats(g, cur, nextName, startStation, endStation, nextIsTerminal)) {
                    continue;
                }
                if (refInPath(cur, link.getToNodeId()) && !(link.getToNodeId().equals(startId) && nextIsTerminal)) {
                    continue;
                }
                if (nextNode.isStation() && !endStation.equals(nextNode.getName()) && link.isSiding()) {
                    continue;
                }
                queue.add(new RefEntry(link.getToNodeId(), cur.dist() + link.getDistance(), link, cur));
            }
        }
        return results;
    }

    private static boolean refEnterFaceAllows(GeoLink inLink, GeoLink outLink) {
        if (inLink == null || outLink.getEnterFacesFrom().isEmpty() || inLink.getEnterFaceTo() == null) {
            return true;
        }
        return outLink.getEnterFacesFrom().contains(inLink.getEnterFaceTo());
    }

    /**
     * 生成带道岔、正线 / 停靠线、入向面门控、同名多站台、幽灵终点的随机小图。边长取整数，制造大量等长路线。
     */
    private static GeoRouteGraph randomGraph(Random r, List<String> names) {
        GeoRouteGraph g = new GeoRouteGraph();
        List<String> nodeIds = new ArrayList<>();
        int nStations = 4 + r.nextInt(5);
        for (int i = 0; i < nStations; i++) {
            String id = "s" + i;
            g.addNode(new GeoNode(id, GeoNode.TYPE_STATION, names.get(r.nextInt(names.size())), i, 64, 0));
            nodeIds.add(id);
        }
        int nSwitches = 3 + r.nextInt(6);
        for (int i = 0; i < nSwitches; i++) {
            String id = "w" + i;
            g.addNode(new GeoNode(id, GeoNode.TYPE_SWITCH, null, i, 64, 1));
            nodeIds.add(id);
        }
        String[] faces = {"0_1", "1_0", "2_3"};
        String[] lines = {"L1", "L2"};
        String[] dirs = {"e", "w", "n", "s"};
        int nEdges = nodeIds.size() + r.nextInt(nodeIds.size() * 2);
        for (int i = 0; i < nEdges; i++) {
            String from = nodeIds.get(r.nextInt(nodeIds.size()));
            String to = r.nextInt(12) == 0 ? "ghost" : nodeIds.get(r.nextInt(nodeIds.size()));
            if (from.equals(to)) {
                continue;
            }
            String track = switch (r.nextInt(4)) {
                case 0 -> "siding";
                case 1 -> "main";
                default -> null;
            };
            String enterTo = r.nextInt(3) == 0 ? faces[r.nextInt(faces.length)] : null;
            Set<String> enterFrom = r.nextInt(3) == 0 ? Set.of(faces[r.nextInt(faces.length)]) : Set.of();
            g.addLink(new GeoLink("e" + i, from, to, lines[r.nextInt(lines.length)], 1 + r.nextInt(4), "#fff",
                    dirs[r.nextInt(dirs.length)], enterFrom, enterTo, track));
        }
        return g;
    }

    @Test
    void kShortestMatchesNaiveReferenceOnRandomGraphs() {
        Random r = new Random(42);
        List<String> names = List.of("A", "B", "C", "D", "E", "F");
        GeoRouteGraph saved = GeoRouteEngine.getGraph();
        try {
            for (int iter = 0; iter < 400; iter++) {
                GeoRouteGraph g = randomGraph(r, names);
                GeoRouteEngine.setGraph(g);
                for (String startName : names) {
                    for (GeoNode start : g.stationNodes(startName)) {
                        for (String end : names) {
                            for (int k : new int[]{1, 3, 16}) {
                                List<RefPath> want = refKShortest(g, start.getId(), end, k);
                                List<GeoRoutePath> got = new GeoRouteEngine.Searcher(0L).kShortest(start.getId(), end, k);
                                assertSamePaths(want, got, "iter " + iter + " " + start.getId() + "->" + end + " k=" + k);
                            }
                        }
                    }
                }
            }
        } finally {
            GeoRouteEngine.setGraph(saved);
        }
    }

    /**
     * 距离序列必须逐条相同；同距离的路线之间允许顺序不同，非最后一个距离档的节点序列多重集必须一致
     * （平行边会产生节点序列相同的不同路线，故按多重集计数）。最后一个距离档可能被 k 截断，其中具体取到
     * 哪几条依赖等长路线的出队顺序，只校验条数。
     */
    private static void assertSamePaths(List<RefPath> want, List<GeoRoutePath> got, String msg) {
        assertEquals(want.size(), got.size(), msg + " size");
        for (int i = 0; i < want.size(); i++) {
            assertEquals(want.get(i).dist(), got.get(i).getDistance() * 1000, 1e-6, msg + " dist[" + i + "]");
        }
        if (want.isEmpty()) {
            return;
        }
        double last = want.getLast().dist();
        Map<List<String>, Integer> count = new HashMap<>();
        for (RefPath p : want) {
            if (p.dist() != last) {
                count.merge(p.ids(), 1, Integer::sum);
            }
        }
        for (GeoRoutePath p : got) {
            if (p.getDistance() * 1000 < last - 1e-6) {
                List<String> ids = new ArrayList<>();
                for (GeoNode node : p.getNodes()) {
                    ids.add(node.getId());
                }
                count.merge(ids, -1, Integer::sum);
            }
        }
        for (int c : count.values()) {
            assertTrue(c == 0, msg + " path multiset differs");
        }
    }
}
