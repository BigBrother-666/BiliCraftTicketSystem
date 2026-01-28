package com.bigbrother.bilicraftticketsystem;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.pricePerKm;

public class TrainRoutes {
    public static MermaidGraph graph;

    @Getter
    public static class PathInfo implements Comparable<PathInfo> {
        private final List<MermaidGraph.Node> path;
        private final Set<String> tags;
        private final double distance;
        private final double price;
        private final MermaidGraph.Node startStation;
        private final MermaidGraph.Node endStation;
        private final List<String> stationSequence;


        public PathInfo(List<MermaidGraph.Node> path, List<MermaidGraph.Edge> edges) {
            if (path.size() < 2) {
                throw new IllegalArgumentException("Path must have at least two nodes");
            }

            this.path = path;
            this.startStation = path.get(0);
            this.endStation = path.get(path.size() - 1);
            this.distance = edges.stream().mapToDouble(MermaidGraph.Edge::getDistance).sum();
            this.price = calculateFare(distance);
            this.tags = path.stream().map(MermaidGraph.Node::getTag).collect(Collectors.toCollection(LinkedHashSet::new));
            this.stationSequence = stationSequence();
        }

        @Override
        public int compareTo(@NotNull TrainRoutes.PathInfo other) {
            if (Math.abs(this.distance - other.distance) < 1e-9) {
                return Integer.compare(this.tags.size(), other.tags.size());
            } else {
                return Double.compare(this.distance, other.distance);
            }
        }

        @Override
        public int hashCode() {
            return stationSequence.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PathInfo other)) return false;

