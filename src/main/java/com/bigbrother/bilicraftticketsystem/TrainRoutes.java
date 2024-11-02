package com.bigbrother.bilicraftticketsystem;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.pricePerKm;

public class TrainRoutes {
    private static Graph graph;

    @Data
    public static class PathInfo implements Comparable<PathInfo> {
        public PathInfo(List<String> path, Set<String> tags, double distance, double price, String startPlatform) {
            this.path = path;
            this.tags = tags;
            this.distance = distance;
            this.price = price;
            this.start = path.get(0);
            this.end = path.get(path.size() - 1);
            this.startPlatform = startPlatform;
        }

        private List<String> path;
        private Set<String> tags;
        private double distance;
        private double price;
        private String start;
        private String end;
        private String startPlatform;

        @Override
        public int compareTo(@NotNull TrainRoutes.PathInfo other) {
            return Double.compare(this.distance, other.distance);
        }
        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PathInfo pathInfo = (PathInfo) obj;
            return Objects.equals(path, pathInfo.path);
        }
    }

    public static class Graph {
        // 邻接表
        private final Map<String, List<Edge>> adjacencyList = new HashMap<>();

        // 边
        static class Edge {
            String target;
            double distance;

            Edge(String target, double distance) {
                this.target = target;
                this.distance = distance;
            }
        }

        // 添加边
        public void addEdge(String source, String target, double distance) {
            adjacencyList.putIfAbsent(source, new ArrayList<>());
            adjacencyList.get(source).add(new Edge(target, distance));
        }

        // 获取所有路径
        public void findAllPaths(String start, Set<String> end, List<String> path, Set<String> visited, List<PathInfo> pathInfoList) {
            visited.add(start);
            path.add(start);
            if (end.contains(start) && path.size() > 1) {
                List<String> outPath = new ArrayList<>();
                Set<String> tags = new HashSet<>();
                boolean repeat = false;
                for (int i = 0; i < path.size(); i++) {
                    if (!outPath.contains(path.get(i).split("-")[0]) || i == path.size() - 1) {
                        outPath.add(path.get(i).split("-")[0]);
                    } else if (i != path.size() - 1 && !outPath.get(outPath.size() - 1).equals(path.get(i).split("-")[0])) {
                        repeat = true;
                    }
                    if (i != path.size() - 1) {
                        if (!tags.add(path.get(i).split("-")[3])) {
                            repeat = true;
                        }
                    }
                }
                double distance = calculateTotalDistance(path);
                if (distance > 0 && !repeat) {
                    pathInfoList.add(new PathInfo(outPath, tags, distance, calculateFare(distance), path.get(0).substring(0, path.get(0).lastIndexOf("-"))));
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
        private double calculateTotalDistance(List<String> path) {
            double totalDistance = 0;
            for (int i = 0; i < path.size() - 1; i++) {
                String from = path.get(i);
                String to = path.get(i + 1);
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

    // 从文件中读取图
    public static void readGraphFromFile(String filename) {
        graph = new Graph();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String source = parts[0].replace("-->", "").trim();
                    double distance = Double.parseDouble(parts[1].trim());
                    String target = parts[2].replace(";", "").trim();
                    graph.addEdge(source, target, distance);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,"路径文件读取错误: " + e.getMessage());
        }
        plugin.getLogger().log(Level.INFO,"路径解析成功！");
    }

    public static List<PathInfo> getPathInfoList(String startStation, String endStation) {
        List<PathInfo> pathInfoList = new ArrayList<>();
        Set<String> start = new HashSet<>();
        Set<String> end = new HashSet<>();
        for (String k : graph.adjacencyList.keySet()) {
            String[] splitStart = startStation.split("-");
            String[] splitKey = k.split("-");
            if (k.startsWith(splitStart[0]) && !splitKey[2].startsWith(splitKey[0].substring(0, splitKey[0].lastIndexOf("站")))) {
                start.add(k);
            }
            if (k.startsWith(endStation.split("-")[0])) {
                end.add(k);
            }
            for (Graph.Edge edge : graph.adjacencyList.get(k)) {
                String[] splitTarget = edge.target.split("-");
                if (edge.target.startsWith(splitStart[0]) && !splitTarget[2].startsWith(splitTarget[0].substring(0, splitTarget[0].lastIndexOf("站")))) {
                    start.add(edge.target);
                }
                if (edge.target.startsWith(endStation.split("-")[0])) {
                    end.add(edge.target);
                }
            }
        }

        for (String s : start) {
            List<String> path = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            graph.findAllPaths(s, end, path, visited, pathInfoList);
        }

        // 去重
        pathInfoList = pathInfoList.stream().distinct().collect(Collectors.toList());

        // 按照价格排序
        Collections.sort(pathInfoList);

        return pathInfoList;
    }
}