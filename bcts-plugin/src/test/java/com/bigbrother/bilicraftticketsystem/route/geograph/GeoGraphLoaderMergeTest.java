package com.bigbrother.bilicraftticketsystem.route.geograph;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「同 id 边跨单元合并」的建图单测。
 * <p>
 * 按遍历单元分文件后，同一条边可能被<b>两个单元</b>各产出一份（共用轨道 / 越界边），各自只记下
 * 「自己那一侧能到达的面」。建图时必须按 edge id 合并、{@code enterFrom} 取并集：
 * <ul>
 *   <li>不去重 → 邻接表里出现重复出边，寻路会算出重复路线；</li>
 *   <li>只取其中一份 → 门控面残缺，另一方向来车被 {@code enterFaceAllows} 拒绝，
 *       表现为「汇入本线的线路断开」。</li>
 * </ul>
 */
class GeoGraphLoaderMergeTest {

    private static Feature point(String id, String type, String name, double x, double y, double z) {
        Feature f = new Feature();
        f.setGeometry(new Point(new LngLatAlt(x, z, y)));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", id);
        props.put("type", type);
        if (name != null) {
            props.put("name", name);
        }
        f.setProperties(props);
        return f;
    }

    private static Feature line(String id, String from, String to, String lineId, List<String> enterFrom,
                                String enterTo) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(10, 0, 64)));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", id);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        props.put("length", 10.0);
        props.put("departDir", "e");
        props.put("enterFrom", enterFrom);
        props.put("enterTo", enterTo);
        f.setProperties(props);
        return f;
    }

    /**
     * 同一条边由两个单元各产出一份、各带一个不同到达面：建图后应只有一条出边，到达面为两者并集。
     */
    @Test
    void sameEdgeFromTwoUnitsMergesEnterFaces() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nSwitch", "switch", null, 0, 64, 0));
        fc.add(point("nB", "station", "B", 10, 64, 0));
        // L1 单元走出这条边时，只到达得了 "1_0" 面
        fc.add(line("e.L1.nSwitch__nB", "nSwitch", "nB", "L1", List.of("1_0"), "1_0"));
        // L2 单元经联络线汇入同一道岔后走出同一条边，贡献 "0_1" 面
        fc.add(line("e.L1.nSwitch__nB", "nSwitch", "nB", "L1", List.of("0_1"), "1_0"));

        GeoRouteGraph graph = new GeoGraphLoader(null).loadFeatureCollection(fc);

        List<GeoLink> out = graph.links("nSwitch");
        assertEquals(1, out.size(), "同 id 边不应在邻接表里重复出现");
        GeoLink link = out.getFirst();
        assertEquals(2, link.getEnterFacesFrom().size(), "两个单元贡献的到达面都应保留");
        assertTrue(link.getEnterFacesFrom().containsAll(List.of("1_0", "0_1")),
                "到达面须为并集，否则一侧来车会被门控拒绝");
        assertEquals(1, graph.linkCount());
    }

    /**
     * 物理重合但 lineId 不同的边（共用轨道）id 本就不同，必须各自保留为独立出边——
     * 它们是路由图里的两条边（各属一条线），只是物理上同一段轨道。
     */
    @Test
    void differentLineIdsOnSharedTrackStaySeparate() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "switch", null, 0, 64, 0));
        fc.add(point("nB", "station", "B", 10, 64, 0));
        fc.add(line("e.L1.nA__nB", "nA", "nB", "L1", List.of("1_0"), "1_0"));
        fc.add(line("e.L2.nA__nB", "nA", "nB", "L2", List.of("1_0"), "1_0"));

        GeoRouteGraph graph = new GeoGraphLoader(null).loadFeatureCollection(fc);

        assertEquals(2, graph.links("nA").size(), "共用轨道的两条线各占一条边");
        assertEquals(2, graph.linkCount());
    }

    /**
     * {@link GeoRouteGraph#getLink(String)} 能按 id 取回边，供建图判重。
     */
    @Test
    void linkIsRetrievableById() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("nA", "switch", null, 0, 64, 0));
        fc.add(point("nB", "station", "B", 10, 64, 0));
        fc.add(line("e.L1.nA__nB", "nA", "nB", "L1", List.of("1_0"), "1_0"));

        GeoRouteGraph graph = new GeoGraphLoader(null).loadFeatureCollection(fc);

        GeoLink link = graph.getLink("e.L1.nA__nB");
        assertNotNull(link);
        assertEquals("nA", link.getFromNodeId());
        assertEquals("nB", link.getToNodeId());
    }
}
