package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.LayerAssigner;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.RailEdge;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LayerAssigner#assignRelative} 单元测试：单线增量遍历时，只对待写区间计算 layer，
 * 把磁盘上其它文件的区间当固定障碍（不改其 layer）。
 */
public class GeoLayerRelativeTest {

    private LngLatAlt p(double x, double z, double y) {
        return new LngLatAlt(x, z, y);
    }

    private RailEdge edge(String from, String to, int layer, List<LngLatAlt> coords) {
        return new RailEdge(from, to, "L", "sys", coords, "#FFFFFF", 1, layer, null, "test_world", null, null);
    }

    @Test
    void newEdgeAboveFixedGetsHigherLayer() {
        // 固定障碍 f 在地面(layer 0, y=64)；新边 n 高架(y=70)从其上方穿过 -> n 至少 layer 1
        RailEdge f = edge("f1", "f2", 0, Arrays.asList(p(5, 0, 64), p(5, 10, 64)));
        RailEdge n = edge("n1", "n2", 0, Arrays.asList(p(0, 5, 70), p(10, 5, 70)));
        LayerAssigner.assignRelative(new ArrayList<>(Collections.singletonList(n)),
                new ArrayList<>(Collections.singletonList(f)));
        assertEquals(1, n.getLayer(), "新边在固定障碍之上，应抬到其 layer+1");
        assertEquals(0, f.getLayer(), "固定障碍 layer 不被改动");
    }

    @Test
    void newEdgeBelowFixedStaysZero() {
        // 新边在固定障碍之下（不产生下压固定边的约束），保持 0
        RailEdge f = edge("f1", "f2", 0, Arrays.asList(p(5, 0, 70), p(5, 10, 70)));
        RailEdge n = edge("n1", "n2", 0, Arrays.asList(p(0, 5, 64), p(10, 5, 64)));
        LayerAssigner.assignRelative(new ArrayList<>(Collections.singletonList(n)),
                new ArrayList<>(Collections.singletonList(f)));
        assertEquals(0, n.getLayer());
        assertEquals(0, f.getLayer());
    }

    @Test
    void respectsExistingFixedLayer() {
        // 固定障碍已在 layer 2；新边从其上方穿过 -> 新边应到 layer 3
        RailEdge f = edge("f1", "f2", 2, Arrays.asList(p(5, 0, 64), p(5, 10, 64)));
        RailEdge n = edge("n1", "n2", 0, Arrays.asList(p(0, 5, 70), p(10, 5, 70)));
        LayerAssigner.assignRelative(new ArrayList<>(Collections.singletonList(n)),
                new ArrayList<>(Collections.singletonList(f)));
        assertEquals(3, n.getLayer(), "应基于固定障碍既有 layer 抬升");
    }

    @Test
    void toSolveEdgesStackAmongThemselves() {
        // 两条待写边之间也要按叠压关系分层：b 在 a 之上 -> a=0, b=1（无固定障碍）
        RailEdge a = edge("a1", "a2", 0, Arrays.asList(p(0, 5, 64), p(10, 5, 64)));
        RailEdge b = edge("b1", "b2", 0, Arrays.asList(p(5, 0, 68), p(5, 10, 68)));
        LayerAssigner.assignRelative(new ArrayList<>(Arrays.asList(a, b)), new ArrayList<>());
        assertEquals(0, a.getLayer());
        assertEquals(1, b.getLayer());
    }

    @Test
    void combinesFixedFloorAndPeerStacking() {
        // a 在固定障碍 f 之上(floor=1)，b 又在 a 之上 -> a=1, b=2
        RailEdge f = edge("f1", "f2", 0, Arrays.asList(p(0, 5, 64), p(10, 5, 64)));
        RailEdge a = edge("a1", "a2", 0, Arrays.asList(p(5, 0, 68), p(5, 10, 68)));
        RailEdge b = edge("b1", "b2", 0, Arrays.asList(p(0, 6, 72), p(10, 6, 72)));
        LayerAssigner.assignRelative(new ArrayList<>(Arrays.asList(a, b)),
                new ArrayList<>(Collections.singletonList(f)));
        assertEquals(1, a.getLayer(), "a 压在固定障碍之上");
        assertEquals(2, b.getLayer(), "b 压在 a 之上，叠加固定下限");
    }
}
