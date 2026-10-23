package com.bigbrother.bilicraftticketsystem.route.geograph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 寻路热路径用的整数化只读图索引（{@link GeoRouteEngine} 内部使用）。
 * <p>
 * 节点 / 车站名映射为下标，出边 / 入边为边下标数组，并预计算每条边的「起点代表的车站名」
 * （即 {@code getNodeStationName(from, link)}），让 K-最短路的扩展与路径回溯只做整数比较，
 * 不再每步拼字符串查 map。由 {@link GeoRouteGraph#searchIndex()} 在图构建完成后惰性构建一次，
 * 之后只读，可被多个查询线程并发使用。
 */
final class GeoSearchIndex {
    /**
     * 节点 id → 下标。
     */
    final Map<String, Integer> nodeIdx = new HashMap<>();
    /**
     * 下标 → 节点；只作为边终点出现、图中不存在的「幽灵」节点为 null。
     */
    final GeoNode[] nodes;
    final boolean[] isStation;
    /**
     * station 节点的站名下标；非 station 或无名为 -1。
     */
    final int[] nodeName;
    /**
     * 车站名 → 下标。
     */
    final Map<String, Integer> nameIdx = new HashMap<>();
    /**
     * 节点 → 出边下标（保持 {@link GeoRouteGraph#links} 原顺序）。
     */
    final int[][] out;
    /**
     * 节点 → 入边下标。
     */
    final int[][] in;
    final GeoLink[] links;
    final int[] linkFrom;
    final int[] linkTo;
    /**
     * {@code crossName[l]} 是边 l 的起点代表的车站名下标：起点是车站时为其站名；起点是进站道岔且本边走正线时
     * 为被跨越的车站名；否则 -1。
     */
    final int[] crossName;

    GeoSearchIndex(GeoRouteGraph g) {
        List<GeoNode> nodeList = new ArrayList<>();
        for (GeoNode node : g.allNodes()) {
            nodeIdx.put(node.getId(), nodeList.size());
            nodeList.add(node);
        }
        // 边终点可能指向图中不存在的节点（幽灵节点），同样编号，但 nodes[i] 为 null
        List<GeoLink> linkList = new ArrayList<>();
        List<Integer> fromList = new ArrayList<>();
        List<Integer> toList = new ArrayList<>();
        int existing = nodeList.size();
        for (int i = 0; i < existing; i++) {
            for (GeoLink link : g.links(nodeList.get(i).getId())) {
                Integer to = nodeIdx.get(link.getToNodeId());
                if (to == null) {
                    to = nodeList.size();
                    nodeIdx.put(link.getToNodeId(), to);
                    nodeList.add(null);
                }
                linkList.add(link);
                fromList.add(i);
                toList.add(to);
            }
        }

        int n = nodeList.size();
        nodes = nodeList.toArray(new GeoNode[0]);
        isStation = new boolean[n];
        nodeName = new int[n];
        for (int i = 0; i < n; i++) {
            GeoNode node = nodes[i];
            isStation[i] = node != null && node.isStation();
            nodeName[i] = isStation[i] ? nameId(node.getName()) : -1;
        }

        int m = linkList.size();
        links = linkList.toArray(new GeoLink[0]);
        linkFrom = new int[m];
        linkTo = new int[m];
        crossName = new int[m];
        int[] outCount = new int[n];
        int[] inCount = new int[n];
        for (int l = 0; l < m; l++) {
            int from = fromList.get(l);
            int to = toList.get(l);
            linkFrom[l] = from;
            linkTo[l] = to;
            outCount[from]++;
            inCount[to]++;
            if (isStation[from]) {
                crossName[l] = nodeName[from];
            } else if (!links[l].isSiding()) {
                crossName[l] = nameId(g.platformNameOfMainlineSwitch(nodes[from], links[l].getLineId()));
            } else {
                crossName[l] = -1;
            }
        }
        out = new int[n][];
        in = new int[n][];
        for (int i = 0; i < n; i++) {
            out[i] = new int[outCount[i]];
            in[i] = new int[inCount[i]];
            outCount[i] = 0;
            inCount[i] = 0;
        }
        for (int l = 0; l < m; l++) {
            out[linkFrom[l]][outCount[linkFrom[l]]++] = l;
            in[linkTo[l]][inCount[linkTo[l]]++] = l;
        }
    }

    private int nameId(String name) {
        if (name == null) {
            return -1;
        }
        Integer id = nameIdx.get(name);
        if (id == null) {
            id = nameIdx.size();
            nameIdx.put(name, id);
        }
        return id;
    }

    /**
     * 车站名下标；图中没有该站名返回 -1。
     */
    int nameIdOf(String name) {
        Integer id = name == null ? null : nameIdx.get(name);
        return id == null ? -1 : id;
    }
}
