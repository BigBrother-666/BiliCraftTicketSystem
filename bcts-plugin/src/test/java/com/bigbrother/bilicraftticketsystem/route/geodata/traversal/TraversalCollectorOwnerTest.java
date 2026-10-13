package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「文件归属按遍历单元、而非按 lineId」的纯逻辑单测（不依赖 Bukkit / TC）。
 * <p>
 * 覆盖重构的核心不变量：
 * <ul>
 *   <li>一个单元走出的边，无论其 {@code lineId} 是哪条线，都落在<b>本单元</b>的产出里
 *       （越界边 / 联络线段 / 转线段都如此）；</li>
 *   <li>同一 {@code (from,to,lineId)} 边被多个到达面产出时，到达面合并而非丢弃；</li>
 *   <li>{@code owner} 属性被写入 geojson，供排查「这条边是哪次行走走出来的」。</li>
 * </ul>
 */
class TraversalCollectorOwnerTest {

    private static List<LngLatAlt> coords() {
        List<LngLatAlt> coords = new ArrayList<>();
        coords.add(new LngLatAlt(0, 0, 64));
        coords.add(new LngLatAlt(10, 0, 64));
        return coords;
    }

    /**
     * 往收集器里记一条边。{@code owner} 恒为该单元 id——这正是 {@link UnitWalk} 的行为。
     */
    private static void record(TraversalCollector collector, String from, String to, String lineId,
                               String owner, String enterFaceFrom) {
        collector.recordEdge(from, to, lineId, "paralon-railway", "#AA0000", coords(), 10,
                "e", "world", enterFaceFrom, "1_0", owner, null);
    }

    /**
     * 越界边：单元 L1 的道岔上声明了通往 L2 的出向，该段 lineId=L2 但由 L1 走出。
     * 旧实现按 lineId 分文件会把它写进 L2.geojson（而 L2 自己永远走不到它，整体覆盖即丢边）；
     * 新实现按 owner 分文件，它留在 L1 的产出里。
     */
    @Test
    void crossBoundaryEdgeBelongsToProducingUnit() {
        TraversalCollector l1 = new TraversalCollector();
        record(l1, "nSwitch", "nL2Node", "L2", "L1", "1_0");

        List<RailEdge> edges = l1.edges();
        assertEquals(1, edges.size(), "越界边应留在产出它的单元里");
        RailEdge edge = edges.getFirst();
        assertEquals("L2", edge.getLineId(), "边自身仍属于 L2（决定列车怎么走、前端怎么着色）");
        assertEquals("L1", edge.getOwnerLineId(), "但归属单元是走出它的 L1（决定写进哪个文件）");
    }

    /**
     * 联络线段：lineId=contact、owner=带出它的营运线。按单元分文件后不再有 contact.geojson，
     * 联络线段各自落在带出它的营运线文件里——这正是 owner 字段从「合并 contact 的删除依据」
     * 升为文件主键后，contact 合并逻辑可以整体删除的原因。
     */
    @Test
    void contactEdgeBelongsToProducingUnit() {
        TraversalCollector l1 = new TraversalCollector();
        record(l1, "nL1Switch", "nContactEnd", "contact", "L1", "1_0");
        TraversalCollector l2 = new TraversalCollector();
        record(l2, "nContactEnd", "nL1Switch", "contact", "L2", "0_1");

        // 同一条物理联络线从两条线的道岔出发，产出方向相反的两条边，各归各的单元、互不覆盖
        assertEquals("L1", l1.edges().getFirst().getOwnerLineId());
        assertEquals("L2", l2.edges().getFirst().getOwnerLineId());
        assertEquals("contact", l1.edges().getFirst().getLineId());
        assertEquals("contact", l2.edges().getFirst().getLineId());
    }

    /**
     * 同一 {@code (from,to,lineId)} 边被多个到达面产出（如一块 {@code [+train:lr]} 牌，
     * 两方向来车都续行本段）：到达面须合并进同一条边，不能后者丢弃。
     */
    @Test
    void sameEdgeFromMultipleEnterFacesMergesFaces() {
        TraversalCollector collector = new TraversalCollector();
        record(collector, "nA", "nB", "L1", "L1", "1_0");
        record(collector, "nA", "nB", "L1", "L1", "0_1");

        List<RailEdge> edges = collector.edges();
        assertEquals(1, edges.size(), "同 id 边只应有一条");
        assertEquals(2, edges.getFirst().getEnterFacesFrom().size(), "两个到达面都应保留");
        assertTrue(edges.getFirst().getEnterFacesFrom().containsAll(List.of("1_0", "0_1")));
    }

    /**
     * {@code owner} 必须出现在 geojson 的 LineString 属性里：它是排查「这条边由哪次行走走出」的唯一依据。
     * 前后端都不读该字段（已核对），故语义升级对它们无影响。
     */
    @Test
    void ownerIsWrittenToGeojson() {
        RailEdge edge = new RailEdge("nA", "nB", "L2", "paralon-railway", coords(), "#AA0000", 10, 0,
                "e", "world", "1_0", "1_0", "L1");
        FeatureCollection fc = new GeojsonBuilder().build(List.of(), List.of(edge));

        Feature line = fc.getFeatures().stream()
                .filter(f -> f.getGeometry() instanceof LineString)
                .findFirst().orElse(null);
        assertNotNull(line, "应产出一条 LineString");
        assertEquals("L1", line.getProperty("owner"), "owner 应为产出该边的单元");
        assertEquals("L2", line.getProperty("lineId"), "lineId 仍是边自身所属线路");
    }

    /**
     * 同一条边被两个单元各产出一份时，edge id 必须相同——读取侧才能按 id 合并到达面。
     */
    @Test
    void edgeIdIsDeterministicAcrossUnits() {
        RailEdge fromL1 = new RailEdge("nA", "nB", "L2", null, coords(), "#AA0000", 10, 0,
                "e", "world", "1_0", "1_0", "L1");
        RailEdge fromL2 = new RailEdge("nA", "nB", "L2", null, coords(), "#AA0000", 10, 0,
                "e", "world", "0_1", "1_0", "L2");
        assertEquals(fromL1.getId(), fromL2.getId(), "同 (from,to,lineId) 的 edge id 必须一致");
    }
}
