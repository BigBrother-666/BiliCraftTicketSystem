package com.bigbrother.bilicraftticketsystem;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TrainRoutes {
    public static MermaidGraph graph;

    @Data
    public static class PathInfo implements Comparable<PathInfo> {
        public PathInfo(List<MermaidGraph.Node> path, Set<String> tags, double distance, double price, MermaidGraph.Node startStation, MermaidGraph.Node endStation) {
            this.path = path;
            this.tags = tags;
            this.distance = distance;
            this.price = price;
            this.endStation = endStation;
            this.startStation = startStation;
        }

        private List<MermaidGraph.Node> path;
        private Set<String> tags;
        private double distance;
        private double price;
        private MermaidGraph.Node startStation;
        private MermaidGraph.Node endStation;

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
            int result = 1;
            for (var node : path) {
                result = 31 * result + (node.getStationName() != null ? node.getStationName().hashCode() : 0);
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PathInfo pathInfo = (PathInfo) obj;
            if (path.size() == pathInfo.path.size()) {
                for (int i = 0; i < pathInfo.path.size(); i++) {
                    if (!path.get(i).getStationName().equals(pathInfo.path.get(i).getStationName())) {
                        return false;
                    }
                }
            } else {
                return false;
            }
            return true;
        }

        public String getStartPlatformTag() {
            return startStation.getTag() + "-" + startStation.getRailwayDirection();
        }

        public String getStartPlatform() {
            return startStation.getStationName() + "-" + startStation.getRailwayDirection();
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
    }

    public static List<PathInfo> getPathInfoList(String startStation, String endStation) {
        List<PathInfo> pathInfoList = new ArrayList<>();
        Set<MermaidGraph.Node> start = new HashSet<>();
        Set<MermaidGraph.Node> end = new HashSet<>();
        for (MermaidGraph.Node node : graph.adjacencyList.keySet()) {
            if (node.getStationName().equals(startStation)) {
                start.add(node);
            }
            if (node.getStationName().equals(endStation)) {
                end.add(node);
            }
            for (MermaidGraph.Edge edge : graph.adjacencyList.get(node)) {
                MermaidGraph.Node target = edge.getTarget();
                if (target.getStationName().equals(startStation)) {
                    start.add(target);
                }
                if (target.getStationName().equals(endStation)) {
                    end.add(target);
                }
            }
        }

        for (MermaidGraph.Node s : start) {
            List<MermaidGraph.Node> path = new ArrayList<>();
            Set<MermaidGraph.Node> visited = new HashSet<>();
            graph.findAllPaths(s, end, path, visited, pathInfoList);
        }

        // 按照价格排序
        Collections.sort(pathInfoList);

        // 去重
        pathInfoList = pathInfoList.stream().distinct().collect(Collectors.toList());

        return pathInfoList;
    }
}
