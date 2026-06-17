package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteGraph;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * geojson 反向建图 + node-id 寻路引擎的纯逻辑单测。
 * <p>
 * fixture 拓扑（站名 A/B，开关 s1/s2，含一条 A→B→A 形态的重复线路 L1）：
 * <pre>
 *   nA  (station A) --L1,10--> s1 --contact,5--> s2 --L1,10--> nB (station B)
 *   nA2 (station A) --L1, 1--------------------> s2
 *   nA  (station A) --L2,100-------------------------------> nB (station B)
 * </pre>
 * 最短 A→B：经 nA2 (1+10=11)；次短经 nA 走 L1/contact/L1 (25)；L2 直达最长 (100)。
 */
public class GeoRouteEngineTest {

    private FeatureCollection fc;

    @BeforeEach
    void setUp() {
        fc = new FeatureCollection();
        fc.add(point("nA", "station", "A", 0, 64, 0));
        fc.add(point("nA2", "station", "A", 0, 64, 50));
        fc.add(point("s1", "switch", null, 10, 64, 0));
        fc.add(point("s2", "switch", null, 20, 64, 0));
        fc.add(point("nB", "station", "B", 30, 64, 0));

        fc.add(line("e.L1.nA__s1", "nA", "s1", "L1", 10));
        fc.add(line("e.contact.s1__s2", "s1", "s2", "contact", 5, "e"));
        fc.add(line("e.L1.s2__nB", "s2", "nB", "L1", 10, "n"));
        fc.add(line("e.L1.nA2__s2", "nA2", "s2", "L1", 1));
        fc.add(line("e.L2.nA__nB", "nA", "nB", "L2", 100));
    }

    private Feature point(String id, String type, String name, double x, double y, double z) {
        Feature f = new Feature();
        // 坐标约定：经度=x、纬度=z、高度=y
        f.setGeometry(new Point(new LngLatAlt(x, z, y)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("type", type);
        if (name != null) {
            props.put("name", name);
        }
        f.setProperties(props);
        return f;
    }

    private Feature line(String id, String from, String to, String lineId, double length) {
        return line(id, from, to, lineId, length, null);
    }

    private Feature line(String id, String from, String to, String lineId, double length, String departDir) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(1, 0, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", length);
        if (departDir != null) {
            props.put("departDir", departDir);
        }
        f.setProperties(props);
        return f;
    }

    @Test
    void buildsGraphWithExpectedCounts() {
        GeoRouteGraph g = new GeoGraphLoader(null).loadFeatureCollection(fc);
        assertEquals(5, g.nodeCount());
        assertEquals(5, g.linkCount());
    }

    @Test
    void stationLookupByName() {
        GeoRouteGraph g = new GeoGraphLoader(null).loadFeatureCollection(fc);
        List<GeoNode> aNodes = g.stationNodes("A");
        assertEquals(2, aNodes.size(), "车站 A 有两个站台节点");
        assertEquals(1, g.stationNodes("B").size());
        assertTrue(g.stationNodes("unknown").isEmpty());
    }

    @Test
    void linkEndpointsAccumulateLineIds() {
        GeoRouteGraph g = new GeoGraphLoader(null).loadFeatureCollection(fc);
        // s2 出入边含 contact / L1，应都累积
        GeoNode s2 = g.getNode("s2");
        assertTrue(s2.getLineIds().contains("contact"));
        assertTrue(s2.getLineIds().contains("L1"));
    }

    @Test
    void shortestPathPicksCheapestPlatform() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        List<GeoRoutePath> paths = GeoRouteEngine.findByStation("A", "B");
        assertFalse(paths.isEmpty());
        // 最短经 nA2：1 + 10 = 11
        GeoRoutePath best = paths.get(0);
        assertEquals(11.0 / 1000, best.getDistance(), 1e-9);
        assertEquals("nA2", best.getStartNode().getId());
        assertEquals("nB", best.getEndNode().getId());
        List<String> ids = new ArrayList<>();
        best.getNodes().forEach(n -> ids.add(n.getId()));
        assertEquals(List.of("nA2", "s2", "nB"), ids);
        assertEquals(List.of("L1", "L1"), best.getLineIdSequence());
    }

    @Test
    void lineIdSequenceKeepsRepeatedLineNonDeduplicated() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        // 从 nA 出发：唯一较短直达是 L1->contact->L1（25），比 L2 直达(100)短
        GeoRoutePath path = GeoRouteEngine.findFromNode("nA", "B");
        assertNotNull(path);
        assertEquals(25.0 / 1000, path.getDistance(), 1e-9);
        // 逐段 lineId 不合并不去重：L1 出现两次，中间夹 contact
        assertEquals(List.of("L1", "contact", "L1"), path.getLineIdSequence());
        List<String> ids = new ArrayList<>();
        path.getNodes().forEach(n -> ids.add(n.getId()));
        assertEquals(List.of("nA", "s1", "s2", "nB"), ids);
    }

