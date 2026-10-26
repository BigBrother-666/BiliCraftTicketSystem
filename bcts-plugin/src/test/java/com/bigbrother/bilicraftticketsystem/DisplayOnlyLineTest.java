package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仅展示线路（{@link LineInfo#isDisplayOnly()}）不参与寻路的单测。
 * <pre>
 *   L1（仅展示）: nA(A) --10--> nB(B)
 *   L2:          nA(A) --20--> nM(M) --20--> nB(B)
 * </pre>
 * 与后端 routing/display_only_test.go 同一场景。
 */
public class DisplayOnlyLineTest {

    private Feature point(String id, String name, double x, double z) {
        Feature f = new Feature();
        f.setGeometry(new Point(new LngLatAlt(x, z, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("type", "station");
        props.put("name", name);
        f.setProperties(props);
        return f;
    }

    private Feature line(String id, String from, String to, String lineId, double length, String dir) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(1, 0, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", length);
        props.put("departDir", dir);
        f.setProperties(props);
        return f;
    }

    private static LineInfo lineInfo(String id, boolean displayOnly) {
        return new LineInfo(id, "sys", id, "#000000", List.of(), null, null, null, null, null, null, null, displayOnly);
    }

    @BeforeEach
    void setUp() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "A", 0, 0));
        fc.add(point("nB", "B", 10, 0));
        fc.add(point("nM", "M", 5, 10));
        fc.add(line("e.L1.nA__nB", "nA", "nB", "L1", 10, "e"));
        fc.add(line("e.L2.nA__nM", "nA", "nM", "L2", 20, "s"));
        fc.add(line("e.L2.nM__nB", "nM", "nB", "L2", 20, "n"));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
    }

    @AfterEach
    void tearDown() {
        LineConfig.replaceAll(List.of());
    }

    @Test
    void displayOnlyLineSkipped() {
        LineConfig.replaceAll(List.of(lineInfo("L1", false), lineInfo("L2", false)));
        assertEquals("L1", GeoRouteEngine.findByStation("A", "B").getFirst().getLineIdSequence().getFirst());

        LineConfig.replaceAll(List.of(lineInfo("L1", true), lineInfo("L2", false)));
        List<GeoRoutePath> paths = GeoRouteEngine.findByStation("A", "B");
        assertFalse(paths.isEmpty());
        for (GeoRoutePath p : paths) {
            assertFalse(p.getLineIdSequence().contains("L1"), "不应经过仅展示线路 L1");
        }
        assertNull(GeoRouteEngine.validatePath(List.of("nA", "nB"), List.of("L1")), "网页购票不能校验通过仅展示线路");
        assertNotNull(GeoRouteEngine.validatePath(List.of("nA", "nM", "nB"), List.of("L2", "L2")));

        LineConfig.replaceAll(List.of(lineInfo("L1", true), lineInfo("L2", true)));
        assertTrue(GeoRouteEngine.findByStation("A", "B").isEmpty());
        assertNull(GeoRouteEngine.findFromNode("nA", "B"), "交通卡 / 导航也不经过仅展示线路");
    }
}
