package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeojsonBuilder;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.RailEdge;
import com.bigbrother.bilicraftticketsystem.route.NodeId;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TraversalTest {

    private RailEdge edge(String from, String to, String lineId) {
        return edge(from, to, lineId, "paralon-railway");
    }

    private RailEdge edge(String from, String to, String lineId, String railwaySystemId) {
        // 默认 owner = lineId（普通线段语义）
        return edge(from, to, lineId, railwaySystemId, lineId);
    }

    private RailEdge edge(String from, String to, String lineId, String railwaySystemId, String ownerLineId) {
        List<LngLatAlt> coords = new ArrayList<>();
        coords.add(new LngLatAlt(0, 0, 64));
        coords.add(new LngLatAlt(10, 0, 64));
        return new RailEdge(from, to, lineId, railwaySystemId, coords, "#AA0000", 10, 0, null, "test_world", null, null, ownerLineId);
    }

    @Test
    void edgeIdIsDeterministic() {
        RailEdge a = edge("n.w.1.64.2", "n.w.3.64.4", "pr-cw");
        RailEdge b = edge("n.w.1.64.2", "n.w.3.64.4", "pr-cw");
        assertEquals(a.getId(), b.getId(), "相同输入应生成相同线段 id");
        assertEquals(NodeId.ofEdge("n.w.1.64.2", "n.w.3.64.4", "pr-cw"), a.getId());

        // 不同线路 -> 不同 id（共线区间叠层）
        RailEdge c = edge("n.w.1.64.2", "n.w.3.64.4", "contact");
        assertNotEquals(a.getId(), c.getId());
    }

    @Test
    void builderDerivesPrevNextAndLineProps() {
        // 仅用线（不含节点 Point，避免依赖 Bukkit Block），验证 prev/next 推算与线属性
        List<RailEdge> edges = new ArrayList<>();
        edges.add(edge("A", "B", "pr-cw"));
        edges.add(edge("B", "C", "pr-cw"));

        FeatureCollection fc = new GeojsonBuilder().build(new ArrayList<>(), edges);
        assertEquals(2, fc.getFeatures().size());

        Feature first = fc.getFeatures().getFirst();
        assertInstanceOf(LineString.class, first.getGeometry());
        Map<String, Object> props = first.getProperties();
        assertEquals("A", props.get("from"));
        assertEquals("B", props.get("to"));
        assertEquals("pr-cw", props.get("lineId"));
        assertEquals("paralon-railway", props.get("railwaySystemId"));
        assertEquals("#AA0000", props.get("color"));
        assertEquals(0, props.get("layer"));
        assertNotNull(props.get("id"));
        assertNotNull(props.get("length"));
    }

    @Test
    void parallelEdgesDoNotDuplicatePrevNext() {
        // 同一对节点之间有两条不同 lineId 的边（如联络线与营运线并行）：两条边都要保留，
        // 但节点的 prev / next 表达的是相邻节点，只能出现一次。
        List<RailEdge> edges = new ArrayList<>();
        edges.add(edge("A", "B", "pr-s3"));
        edges.add(edge("A", "B", "contact", null, "pr-s3"));
        edges.add(edge("A", "C", "pr-s3"));
        Map<String, List<String>> next = new GeojsonBuilder().adjacency(edges, true);
        Map<String, List<String>> prev = new GeojsonBuilder().adjacency(edges, false);
        assertEquals(List.of("B", "C"), next.get("A"), "next 不应重复");
        assertEquals(List.of("A"), prev.get("B"), "prev 不应重复");
    }

    @Test
    void contactEdgeOmitsRailwaySystemId() {
        // 联络线区间 railwaySystemId 为 null，geojson 中应省略该属性
        List<RailEdge> edges = new ArrayList<>();
        edges.add(edge("A", "B", "contact", null));
        FeatureCollection fc = new GeojsonBuilder().build(new ArrayList<>(), edges);
        Map<String, Object> props = fc.getFeatures().getFirst().getProperties();
        assertFalse(props.containsKey("railwaySystemId"), "联络线区间不应含 railwaySystemId");
    }

    @Test
    void builderWritesOwnerProperty() {
        // owner = 产出本段的遍历单元（即本段所在的文件名）。联络线段的 owner 是带出它的营运线，
        // 与自身 lineId=contact 不同——这正是「文件归属按单元、而非按 lineId」的体现。
        List<RailEdge> edges = new ArrayList<>();
        edges.add(edge("switchA", "switchB", "contact", null, "line-a"));
        FeatureCollection fc = new GeojsonBuilder().build(new ArrayList<>(), edges);
        Map<String, Object> props = fc.getFeatures().getFirst().getProperties();
        assertEquals("line-a", props.get("owner"), "联络线段应写出 owner=带出它的营运线");
    }
}
