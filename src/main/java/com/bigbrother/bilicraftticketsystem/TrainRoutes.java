package com.bigbrother.bilicraftticketsystem;

import lombok.Data;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class TrainRoutes {
    private static List<PathInfo> pathInfoList;
    private final Graph graph;

    public TrainRoutes() {
        graph = readGraphFromFile("src/main/resources/routes.txt");
        pathInfoList = new ArrayList<>();
    }

    @Data
    public static class PathInfo {
        public PathInfo(List<String> path, List<String> tags, double distance, double price) {
            this.path = path;
            this.tags = tags;
            this.distance = distance;
            this.price = price;
            this.start = path.getFirst();
            this.end = path.getLast();
        }

        private List<String> path;
        private List<String> tags;
        private double distance;
        private double price;
        private String start;
        private String end;
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
        public void findAllPaths(String start, Set<String> end, List<String> path, Set<String> visited) {
            visited.add(start);
            path.add(start);

            if (end.contains(start)) {
                List<String> outPath = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < path.size(); i++) {
                    outPath.add(path.get(i).split("-")[0]);
                    if (i != 0 && i != path.size() - 1) {
                        tags.add(path.get(i).split("-")[3]);
                    }
                }
                double distance = calculateTotalDistance(path);
                pathInfoList.add(new PathInfo(outPath, tags, distance, calculateFare(distance)));
            } else {
                List<Edge> edges = adjacencyList.get(start);
                if (edges != null) {
                    for (Edge edge : edges) {
                        if (!visited.contains(edge.target)) {
                            findAllPaths(edge.target, end, path, visited);
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
            return Math.round(distance * 0.3 * 100.0) / 100.0;
        }
    }

    // 从文件中读取图
    public static Graph readGraphFromFile(String filename) {
        Graph graph = new Graph();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String source = parts[0].trim().replace("-->", "");
                    double distance = Double.parseDouble(parts[1].trim());
                    String target = parts[2].trim().replace(";", "");
                    graph.addEdge(source, target, distance);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("文件读取错误: " + e.getMessage());
        }
        return graph;
    }

    public List<PathInfo> getPathInfoList(String startStation, String endStation) {
        Set<String> start = new HashSet<>();
        Set<String> end = new HashSet<>();
        for (String k : graph.adjacencyList.keySet()) {
            if (k.startsWith(startStation.split("-")[0])) {
                start.add(k);
            }
            if (k.startsWith(endStation.split("-")[0])) {
                end.add(k);
            }
            for (Graph.Edge edge : graph.adjacencyList.get(k)) {
                if (edge.target.startsWith(startStation.split("-")[0])) {
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
            graph.findAllPaths(s, end, path, visited);
        }

        return pathInfoList;
    }
}