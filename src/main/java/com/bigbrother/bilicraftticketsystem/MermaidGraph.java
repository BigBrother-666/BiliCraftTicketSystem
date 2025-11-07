package com.bigbrother.bilicraftticketsystem;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.pricePerKm;

public class MermaidGraph {
    // 根据tag找节点详情
    public final Map<String, List<Node>> nodeTagMap;

    // 邻接表
    public final Map<Node, List<Edge>> adjacencyList;

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
         * @return 是否是未开通的车站节点
         */
        public boolean isUnusedStationNode() {
            return !this.isStation() && TrainRoutes.graph.nodeTagMap.get(tag).size() == 2;
        }

        /**
         * @return 去掉“站”字的车站名
         */
        public String getClearStationName() {
            return stationName.substring(0, stationName.length() - 1);
        }

        public @Nullable String getPlatformTag() {
            if (this.isStation()) {
                return tag + "-" + railwayDirection;
            }
            return null;
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

    /**
     * 根据站台tag寻找节点信息
     *
     * @param platformTag 站台tag
     * @return 节点
     */
    public @Nullable Node getBCSpawnNode(String platformTag) {
        String[] split = platformTag.split("-");
        if (split.length < 2) {
            return null;
        }
        if (nodeTagMap.get(split[0]) == null) {
            return null;
        }
        for (MermaidGraph.Node node : nodeTagMap.get(split[0])) {
            if (node.isStation() && node.getRailwayDirection().startsWith(split[1])) {
                return node;
            }
        }
        return null;
    }

    /**
     * 查找图中的所有开始节点（即没有入边的节点）
     *
     * @return 开始节点集合
     */
    public Set<Node> findStartNodes() {
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
        return allNodes;
    }

    public static Node parseMermaid(String s) {
        String[] split = s.split("-");
        if (split.length >= 4) {
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

    // 获取所有路径
    public void findAllPaths(Node start, Set<Node> end, List<Node> path, Set<Node> visited, List<TrainRoutes.PathInfo> pathInfoList) {
        if (!path.isEmpty()) {
            visited.add(start);
        }
        path.add(start);
        if (end.contains(start) && path.size() > 1) {
            List<Node> outPath = new ArrayList<>();
            Set<String> tags = new LinkedHashSet<>();
            boolean repeat = false;
            for (int i = 0; i < path.size(); i++) {
                Node currNode = path.get(i);
                // 添加tag
                if (i != path.size() - 1) {
                    if (!tags.add(currNode.getTag())) {
                        repeat = true;
                        break;
                    }
                }
                // 车站名为空（只包含tag的节点 ---tag）
                if (!currNode.isStation()) {
                    continue;
                }
                // 添加path（只添加包含车站名的node，且不包含相同车站名）
                if (outPath.stream().noneMatch(arr -> arr.getStationName().equals(currNode.getStationName())) || i == path.size() - 1) {
                    outPath.add(currNode);
                } else if (i != path.size() - 1 && !outPath.get(outPath.size() - 1).getStationName().equals(currNode.getStationName())) {
                    // 路径已存在当前车站名   当前节点不是最后一个节点，且路径最后一个节点的车站名和当前车站名不同
                    // 跳过直达车在一个车站需要多个tag的情况
                    repeat = true;
                    break;
                }
            }
            double distance = calculateTotalDistance(path);
            if (distance > 0 && !repeat) {
                pathInfoList.add(new TrainRoutes.PathInfo(
                        outPath,
                        tags,
                        distance,
                        calculateFare(distance),
                        path.get(0),
                        path.get(path.size() - 1)
                ));
            }
        } else {
            List<Edge> edges = adjacencyList.get(start);
            if (edges != null) {
                for (Edge edge : edges) {
                    if (!visited.contains(edge.target)) {
                        findAllPaths(edge.target, end, path, visited, pathInfoList);
                    }
                }
            }
        }

        // 回溯
        visited.remove(start);
        path.remove(path.size() - 1);
    }

    // 计算路径的总距离
    private double calculateTotalDistance(List<Node> path) {
        double totalDistance = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node from = path.get(i);
            Node to = path.get(i + 1);
            List<Edge> edges = adjacencyList.get(from);
            if (edges != null) {
                for (Edge edge : edges) {
                    if (edge.target.equals(to)) {
                        totalDistance += edge.distance;
                        break;
                    }
                }
            }
        }
        return totalDistance;
    }

    // 计算票价
    public double calculateFare(double distance) {
        return Math.round(distance * pricePerKm * 100.0) / 100.0;
    }
}

