package com.bigbrother.bilicraftticketsystem;

import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.pricePerKm;

public class TrainRoutes {
    private static Graph graph;
    @Getter
    private static Map<String, List<String>> stationTagMap;  // tag : [车站-铁路名-方向, 车站-铁路名-方向, ...]

    // 生成lore的数据
    @Getter
    public static class StationAndRailway {
        private final String stationName;
        private final String railwayName;

        public static Set<String> getAllStations(List<StationAndRailway> list) {
            return list.stream()
                    .map(StationAndRailway::getStationName)
                    .collect(Collectors.toSet());
        }

        public StationAndRailway(String stationName, String railwayName) {
            this.stationName = stationName;
            this.railwayName = railwayName;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            StationAndRailway pair = (StationAndRailway) obj;
            return Objects.equals(stationName, pair.stationName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stationName);
        }
    }

    @Data
    public static class PathInfo implements Comparable<PathInfo> {
        public PathInfo(List<StationAndRailway> path, Set<String> tags, double distance, double price, String startPlatform, String startPlatformTag) {
            this.path = path;
            this.tags = tags;
            this.distance = distance;
            this.price = price;
            this.start = path.get(0).getStationName();
            this.end = path.get(path.size() - 1).getStationName();
            this.startPlatform = startPlatform;
            this.startPlatformTag = startPlatformTag;
        }

        private List<StationAndRailway> path;
        private Set<String> tags;
        private double distance;
        private double price;
        private String start;
        private String end;
        private String startPlatform;
        private String startPlatformTag;

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
            if (!path.isEmpty()) {
                visited.add(start);
            }
            path.add(start);
            if (end.contains(start) && path.size() > 1) {
                List<StationAndRailway> outPath = new ArrayList<>();   // [车站名, 铁路名+方向]
                Set<String> tags = new LinkedHashSet<>();
                boolean repeat = false;
                for (int i = 0; i < path.size(); i++) {
                    String[] split = path.get(i).split("-");
                    if (split.length < 4) {
                        break;
                    }
                    // 添加tag
                    if (i != path.size() - 1) {
                        if (!tags.add(split[3])) {
                            repeat = true;
                            break;
                        }
                    }
                    // 车站名为空（只包含tag的节点 ---tag）
                    if (split[0].isEmpty()) {
                        continue;
                    }
                    // 添加path
                    if (outPath.stream().noneMatch(arr -> arr.getStationName().equals(split[0])) || i == path.size() - 1) {
                        outPath.add(new StationAndRailway(split[0], split[1] + split[2]));
                    } else if (i != path.size() - 1 && !outPath.get(outPath.size() - 1).getStationName().equals(split[0])) {
                        // 路径已存在当前车站名   当前节点不是最后一个节点，且路径最后一个节点的车站名和当前车站名不同
                        // 跳过直达车在一个车站需要多个tag的情况
                        repeat = true;
                        break;
                    }
                }
                double distance = calculateTotalDistance(path);
                if (distance > 0 && !repeat) {
                    pathInfoList.add(new PathInfo(
                            outPath,
                            tags,
                            distance,
                            calculateFare(distance),
                            path.get(0).substring(0, path.get(0).lastIndexOf("-")),
                            path.get(0).split("-")[3] + "-" + path.get(0).split("-")[2]
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
    public static void readGraphFromFile(String filename) throws IOException {
        graph = new Graph();
        stationTagMap = new HashMap<>();
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
                // 解析车站tsg要使用的数据
                if (Arrays.stream(source.split("-")).noneMatch(String::isEmpty)) {
                    int lastIndex = source.lastIndexOf("-");
                    String info = source.substring(0, lastIndex);
                    String tag = source.substring(lastIndex + 1);
                    if (stationTagMap.containsKey(tag)) {
                        stationTagMap.get(tag).add(info);
                    } else {
                        List<String> temp = new ArrayList<>();
                        temp.add(info);
                        stationTagMap.put(tag, temp);
                    }
                }
                if (Arrays.stream(target.split("-")).noneMatch(String::isEmpty)) {
                    int lastIndex = target.lastIndexOf("-");
                    String info = target.substring(0, lastIndex);
                    String tag = target.substring(lastIndex + 1);
                    if (stationTagMap.containsKey(tag)) {
                        stationTagMap.get(tag).add(info);
                    } else {
                        List<String> temp = new ArrayList<>();
                        temp.add(info);
                        stationTagMap.put(tag, temp);
                    }
                }
            }
        }
        br.close();
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

        // 按照价格排序
        Collections.sort(pathInfoList);

        // 去重
        pathInfoList = pathInfoList.stream().distinct().collect(Collectors.toList());

        return pathInfoList;
    }
}