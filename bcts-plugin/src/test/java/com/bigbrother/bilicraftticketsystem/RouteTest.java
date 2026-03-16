package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RouteTest {
    private static final int total = 5000;


    private MermaidGraph.Node getRandomNode() {
        Set<MermaidGraph.Node> nodes = TrainRoutes.graph.getAllNodes();
        List<MermaidGraph.Node> sample = new ArrayList<>(nodes);
        return sample.get(new Random().nextInt(sample.size()));
    }

    @BeforeEach
    void setup() {
        try {
            TrainRoutes.readGraphFromFile(ResourceLoader.load("routes.mmd"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    void testShortestPath() {
        TrainRoutes.PathInfo path = TrainRoutes.findShortestPath("新赫洛堡站", "新赫洛堡站");
        assertNotNull(path);
        assertEquals(9, path.getStationSequence().size());
        path = TrainRoutes.findShortestPath("新赫洛堡站", "None");
        assertNull(path);
        path = TrainRoutes.findShortestPath("新赫洛堡站", "");
        assertNull(path);
    }

    @Test
    void testAllPath() {
        List<TrainRoutes.PathInfo> paths = TrainRoutes.getPathInfoList("新特拉姆站", "春风平原站");
        assertNotNull(paths);

        // 路线不相同
        List<List<String>> stationSequenceList = new ArrayList<>();
        for (TrainRoutes.PathInfo path : paths) {
            for (List<String> sequence : stationSequenceList) {
                assertNotEquals(sequence, path.getStationSequence());
            }
            stationSequenceList.add(path.getStationSequence());
        }

        paths = TrainRoutes.getPathInfoList("新赫洛堡站", "None");
        assertEquals(0, paths.size());
        paths = TrainRoutes.getPathInfoList("新赫洛堡站", "");
        assertEquals(0, paths.size());
    }

    @Test
    void benchmarkShortestPathSearch() {
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        int cnt = 0;

        long startAll = System.nanoTime();
        while (cnt < total) {
            MermaidGraph.Node startStation = getRandomNode();
            MermaidGraph.Node endStation = getRandomNode();
            if (startStation.isStation() && endStation.isStation()) {
                long start = System.nanoTime();

                TrainRoutes.findShortestPath(
                        startStation.getStationName(),
                        endStation.getStationName()
                );

                long duration = System.nanoTime() - start;

                totalTime += duration;
                minTime = Math.min(minTime, duration);
                maxTime = Math.max(maxTime, duration);

                cnt++;
            }
        }

        long endAll = System.nanoTime();
        System.out.println("====================================");
        System.out.printf("图节点数: %d, 边数: %d%n", TrainRoutes.graph.adjacencyList.size(), TrainRoutes.graph.adjacencyList.values().stream().mapToInt(List::size).sum());
        System.out.printf("寻找%d轮最短路径总时间: %d ms%n", total, (endAll - startAll) / 1_000_000);
        System.out.printf("平均时间: %d μs%n", totalTime / total / 1000);
        System.out.printf("最小时间: %d μs%n", minTime / 1000);
        System.out.printf("最大时间: %d μs%n", maxTime / 1000);
        System.out.println("====================================");
    }

    @Test
    void benchmarkAllPathSearch() {
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        List<Integer> pathCountList = new ArrayList<>();
        int cnt = 0;

        long startAll = System.nanoTime();
        while (cnt < total) {
            MermaidGraph.Node startStation = getRandomNode();
            MermaidGraph.Node endStation = getRandomNode();
            if (startStation.isStation() && endStation.isStation()) {
                long start = System.nanoTime();

                List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(
                        startStation.getStationName(),
                        endStation.getStationName()
                );

                long duration = System.nanoTime() - start;

                totalTime += duration;
                minTime = Math.min(minTime, duration);
                maxTime = Math.max(maxTime, duration);
                pathCountList.add(pathInfoList.size());

                cnt++;
            }
        }

        long endAll = System.nanoTime();

        System.out.println("====================================");
        System.out.printf("图节点数: %d, 边数: %d%n", TrainRoutes.graph.adjacencyList.size(), TrainRoutes.graph.adjacencyList.values().stream().mapToInt(List::size).sum());
        System.out.printf("寻找%d轮全部路径总时间: %d ms%n", total, (endAll - startAll) / 1_000_000);
        System.out.printf("平均时间: %d μs%n", totalTime / total / 1000);
        System.out.printf("最小时间: %d μs%n", minTime / 1000);
        System.out.printf("最大时间: %d μs%n", maxTime / 1000);
        System.out.printf("最小路径数: %d%n", pathCountList.stream().min(Integer::compare).orElse(0));
        System.out.printf("最大路径数: %d%n", pathCountList.stream().max(Integer::compare).orElse(0));
        System.out.printf("平均路径数: %d%n", pathCountList.stream().mapToInt(Integer::intValue).sum() / total);
        System.out.println("====================================");
    }
}
