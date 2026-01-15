package com.bigbrother.bilicraftticketsystem;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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

    public static List<PathInfo> getPathInfoList(String startStation, String endStation) {
        if (startStation == null || endStation == null || startStation.isEmpty() || endStation.isEmpty()) {
            return new ArrayList<>();
        }

        List<PathInfo> pathInfoList = new ArrayList<>();
        Set<MermaidGraph.Node> start = new HashSet<>();

        for (MermaidGraph.Node node : graph.getAllNodes()) {
            if (node.getStationName().equals(startStation) && !node.isEndStation()) {
                start.add(node);
            }
        }

        for (MermaidGraph.Node s : start) {
            graph.findAllPaths(endStation, new ArrayList<>(List.of(s)), new ArrayList<>(), new HashSet<>(), pathInfoList);
        }

        // 按照价格排序
        Collections.sort(pathInfoList);

        // 去重
        pathInfoList = pathInfoList.stream().distinct().collect(Collectors.toList());

        return pathInfoList;
    }
}
