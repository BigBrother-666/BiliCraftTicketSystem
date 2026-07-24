package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoTraversalTaskIncrementalMergeTest {
    private RailEdge edge(String from, String to, String lineId) {
        List<LngLatAlt> coords = new ArrayList<>();
        coords.add(new LngLatAlt(0, 0, 64));
        coords.add(new LngLatAlt(10, 0, 64));
        return new RailEdge(from, to, lineId, "railway", coords, "#AA0000", 10, 0,
                null, "world", null, null, lineId);
    }

    @Test
    void incrementalTargetLineDropsMovedOldSegment() {
        RailEdge oldMovedSegment = edge("oldA", "oldB", "line-a");
        RailEdge currentSegment = edge("newA", "newB", "line-a");

        List<RailEdge> preserved = GeoTraversalTask.preserveExternalIngressEdges(
                List.of(oldMovedSegment),
                List.of(currentSegment),
                Set.of("newA", "newB"),
                Map.of(
                        "oldA", Set.of("line-a"),
                        "oldB", Set.of("line-a")
                ),
                "line-a"
        );

        assertTrue(preserved.isEmpty(), "moved target-line segments must not be resurrected");
    }

    @Test
    void incrementalTargetLineKeepsExternalIngressSegment() {
        RailEdge externalIngress = edge("lineBNode", "lineANode", "line-a");
        RailEdge currentSegment = edge("newA", "newB", "line-a");

        List<RailEdge> preserved = GeoTraversalTask.preserveExternalIngressEdges(
                List.of(externalIngress),
                List.of(currentSegment),
                Set.of("newA", "newB"),
                Map.of(
                        "lineBNode", Set.of("line-b"),
                        "lineANode", Set.of("line-a")
                ),
                "line-a"
        );

        assertEquals(1, preserved.size());
        assertEquals(externalIngress.getId(), preserved.getFirst().getId());
    }
}
