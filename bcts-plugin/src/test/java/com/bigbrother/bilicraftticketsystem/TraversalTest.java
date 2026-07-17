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

    /**
     * 复刻 {@code GeoTraversalTask.saveLineIncremental} 的联络线合并删除判据（该方法私有且依赖 Bukkit，
     * 无法直接单测）：只丢弃 owner ∈ targetLineIds 的旧段，其余（含 owner=null）保留。
     */
    private List<RailEdge> mergeContact(List<RailEdge> oldContact, java.util.Set<String> targetLineIds) {
        List<RailEdge> kept = new ArrayList<>();
        for (RailEdge e : oldContact) {
            String owner = e.getOwnerLineId();
            if (owner != null && targetLineIds.contains(owner)) {
                continue;
            }
            kept.add(e);
        }
        return kept;
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
        // owner 非空时写出；联络线段 owner = 触发它的目标线（与自身 lineId=contact 不同）
        List<RailEdge> edges = new ArrayList<>();
        edges.add(edge("switchA", "switchB", "contact", null, "line-a"));
        FeatureCollection fc = new GeojsonBuilder().build(new ArrayList<>(), edges);
        Map<String, Object> props = fc.getFeatures().getFirst().getProperties();
        assertEquals("line-a", props.get("owner"), "联络线段应写出 owner=触发它的目标线");
    }

    @Test
    void incrementalMergeKeepsOtherLinesReverseContactSegment() {
        // 同一物理联络线：walk line-a 走出 switchA->switchB(owner=line-a)，
        // 走 line-b 时曾走出反向 switchB->switchA(owner=line-b)。
        // 现在 walk line-a：只应删 owner=line-a 的旧段，保留 owner=line-b 的反向段（否则联络线断开）。
        List<RailEdge> oldContact = new ArrayList<>();
        RailEdge aToB = edge("switchA", "switchB", "contact", null, "line-a");
        RailEdge bToA = edge("switchB", "switchA", "contact", null, "line-b");
        oldContact.add(aToB);
        oldContact.add(bToA);

        List<RailEdge> kept = mergeContact(oldContact, java.util.Set.of("line-a"));

        assertEquals(1, kept.size(), "只应保留对端线拥有的反向段");
        assertEquals(bToA.getId(), kept.getFirst().getId(), "保留的必须是 owner=line-b 的反向段");
        assertTrue(kept.stream().noneMatch(e -> e.getId().equals(aToB.getId())),
                "owner=line-a 的旧段应被删除，由本次遍历重新走出");
    }

    @Test
    void incrementalMergeKeepsLegacyNullOwnerSegments() {
        // 旧文件（owner 字段引入前）的联络线段 owner=null，合并时应保守保留
        List<RailEdge> oldContact = new ArrayList<>();
        oldContact.add(edge("switchA", "switchB", "contact", null, null));
        List<RailEdge> kept = mergeContact(oldContact, java.util.Set.of("line-a"));
        assertEquals(1, kept.size(), "owner=null 的旧段应保守保留");
    }
}
