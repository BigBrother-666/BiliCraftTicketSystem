package com.bigbrother.bilicraftticketsystem.route.geograph;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bossbar 实时进度用的折线投影 / 路线轨迹纯逻辑单测。
 * <p>
 * fixture（同一 y=64 平面，坐标 x/z）：
 * <pre>
 *   A(0,0) --L1,len 100, 折线 (0,0)→(100,0)--> s1(100,0) --L1,len 50, 折线 (100,0)→(100,50)--> B(100,50)
 *   s1 --L2,len 300--> C(400,0)
 * </pre>
 */
public class GeoRouteTrackTest {
    private static final String A = "n.w.0.64.0";
    private static final String S1 = "n.w.100.64.0";
    private static final String B = "n.w.100.64.50";
    private static final String C = "n.w.400.64.0";

    private GeoRouteGraph graph;

    @BeforeEach
    void setUp() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point(A, "station", "A", 0, 0));
        fc.add(point(S1, "switch", null, 100, 0));
        fc.add(point(B, "station", "B", 100, 50));
        fc.add(point(C, "station", "C", 400, 0));
        fc.add(line("e1", A, S1, "L1", 100, new double[][]{{0, 0}, {50, 0}, {100, 0}}));
        fc.add(line("e2", S1, B, "L1", 50, new double[][]{{100, 0}, {100, 50}}));
        fc.add(line("e3", S1, C, "L2", 300, new double[][]{{100, 0}, {400, 0}}));
        graph = new GeoGraphLoader(null).loadFeatureCollection(fc);
    }

    @Test
    void polylineProjection() {
        GeoPolyline line = graph.getLink("e1").getPolyline();
        assertNotNull(line);
        assertEquals(100, line.getLength(), 1e-9);
        GeoPolyline.Projection p = line.project(75, 64, 2, -1);
        assertEquals(0.75, p.fraction(), 1e-9);
        assertEquals(1, p.segment());
        assertEquals(4, p.distSq(), 1e-9);
        // 带提示的局部搜索结果与全量一致
        assertEquals(0.75, line.project(75, 64, 2, 0).fraction(), 1e-9);
        // 超出端点夹到两端
        assertEquals(0, line.project(-10, 64, 0, -1).fraction(), 1e-9);
        assertEquals(1, line.project(120, 64, 0, -1).fraction(), 1e-9);
    }

    @Test
    void nodePathLocate() {
        GeoRouteTrack track = GeoRouteTrack.ofNodePath(graph, List.of(A, S1, B));
        assertNotNull(track);
        assertEquals(150, track.totalDistance(), 1e-9);
        assertEquals("w", track.getWorld());
        GeoRouteTrack.Cursor cursor = new GeoRouteTrack.Cursor();
        assertEquals(40, track.locate(0, 1, 40, 64, 0.5, cursor), 1e-9);
        // 第二跳：沿 s1→B 走了 20
        assertEquals(120, track.locate(0, 1, 100.3, 64, 20, cursor), 1e-9);
        // 搜索范围限定在第一跳时，夹到第一跳末端
        assertEquals(100, track.locate(0, 0, 100.3, 64, 20, new GeoRouteTrack.Cursor()), 1e-9);
    }

    @Test
    void nodePathMissingLinkReturnsNull() {
        assertNull(GeoRouteTrack.ofNodePath(graph, List.of(A, B)));
    }

    @Test
    void lineStationsTrack() {
        GeoRouteTrack.StationTrack st = GeoRouteTrack.ofLineStations(graph, List.of("A", "B"), "L1");
        assertNotNull(st);
        assertEquals(150, st.track().totalDistance(), 1e-9);
        assertEquals(0, st.stationDistance(0), 1e-9);
        assertEquals(150, st.stationDistance(1), 1e-9);
        assertArrayEquals(new int[]{0, 2}, st.stationNodeIndex());
        // 车站不在该线
        assertNull(GeoRouteTrack.ofLineStations(graph, List.of("A", "C"), "L1"));
    }

    private Feature point(String id, String type, String name, double x, double z) {
        Feature f = new Feature();
        // 坐标约定：经度=x、纬度=z、高度=y
        f.setGeometry(new Point(new LngLatAlt(x, z, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("type", type);
        if (name != null) {
            props.put("name", name);
        }
        f.setProperties(props);
        return f;
    }

    private Feature line(String id, String from, String to, String lineId, double length, double[][] xz) {
        Feature f = new Feature();
        LngLatAlt[] pts = new LngLatAlt[xz.length];
        for (int i = 0; i < xz.length; i++) {
            pts[i] = new LngLatAlt(xz[i][0], xz[i][1], 64);
        }
        f.setGeometry(new LineString(pts));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", length);
        f.setProperties(props);
        return f;
    }
}