    @Test
    void switcherLineIdsExportsPerSwitchDecision() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        // nA -> s1 -> s2 -> nB，段 lineId = [L1, contact, L1]
        // s1 的驶出段是 contact，s2 的驶出段是 L1；起点站 nA 与终点站 nB 不产生道岔决策
        GeoRoutePath path = GeoRouteEngine.findFromNode("nA", "B");
        assertNotNull(path);
        assertEquals(List.of("contact", "L1"), path.switcherLineIds());
        assertEquals(List.of("A", "B"), path.stationSequence());
        assertEquals("A", path.getStartStationName());
        assertEquals("B", path.getEndStationName());
    }

    @Test
    void routeStepsExportsEveryNode() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        // nA(站台) -> s1(道岔,驶出向 e) -> s2(道岔,驶出向 n) -> nB(站台)
        GeoRoutePath path = GeoRouteEngine.findFromNode("nA", "B");
        assertNotNull(path);
        // 每个节点一项：站台=P，道岔=S:驶出段物理出向
        assertEquals(List.of("P", "S:e", "S:n", "P"), path.routeSteps());
    }

    @Test
    void sidingDirectionDetectsMainlineSwitch() {
        // 进站道岔 sw：一条出边去 platform 车站(到发线, departDir=s)、一条去 sw2 道岔(正线, departDir=e)
        FeatureCollection f = new FeatureCollection();
        f.add(point("sw", "switch", null, 0, 64, 0));
        f.add(point("plat", "station", "P", 5, 64, 5));
        f.add(point("sw2", "switch", null, 10, 64, 0));
        f.add(line("e.L.sw__plat", "sw", "plat", "L", 5, "s"));
        f.add(line("e.L.sw__sw2", "sw", "sw2", "L", 5, "e"));
        GeoRouteGraph g = new GeoGraphLoader(null).loadFeatureCollection(f);
        // 进站道岔 sw → 到发线出向 = 通往车站那条边的 departDir = "s"
        assertEquals("s", g.sidingDirectionOfMainlineSwitch(g.getNode("sw")));
        // 普通分岔(只有去车站、无去道岔的出边) sw2 不是进站道岔 → null
        assertNull(g.sidingDirectionOfMainlineSwitch(g.getNode("sw2")));
        assertNull(g.sidingDirectionOfMainlineSwitch(null));
    }

    @Test
    void unknownStartOrEndReturnsNoPath() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        assertNull(GeoRouteEngine.findFromNode("does-not-exist", "B"));
        assertNull(GeoRouteEngine.findFromNode("nA", "no-such-station"));
        assertTrue(GeoRouteEngine.findByStation("no-such-station", "B").isEmpty());
    }

    @Test
    void startLineIdReturnsFirstSegment() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
        // 最短 A→B 经 nA2：段 [L1, L1]，起始线为 L1
        GeoRoutePath best = GeoRouteEngine.findByStation("A", "B").get(0);
        assertEquals("L1", best.getStartLineId());

        // nA 出发：段 [L1, contact, L1]，首段即起始线 L1
        GeoRoutePath viaContact = GeoRouteEngine.findFromNode("nA", "B");
        assertNotNull(viaContact);
        assertEquals("L1", viaContact.getStartLineId());
    }
}
