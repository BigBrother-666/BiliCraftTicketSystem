package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.menu.search.TicketRanker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TicketRanker} 的纯逻辑单测：距离前 N ∪ 票价前 M 合并去重 + 归一化加权排序。
 */
public class TicketRankerTest {

    private static TicketRanker.Candidate c(int idx, double dist, double price) {
        return new TicketRanker.Candidate(idx, dist, price);
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(TicketRanker.rank(new ArrayList<>(), 5, 5, 0.5, 0.5).isEmpty());
        assertTrue(TicketRanker.rank(null, 5, 5, 0.5, 0.5).isEmpty());
    }

    @Test
    void unionOfTopDistanceAndTopPrice() {
        // 0: 近但贵  1: 远但便宜  2: 中等  3: 又远又贵（既不进距离前1也不进票价前1）
        List<TicketRanker.Candidate> in = List.of(
                c(0, 10, 100),
                c(1, 100, 10),
                c(2, 50, 50),
                c(3, 200, 200)
        );
        // 距离前 1 = {0}，票价前 1 = {1}，合并 = {0,1}，下标 3 不应出现
        List<Integer> order = TicketRanker.rank(in, 1, 1, 0.5, 0.5);
        assertEquals(2, order.size());
        assertTrue(order.contains(0));
        assertTrue(order.contains(1));
        assertFalse(order.contains(3));
    }

    @Test
    void weightAllOnPriceSortsByPrice() {
        List<TicketRanker.Candidate> in = List.of(
                c(0, 10, 100),   // 最贵
                c(1, 100, 10),   // 最便宜
                c(2, 50, 50)
        );
        // 不限条数，权重全给票价 → 完全按票价升序
        List<Integer> order = TicketRanker.rank(in, 0, 0, 0.0, 1.0);
        assertEquals(List.of(1, 2, 0), order);
    }

    @Test
    void weightAllOnDistanceSortsByDistance() {
        List<TicketRanker.Candidate> in = List.of(
                c(0, 10, 100),   // 最近
                c(1, 100, 10),   // 最远
                c(2, 50, 50)
        );
        List<Integer> order = TicketRanker.rank(in, 0, 0, 1.0, 0.0);
        assertEquals(List.of(0, 2, 1), order);
    }

    @Test
    void singleCandidateNoDivideByZero() {
        List<Integer> order = TicketRanker.rank(List.of(c(0, 42, 42)), 5, 5, 0.5, 0.5);
        assertEquals(List.of(0), order);
    }

    @Test
    void identicalValuesStable() {
        // 全部相同 → span 为 0，归一化取 0，不抛异常，全部保留
        List<TicketRanker.Candidate> in = List.of(c(0, 10, 10), c(1, 10, 10), c(2, 10, 10));
        List<Integer> order = TicketRanker.rank(in, 0, 0, 0.5, 0.5);
        assertEquals(3, order.size());
    }

    @Test
    void minDirectAddsBestDirectWhenResultAllTransfer() {
        // 下标 0/1 = 直达（贵/远），2/3 = 联程（又近又便宜），directCount=2
        // 距离前1={2}、票价前1={3}，混排结果全是联程 → 应补 1 条最优直达到最前
        List<TicketRanker.Candidate> in = List.of(
                c(0, 100, 100),  // 直达：贵
                c(1, 90, 120),   // 直达：更贵但稍近
                c(2, 10, 30),    // 联程：最近
                c(3, 12, 20)     // 联程：最便宜
        );
        List<Integer> order = TicketRanker.rankWithMinDirect(in, 2, 1, 1, 0.5, 0.5, 1);
        assertFalse(order.isEmpty());
        assertTrue(order.getFirst() < 2, "结果最前应是补充的直达票");
        // 最优直达（权重最小）应为下标 1（更近，两权重各半时略优）或 0，取决于归一化；至少是直达
        long directCount = order.stream().filter(i -> i < 2).count();
        assertEquals(1, directCount, "只补 1 条直达");
    }

    @Test
    void minDirectNoopWhenResultAlreadyHasDirect() {
        // 直达票本就进了展示集，无需兜底，结果不应重复补
        List<TicketRanker.Candidate> in = List.of(
                c(0, 10, 10),    // 直达：又近又便宜，必进
                c(1, 100, 100)   // 联程：又远又贵
        );
        List<Integer> order = TicketRanker.rankWithMinDirect(in, 1, 1, 1, 0.5, 0.5, 1);
        assertEquals(1, order.stream().filter(i -> i < 1).count(), "直达票不重复");
    }

    @Test
    void minDirectZeroDisablesFallback() {
        List<TicketRanker.Candidate> in = List.of(c(0, 100, 100), c(1, 10, 10));
        // directCount=1, minDirect=0 → 不兜底，结果里可以没有直达票
        List<Integer> order = TicketRanker.rankWithMinDirect(in, 1, 1, 1, 0.5, 0.5, 0);
        assertEquals(TicketRanker.rank(in, 1, 1, 0.5, 0.5), order);
    }

    @Test
    void minDirectNoopWhenNoDirectExists() {
        // 全是联程（directCount=0），无直达可补，不抛异常；不限条数应保留全部
        List<TicketRanker.Candidate> in = List.of(c(0, 10, 10), c(1, 20, 20));
        List<Integer> order = TicketRanker.rankWithMinDirect(in, 0, 0, 0, 0.5, 0.5, 1);
        assertEquals(2, order.size());
    }
}
