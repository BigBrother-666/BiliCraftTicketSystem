package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GeoRouteEngine#findTransferJourneys} 的纯逻辑单测。
 * <p>
 * 复现用户场景：L1 从 A 绕远到 E，L2 从 B 短直达 E，A→B(L1) 换 B→E(L2) 更近。
 * B 的 L1 站台与 L2 站台是<b>两个独立节点、无连接边</b>（步行换乘不是图上的边），
 * 因此单趟直达（{@code findByStation}）走不通，只能靠换乘方案覆盖。
 * <pre>
 *   L1: nA --L1,10--> nB1(B) --L1,90--> nE1(E)     （A 直达 E 走 L1 = 100，很绕）
 *   L2: nB2(B) --L2,10--> nE2(E)                    （B 直达 E 走 L2 = 10）
 * </pre>
 * A→E 直达最短 = 100（L1）；换乘 A→B(10) + B→E(10) = 20，更近。
 */
public class TransferJourneyTest {

    private Feature point(String id, String type, String name, double x, double y, double z) {
        Feature f = new Feature();
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

    private Feature line(String id, String from, String to, String lineId, double length, String departDir) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(1, 0, 64)));
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", length);
        props.put("departDir", departDir);
        f.setProperties(props);
        return f;
    }

    private FeatureCollection scenario() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "station", "A", 0, 64, 0));
        fc.add(point("nB1", "station", "B", 10, 64, 0));   // B 的 L1 站台
        fc.add(point("nE1", "station", "E", 100, 64, 0));  // E 的 L1 站台
        fc.add(point("nB2", "station", "B", 10, 64, 20));  // B 的 L2 站台（独立节点）
        fc.add(point("nE2", "station", "E", 100, 64, 20)); // E 的 L2 站台

        fc.add(line("e.L1.nA__nB1", "nA", "nB1", "L1", 10, "e"));
        fc.add(line("e.L1.nB1__nE1", "nB1", "nE1", "L1", 90, "e"));
        fc.add(line("e.L2.nB2__nE2", "nB2", "nE2", "L2", 10, "s"));
        return fc;
    }

    @Test
    void findsTransferAtBWhenCheaperThanDirect() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(scenario()));

        // 直达最短 A→E = 100（L1 绕远）
        List<GeoRoutePath> direct = GeoRouteEngine.findByStation("A", "E");
        assertFalse(direct.isEmpty());
        assertEquals(100.0 / 1000, direct.getFirst().getDistance(), 1e-9);

        // 换乘方案：A→B(L1,10) 换 B→E(L2,10)，总 20 < 100
        List<JourneyPlan> plans = GeoRouteEngine.findTransferJourneys("A", "E", 0);
        assertFalse(plans.isEmpty(), "应找到经 B 的换乘方案");
        JourneyPlan best = plans.getFirst();
        assertEquals("B", best.getTransferStations().getFirst());
        assertEquals(2, best.legCount());
        assertEquals(20.0 / 1000, best.getTotalDistance(), 1e-9);
        assertEquals("A", best.getStartStationName());
        assertEquals("E", best.getEndStationName());
        // 段 1 走 L1、段 2 走 L2
        assertEquals("L1", best.getLegs().get(0).getStartLineId());
        assertEquals("L2", best.getLegs().get(1).getStartLineId());
        assertTrue(best.getTotalDistance() < direct.getFirst().getDistance(), "换乘应比直达更近");
    }

    @Test
    void noTransferWhenDirectAlreadyShortest() {
        // 只有 L1：A→B→E 全在一条线上，直达即最优，不该产生换乘噪音
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "station", "A", 0, 64, 0));
        fc.add(point("nB1", "station", "B", 10, 64, 0));
        fc.add(point("nE1", "station", "E", 20, 64, 0));
        fc.add(line("e.L1.nA__nB1", "nA", "nB1", "L1", 10, "e"));
        fc.add(line("e.L1.nB1__nE1", "nB1", "nE1", "L1", 10, "e"));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));

        // A→B(10) + B→E(10) = 20 == 直达 20，不严格更小 → 不产生方案
        List<JourneyPlan> plans = GeoRouteEngine.findTransferJourneys("A", "E", 0);
        assertTrue(plans.isEmpty(), "直达已最优时不应产生换乘方案");
    }

    @Test
    void sameStartEndReturnsEmpty() {
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(scenario()));
        assertTrue(GeoRouteEngine.findTransferJourneys("A", "A", 0).isEmpty());
    }

    @Test
    void minImprovementFiltersMarginalTransfer() {
        // 场景 scenario()：直达 A→E=100（L1），换乘 A→B(10)+B→E(10)=20，省 80%
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(scenario()));

        // 门槛 20%：换乘省 80% ≫ 20%，应显示
        assertFalse(GeoRouteEngine.findTransferJourneys("A", "E", 3, 0.2).isEmpty(),
                "省 80% 远超 20% 门槛，应显示");

        // 门槛 90%：换乘省 80% < 90%，被过滤
        assertTrue(GeoRouteEngine.findTransferJourneys("A", "E", 3, 0.9).isEmpty(),
                "省 80% 不足 90% 门槛，应过滤");
    }

    @Test
    void condensedMatrixFindsTransferStation() {
        // 缩合距离矩阵枚举全部换乘站：换乘点 B 经站名级预筛后应被实体化，即便只要 1 条结果也能算出方案。
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(scenario()));
        List<JourneyPlan> plans = GeoRouteEngine.findTransferJourneys("A", "E", 1);
        assertFalse(plans.isEmpty(), "站名级缩合矩阵应枚举到换乘站 B 并实体化出方案");
        assertEquals("B", plans.getFirst().getTransferStations().getFirst());
    }
}