            return this.stationSequence.equals(other.stationSequence);
        }

        private List<String> stationSequence() {
            List<String> list = new ArrayList<>();
            for (MermaidGraph.Node n : path) {
                if (n.isStation()) {
                    list.add(n.getStationName());
                }
            }
            return list;
        }

        public String getStartPlatformTag() {
            return startStation.getTag() + "-" + startStation.getRailwayDirection();
        }

        public String getStartPlatform() {
            return startStation.getStationName() + "-" + startStation.getRailwayDirection();
        }

        // 计算票价
        private double calculateFare(double distance) {
            return Math.round(distance * pricePerKm * 100.0) / 100.0;
        }
    }

    // 从文件中读取图
    public static void readGraphFromFile(String filename) throws IOException {
        graph = new MermaidGraph();
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\\|");
            if (parts.length == 3) {
                String source = parts[0].replace("-->", "")
                        .replace("==>", "")
                        .replace("-.->", "").trim();
                double distance = Double.parseDouble(parts[1].trim());
                String target = parts[2].replace(";", "").trim();
                graph.addEdge(source, target, distance);
            }
        }
        br.close();
        graph.findStartNodes();
    }

    public static @Nullable PathInfo getShortestPathFromStartNode(MermaidGraph.Node startNode, String endStation) {
        List<PathInfo> pathInfoList = new ArrayList<>();
        if (startNode != null) {
            graph.findAllPaths(endStation, new ArrayList<>(List.of(startNode)), new ArrayList<>(), new HashSet<>(), pathInfoList);
        }
        return pathInfoList.stream().min(PathInfo::compareTo).orElse(null);
    }

    public static List<PathInfo> getPathInfoList(String startStation, String endStation) {
        if (startStation == null || endStation == null || startStation.isEmpty() || endStation.isEmpty()) {
            return new ArrayList<>();
        }

        List<PathInfo> pathInfoList = new ArrayList<>();
        List<MermaidGraph.Node> startNodes = graph.getSpawnableNodes(startStation);

        for (MermaidGraph.Node s : startNodes) {
            graph.findAllPaths(endStation, new ArrayList<>(List.of(s)), new ArrayList<>(), new HashSet<>(), pathInfoList);
        }

        // 按照价格排序
        Collections.sort(pathInfoList);

        // 去重
        pathInfoList = pathInfoList.stream().distinct().collect(Collectors.toList());

        return pathInfoList;
    }

    /**
     * 获取某个特定路线的PathInfo
     *
     * @param startPlatformTag 起始站台tag
     * @param tags             路线包含的tag
     * @return 路线详情
     */
    @Nullable
    public static TrainRoutes.PathInfo getPathInfo(String startPlatformTag, List<String> tags, String endStation) {
        MermaidGraph.Node currNode = graph.getNodeFromPtag(startPlatformTag);
        if (currNode == null || tags.isEmpty() || !currNode.getTag().equals(tags.get(0))) {
            return null;
        }

        List<MermaidGraph.Node> nodePath = new ArrayList<>();
        List<MermaidGraph.Edge> edgePath = new ArrayList<>();
        nodePath.add(currNode);
        tags.remove(0);
        for (String tag : tags) {
            boolean found = false;
            for (MermaidGraph.Edge edge : graph.getEdges(currNode)) {
                if (edge.getTarget().getTag().equals(tag)) {
                    found = true;
                    nodePath.add(edge.getTarget());
                    edgePath.add(edge);
                    currNode = edge.getTarget();
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        // 添加做后一个边和节点
        for (MermaidGraph.Edge edge : graph.getEdges(currNode)) {
            if (edge.getTarget().getStationName().equals(endStation)) {
                nodePath.add(edge.getTarget());
                edgePath.add(edge);
                break;
            }
        }
        return new TrainRoutes.PathInfo(nodePath, edgePath);
    }

    @Nullable
    public static TrainRoutes.PathInfo findShortestPath(
            String startStation,
            String endStation
    ) {
        List<MermaidGraph.Node> spawnableStartNodes = graph.getSpawnableNodes(startStation);
        if (spawnableStartNodes.isEmpty()) {
            return null;
        }

        // 最短距离
        Map<MermaidGraph.Node, Double> dist = new HashMap<>();
        // 前驱节点
        Map<MermaidGraph.Node, MermaidGraph.Node> prevNode = new HashMap<>();
        // 到达该节点所使用的边
        Map<MermaidGraph.Node, MermaidGraph.Edge> prevEdge = new HashMap<>();

        PriorityQueue<MermaidGraph.Node> pq = new PriorityQueue<>(
                Comparator.comparingDouble(n ->
                        dist.getOrDefault(n, Double.POSITIVE_INFINITY))
        );

        // 多起点初始化
        for (MermaidGraph.Node start : spawnableStartNodes) {
            dist.put(start, 0.0);
            pq.add(start);
        }

        while (!pq.isEmpty()) {
            MermaidGraph.Node current = pq.poll();
            double currentDist = dist.get(current);

            // 到达终点，直接结束
            if (current.getStationName().equals(endStation) && prevNode.containsKey(current)) {
                return buildPath(current, prevNode, prevEdge);
            }

            for (MermaidGraph.Edge edge : graph.getEdges(current)) {
                MermaidGraph.Node next = edge.getTarget();
                double newDist = currentDist + edge.getDistance();
                double oldDist = dist.getOrDefault(next, Double.POSITIVE_INFINITY);

                if (newDist < oldDist) {
                    dist.put(next, newDist);
                    prevNode.put(next, current);
                    prevEdge.put(next, edge);
                    pq.add(next);
                }
            }
        }

        return null;
    }

    private static TrainRoutes.PathInfo buildPath(
            MermaidGraph.Node end,
            Map<MermaidGraph.Node, MermaidGraph.Node> prevNode,
            Map<MermaidGraph.Node, MermaidGraph.Edge> prevEdge
    ) {
        List<MermaidGraph.Node> nodes = new ArrayList<>();
        List<MermaidGraph.Edge> edges = new ArrayList<>();

        MermaidGraph.Node cur = end;

        while (prevNode.containsKey(cur)) {
            nodes.add(cur);
            edges.add(prevEdge.get(cur));
            cur = prevNode.get(cur);
        }

        nodes.add(cur); // 起点

        Collections.reverse(nodes);
        Collections.reverse(edges);

        return new TrainRoutes.PathInfo(nodes, edges);
    }
}
