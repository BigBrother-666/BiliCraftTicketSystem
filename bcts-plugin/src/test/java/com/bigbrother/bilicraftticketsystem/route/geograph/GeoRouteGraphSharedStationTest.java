package com.bigbrother.bilicraftticketsystem.route.geograph;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 共线车站的进站道岔判定测试。
 * <p>
 * 两条线共用一个车站时，进站道岔上同时挂着两条线各自的正线与停靠线出边。旧实现要求「全部出边同属
 * 一条线」才算进站道岔，共线车站因此永远认不出（前端高亮线路时正线不隐藏、寻路车站去重失效）。
 */
class GeoRouteGraphSharedStationTest {

    /**
     * 构造一条有向边（不带门控面）。
     */
    private static GeoLink link(String from, String to, String lineId, String departDir) {
        return new GeoLink("e." + lineId + "." + from + "__" + to, from, to, lineId, 5, "#fff",
                departDir, Set.of(), null);
    }

    /**
     * 构造「进站道岔 in → 车站 P / 出站道岔 out」的共线车站结构，两条线 L1、L2 共用。
     */
    private static GeoRouteGraph sharedStationGraph() {
        GeoRouteGraph g = new GeoRouteGraph();
        g.addNode(new GeoNode("in", "switch", null, 0, 64, 0));
        g.addNode(new GeoNode("platform", "station", "P", 10, 64, 10));
        g.addNode(new GeoNode("out", "switch", null, 20, 64, 0));
        for (String lineId : new String[]{"L1", "L2"}) {
            g.addLink(link("in", "platform", lineId, "s"));
            g.addLink(link("in", "out", lineId, "e"));
        }
        return g;
    }

    /**
     * 共线车站的进站道岔对两条线都应识别出停靠线车站。
     */
    @Test
    void sharedStationRecognizedForEachLine() {
        GeoRouteGraph g = sharedStationGraph();
        GeoNode in = g.getNode("in");
        assertEquals("P", g.platformNameOfMainlineSwitch(in, "L1"));
        assertEquals("P", g.platformNameOfMainlineSwitch(in, "L2"));
    }

    /**
     * 不经过该道岔的线路不应被误判为进站道岔。
     */
    @Test
    void unrelatedLineIsNotEnterSwitcher() {
        GeoRouteGraph g = sharedStationGraph();
        assertNull(g.platformNameOfMainlineSwitch(g.getNode("in"), "L3"));
    }

    /**
     * 两线分叉道岔（每条线在此只有一条出边、通往不同方向）不是进站道岔。
     */
    @Test
    void divergingSwitchIsNotEnterSwitcher() {
        GeoRouteGraph g = new GeoRouteGraph();
        g.addNode(new GeoNode("fork", "switch", null, 0, 64, 0));
        g.addNode(new GeoNode("platform", "station", "P", 10, 64, 10));
        g.addNode(new GeoNode("next", "switch", null, 20, 64, 0));
        g.addLink(link("fork", "platform", "L1", "s"));
        g.addLink(link("fork", "next", "L2", "e"));
        assertNull(g.platformNameOfMainlineSwitch(g.getNode("fork"), "L1"));
        assertNull(g.platformNameOfMainlineSwitch(g.getNode("fork"), "L2"));
    }
}
