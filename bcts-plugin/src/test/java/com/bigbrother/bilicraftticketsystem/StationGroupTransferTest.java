package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.config.StationGroupConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
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
 * 车站组换乘（{@link StationGroupConfig}）对 {@link GeoRouteEngine#findTransferJourneys} 影响的单测。
 * <pre>
 *   L1: n甲(甲) --L1,10--> n乙(乙)
 *   L2: n丙(丙) --L2,10--> n丁(丁)
 * </pre>
 * 乙、丙不同名且无连接边：无车站组时 甲→丁 无任何方案；乙、丙同组后可在乙下车、丙上车联程到丁。
 */
public class StationGroupTransferTest {

    /**
     * 构造车站点要素。
     */
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

    /**
     * 构造有向区间要素。
     */
    private Feature line(String id, String from, String to, String lineId, double length) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(1, 0, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", length);
        props.put("departDir", "e");
        f.setProperties(props);
        return f;
    }

    @BeforeEach
    void setUp() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("n甲", "甲", 0, 0));
        fc.add(point("n乙", "乙", 10, 0));
        fc.add(point("n丙", "丙", 12, 0));
        fc.add(point("n丁", "丁", 22, 0));
        fc.add(line("e.L1.n甲__n乙", "n甲", "n乙", "L1", 10));
        fc.add(line("e.L2.n丙__n丁", "n丙", "n丁", "L2", 10));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));
    }

    @AfterEach
    void tearDown() {
        StationGroupConfig.replaceAll(Map.of());
    }

    @Test
    void noGroupNoTransfer() {
        StationGroupConfig.replaceAll(Map.of());
        assertTrue(GeoRouteEngine.findTransferJourneys("甲", "丁", 0).isEmpty());
    }

    @Test
    void groupEnablesTransferBetweenDifferentNames() {
        StationGroupConfig.replaceAll(Map.of("乙丙", List.of("乙", "丙")));

        List<JourneyPlan> plans = GeoRouteEngine.findTransferJourneys("甲", "丁", 0);
        assertEquals(1, plans.size());
        JourneyPlan plan = plans.getFirst();
        assertEquals("甲", plan.getStartStationName());
        assertEquals("丁", plan.getEndStationName());
        assertEquals("乙", plan.getLegs().get(0).getEndStationName());
        assertEquals("丙", plan.getLegs().get(1).getStartStationName());
        assertEquals("乙", plan.getTransferStations().getFirst());
        assertEquals(20.0 / 1000, plan.getTotalDistance(), 1e-9);
    }

    @Test
    void groupMembersLookup() {
        StationGroupConfig.replaceAll(Map.of("乙丙", List.of("乙", "丙")));
        assertEquals(List.of("乙", "丙"), StationGroupConfig.groupMembers("丙"));
        assertEquals(List.of("甲"), StationGroupConfig.groupMembers("甲"));
        assertEquals("乙丙", StationGroupConfig.groupOf("乙"));
        assertNull(StationGroupConfig.groupOf("甲"));
    }

    @Test
    void groupDoesNotAffectDirectSearch() {
        StationGroupConfig.replaceAll(Map.of("乙丙", List.of("乙", "丙")));
        // 直达查询不受车站组影响：甲 无法直达 丁
        assertTrue(GeoRouteEngine.findByStation("甲", "丁").isEmpty());
    }
}
