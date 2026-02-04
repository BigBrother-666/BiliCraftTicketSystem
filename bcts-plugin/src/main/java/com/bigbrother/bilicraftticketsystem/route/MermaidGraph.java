package com.bigbrother.bilicraftticketsystem.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MermaidGraph {
    public static final String emptyField = "/";

    // 根据tag找节点详情
    public final Map<String, List<Node>> nodeTagMap;

    // 邻接表
    public final Map<Node, List<Edge>> adjacencyList;

    // 入度为0的节点
    public Set<Node> startNodes;

    public MermaidGraph() {
        nodeTagMap = new HashMap<>();
        adjacencyList = new HashMap<>();
    }

    // 边
    @Data
    @AllArgsConstructor
    public static class Edge {
        private Node target;
        private double distance;
    }

    // 节点
    @Data
    @AllArgsConstructor
    public static class Node {
        private String stationName;
        private String railwayName;
        private String railwayDirection;
        private String tag;

        public boolean isStation() {
            return stationName != null && !stationName.isEmpty();
        }

        /**
         * @return 去掉“站”字的车站名
         */
        public String getClearStationName() {
            return stationName.substring(0, stationName.length() - 1);
        }

        public @Nullable String getPlatformTag() {
            return tag + "-" + railwayDirection;
        }

        /**
         * 比较两个platformTag是否相同
         *
         * @return true:相同
         */
        public static boolean cmpPlatformTag(String pTag1, String pTag2) {
            String[] sp1 = pTag1.split("-");
            String[] sp2 = pTag2.split("-");
            if (sp1.length != 2 || sp2.length != 2) {
                return false;
            }
            return sp1[0].equals(sp2[0]) && (sp1[1].startsWith(sp2[1]) || sp2[1].startsWith(sp1[1]));
        }

        /**
         * @return 是否是一条线路的终点站
         */
        public boolean isEndStation() {
            // 方向和车站相同
            return this.getRailwayDirection().startsWith(this.getClearStationName());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node other)) {
                return false;
            }
            return Objects.equals(stationName, other.stationName)
                    && Objects.equals(railwayName, other.railwayName)
                    && Objects.equals(railwayDirection, other.railwayDirection)
                    && Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stationName, railwayName, railwayDirection, tag);
        }
    }

    public List<Edge> getEdges(Node node) {
        return adjacencyList.getOrDefault(node, new ArrayList<>());
    }

    /**
     * 获取某站的可发车Node
     */
    public List<Node> getSpawnableNodes(String stationName) {
        List<Node> ret = new ArrayList<>();
        for (MermaidGraph.Node node : this.getAllNodes()) {
            if (node.getStationName().equals(stationName) && !node.isEndStation()) {
                ret.add(node);
            }
        }
        return ret;
    }

    /**
     * 根据站台tag寻找节点信息
     *
     * @param platformTag 站台tag
     * @return 节点
     */
    public @Nullable Node getNodeFromPtag(String platformTag) {
        // 注意limit设置-1，否则switcher节点的站台tag（"tag-"）split后只有一个元素
        String[] split = platformTag.split("-", -1);
        if (split.length < 2) {
            return null;
        }
        if (nodeTagMap.get(split[0]) == null) {
            return null;
        }
        for (MermaidGraph.Node node : nodeTagMap.get(split[0])) {
            if (node.getRailwayDirection().startsWith(split[1])) {
                return node;
            }
        }
        return null;
    }

    public String getStationNameFromTag(String tag) {
        List<Node> nodes = nodeTagMap.get(tag);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0).getStationName();
        }
        return "";
    }

    /**
     * 获取指定节点的所有父节点
     *
     * @param node 目标节点
     * @return 父节点列表，没有父节点则返回空列表
     */
    public Set<Node> getParentNodes(Node node) {
        Set<Node> parentNodes = new HashSet<>();
        for (Map.Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {
            Node potentialParent = entry.getKey();
            List<Edge> edges = entry.getValue();
            for (Edge edge : edges) {
                if (edge.getTarget().equals(node)) {
                    parentNodes.add(potentialParent);
                    break;
                }
            }
        }
        return parentNodes;
    }

    /**
     * 查找图中的所有开始节点（即没有入边的节点）
     * 调用后读取startNode变量
     */
    public void findStartNodes() {
        // 收集所有节点
        Set<Node> allNodes = new HashSet<>();
        Set<Node> hasIncomingEdge = new HashSet<>();

        // 遍历邻接表
        for (Map.Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {
            Node source = entry.getKey();
            allNodes.add(source);

            // 记录所有有入边的目标节点
            for (Edge edge : entry.getValue()) {
                hasIncomingEdge.add(edge.getTarget());
                allNodes.add(edge.getTarget());
            }
        }

        // 开始节点 = 所有节点 - 有入边的节点
        allNodes.removeAll(hasIncomingEdge);
        if (allNodes.isEmpty()) {
            if (hasIncomingEdge.isEmpty()) {
                startNodes = Set.of();
            } else {
                startNodes = Set.of(hasIncomingEdge.iterator().next());
            }
        } else {
            startNodes = allNodes;
        }
    }

    /**
     * 收集所有节点
     */
    public Set<Node> getAllNodes() {
        Set<Node> allNodes = new HashSet<>();

        for (Map.Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {
            Node source = entry.getKey();
            allNodes.add(source);
            for (Edge edge : entry.getValue()) {
                allNodes.add(edge.getTarget());
            }
        }
        return allNodes;
    }

    public static Node parseMermaid(String s) {
        String[] split = s.split("-");
        if (split.length >= 4) {
            for (int i = 0; i < split.length; i++) {
                split[i] = split[i].trim();
                if (split[i].equals(emptyField)) {
                    split[i] = "";
                }
            }
            return new Node(split[0], split[1], split[2], split[3]);
        } else {
            throw new IllegalArgumentException("mermaid格式错误： " + s);
        }
    }

    // 添加边
    public void addEdge(String source, String target, double distance) {
        Node src = parseMermaid(source);
        Node tgt = parseMermaid(target);

        // 构建tag和node映射
        nodeTagMap.putIfAbsent(src.getTag(), new ArrayList<>());
        nodeTagMap.get(src.getTag()).add(src);
        nodeTagMap.putIfAbsent(tgt.getTag(), new ArrayList<>());
        nodeTagMap.get(tgt.getTag()).add(tgt);

        adjacencyList.putIfAbsent(src, new ArrayList<>());
        adjacencyList.get(src).add(new Edge(tgt, distance));
    }

    // DFS 获取所有路径
    public void findAllPaths(
            String endStationName,
            List<Node> nodePath,
            List<Edge> edgePath,
            Set<String> visitedStations,
            List<TrainRoutes.PathInfo> pathInfoList
    ) {
        Node currentNode = nodePath.get(nodePath.size() - 1);
        if (currentNode.stationName.equals(endStationName) && nodePath.size() > 1) {
            // 到达终点
            pathInfoList.add(new TrainRoutes.PathInfo(new ArrayList<>(nodePath), new ArrayList<>(edgePath)));
            return;
        }

        for (Edge edge : this.getEdges(currentNode)) {
            Node next = edge.target;

            // 不允许重复经过车站  起点终点相同除外
            if (visitedStations.contains(next.stationName) && !next.stationName.equals(endStationName)) {
                continue;
            }

            if (next.isStation()) {
                visitedStations.add(next.stationName);
            }
            nodePath.add(next);
            edgePath.add(edge);

            findAllPaths(endStationName, nodePath, edgePath, visitedStations, pathInfoList);

            // 回溯
            visitedStations.remove(next.stationName);
            nodePath.remove(nodePath.size() - 1);
            edgePath.remove(edgePath.size() - 1);
        }
    }
}

