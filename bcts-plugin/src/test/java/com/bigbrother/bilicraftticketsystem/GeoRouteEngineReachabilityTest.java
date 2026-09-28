package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
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
 * {@code GeoRouteEngine} 新增的「不可达快速短路」+「计算耗时上限」纯逻辑单测。
 * <p>
 * 只通过公开方法（{@link GeoRouteEngine#findByStation(String, String, int, long)} /
 * {@link GeoRouteEngine#findFromNode}）验证行为，与 {@link GeoRouteEngineTest} 同一套测试风格
 * （不反射访问私有的 {@code reachableIgnoringRevisit}），复用其中部分 fixture 构造 helper。
 */
public class GeoRouteEngineReachabilityTest {

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

    private Feature line(String id, String from, String to, String lineId, double length, String departDir,
                         List<String> enterFrom, String enterTo) {
        Feature f = line(id, from, to, lineId, length, departDir);
        Map<String, Object> props = f.getProperties();
        if (enterFrom != null) {
            props.put("enterFrom", enterFrom);
        }
        if (enterTo != null) {
            props.put("enterTo", enterTo);
        }
        return f;
    }

    @Test
    void deadlineZeroIsUnlimitedAndMatchesLegacyOverload() {
        // 复刻 GeoRouteEngineTest.shortestPathPicksCheapestPlatform 的图：deadlineNanos=0（不限时）
        // 走的重载应与旧的三参数重载结果完全一致（新增重载只是委派，不改变任何行为）。
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "station", "A", 0, 64, 0));
        fc.add(point("nA2", "station", "A", 0, 64, 50));
        fc.add(point("s1", "switch", null, 10, 64, 0));
        fc.add(point("s2", "switch", null, 20, 64, 0));
        fc.add(point("nB", "station", "B", 30, 64, 0));
        fc.add(line("e.L1.nA__s1", "nA", "s1", "L1", 10, null));
        fc.add(line("e.contact.s1__s2", "s1", "s2", "contact", 5, "e"));
        fc.add(line("e.L1.s2__nB", "s2", "nB", "L1", 10, "n"));
        fc.add(line("e.L1.nA2__s2", "nA2", "s2", "L1", 1, null));
        fc.add(line("e.L2.nA__nB", "nA", "nB", "L2", 100, null));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));

        List<GeoRoutePath> legacy = GeoRouteEngine.findByStation("A", "B", 0);
        List<GeoRoutePath> withDeadline = GeoRouteEngine.findByStation("A", "B", 0, 0L);
        assertEquals(legacy.size(), withDeadline.size());
        for (int i = 0; i < legacy.size(); i++) {
            assertEquals(legacy.get(i).getDistance(), withDeadline.get(i).getDistance(), 1e-9);
            assertEquals(legacy.get(i).getNodes().stream().map(n -> n.getId()).toList(),
                    withDeadline.get(i).getNodes().stream().map(n -> n.getId()).toList());
        }
    }

    @Test
    void unreachableWhenEnterFaceGatingBlocksAllPaths() {
        // 复刻 GeoRouteEngineTest.enterFaceGatesOutEdgeAtSharedSwitchNode 的图：右来直行到 F 应被
        // 门控拦截——快速短路命中后 findByStation 应直接返回空，而不是穷举到 KSP_MAX_POPS。
        FeatureCollection f = new FeatureCollection();
        f.add(point("nL", "station", "L", 0, 64, 0));
        f.add(point("nR", "station", "R", 0, 64, 20));
        f.add(point("sw", "switch", null, 10, 64, 10));
        f.add(point("nF", "station", "F", 20, 64, 10));
        f.add(point("nS", "station", "S", 10, 64, 30));
        f.add(line("e.LN.nL__sw", "nL", "sw", "LN", 10, "e", null, "1_0"));
        f.add(line("e.RN.nR__sw", "nR", "sw", "RN", 10, "e", null, "-1_0"));
        f.add(line("e.LN.sw__nF", "sw", "nF", "LN", 10, "e", List.of("1_0"), "1_0"));
        f.add(line("e.RN.sw__nS", "sw", "nS", "RN", 10, "s", List.of("-1_0"), "0_1"));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(f));

        assertTrue(GeoRouteEngine.findByStation("R", "F", 0, 0L).isEmpty(),
                "右来直行到 F 应被入向面门控拦截（快速短路应判定不可达）");
        assertFalse(GeoRouteEngine.findByStation("R", "S", 0, 0L).isEmpty(),
                "右来转到 S 合法，不应被短路误判为不可达");
        assertFalse(GeoRouteEngine.findByStation("L", "F", 0, 0L).isEmpty(),
                "左来直行到 F 合法，不应被短路误判为不可达");
    }

    @Test
    void unreachableWhenStationsAreCompletelyDisconnected() {
        FeatureCollection f = new FeatureCollection();
        f.add(point("nA", "station", "A", 0, 64, 0));
        f.add(point("nB", "station", "B", 1000, 64, 1000)); // 无任何边连接
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(f));

        assertTrue(GeoRouteEngine.findByStation("A", "B", 0, 0L).isEmpty());
    }

    @Test
    void standingStillAtStartIsNotArrivalWhenNoOutgoingEdges() {
        // 单节点：起点站台没有任何出边。查询「A -> A」不应因为"起点本身就是终点"而误判为可达，
        // 必须走过至少一条边才算命中（与 kShortest 现有的 cur.prevLink()!=null 语义一致）。
        FeatureCollection f = new FeatureCollection();
        f.add(point("nA", "station", "A", 0, 64, 0));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(f));

        assertNull(GeoRouteEngine.findFromNode("nA", "A"));
        assertTrue(GeoRouteEngine.findByStation("A", "A", 0, 0L).isEmpty());
    }

    @Test
    void loopBackToSameNamedStationCountsAsReachable() {
        // 复刻 GeoRouteEngineTest.sameStationLoopsBackOnRingLine 的环线图：应判定可达（走过至少一条
        // 边后绕回同名站），不应被短路误判为不可达。
        FeatureCollection f = new FeatureCollection();
        f.add(point("nR", "station", "R", 0, 64, 0));
        f.add(point("sx", "switch", null, 10, 64, 0));
        f.add(point("nR2", "station", "R", 20, 64, 0));
        f.add(line("e.R.nR__sx", "nR", "sx", "R", 10, "e"));
        f.add(line("e.R.sx__nR2", "sx", "nR2", "R", 10, "e"));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(f));

        assertNotNull(GeoRouteEngine.findFromNode("nR", "R"), "环线绕回同名站应可达");
        assertFalse(GeoRouteEngine.findByStation("R", "R", 0, 0L).isEmpty());
    }

    @Test
    void expiredDeadlineDoesNotCrashAndReturnsUsableResult() {
        // 复刻 GeoRouteEngineTest 的基础 fixture（可达），传一个已经过期的 deadline：
        // 验证不抛异常、不死循环，返回一个非 null 的列表（内容可能为空——这个测试的重点是行为健壮，
        // 不是精确断言必然截断在哪一步；真实截断效果更适合在服务器上用大图手动验证）。
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "station", "A", 0, 64, 0));
        fc.add(point("nB", "station", "B", 30, 64, 0));
        fc.add(line("e.L1.nA__nB", "nA", "nB", "L1", 10, "e"));
        GeoRouteEngine.setGraph(new GeoGraphLoader(null).loadFeatureCollection(fc));

        long expiredDeadline = System.nanoTime() - 1_000_000_000L; // 已经过期 1 秒
        assertDoesNotThrow(() -> {
            List<GeoRoutePath> results = GeoRouteEngine.findByStation("A", "B", 0, expiredDeadline);
            assertNotNull(results);
        });
    }
}
