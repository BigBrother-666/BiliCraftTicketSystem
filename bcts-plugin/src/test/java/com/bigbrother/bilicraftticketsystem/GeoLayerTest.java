package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.LayerAssigner;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.RailEdge;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * task3 两处修复的纯逻辑测试：
 * <ul>
 *   <li>{@link GeoUtils#simplifyLineString} 保留高度变化拐点（不再压平）。</li>
 *   <li>{@link LayerAssigner} 按 XZ 平面空间交叉 + 高度给区间分层。</li>
 * </ul>
 */
public class GeoLayerTest {

    private LngLatAlt p(double x, double z, double y) {
        return new LngLatAlt(x, z, y);
    }

    private RailEdge edge(String from, String to, List<LngLatAlt> coords) {
        return new RailEdge(from, to, "L", "sys", coords, "#FFFFFF", 1, 0, null, "test_world");
    }

    @Test
    void simplifyKeepsHeightChangePoints() {
        // XZ 平面是一条直线（z 恒为 0），高度非匀速变化（64,65,65,66）——
        // 旧的二维共线判定会把两个中间点全丢弃压平高度，三维判定必须保留
        List<LngLatAlt> coords = new ArrayList<>(Arrays.asList(
                p(0, 0, 64), p(1, 0, 65), p(2, 0, 65), p(3, 0, 66)));
        List<LngLatAlt> out = GeoUtils.simplifyLineString(coords);
        assertEquals(4, out.size(), "高度非匀速变化时拐点都应保留");
        assertEquals(64, out.get(0).getAltitude());
        assertEquals(66, out.get(3).getAltitude());
    }

    @Test
    void simplifyDropsTrue3dCollinearMidpoint() {
        // 三维严格共线（XZ 直线 + 高度恒定）：中间点可丢
        List<LngLatAlt> flat = new ArrayList<>(Arrays.asList(
                p(0, 0, 64), p(1, 0, 64), p(2, 0, 64)));
        assertEquals(2, GeoUtils.simplifyLineString(flat).size());

        // 平坡均匀爬升也三维共线：中间点可丢，但首末高度保留
        List<LngLatAlt> ramp = new ArrayList<>(Arrays.asList(
                p(0, 0, 64), p(2, 0, 66), p(4, 0, 68)));
        List<LngLatAlt> out = GeoUtils.simplifyLineString(ramp);
        assertEquals(2, out.size());
        assertEquals(64, out.get(0).getAltitude());
        assertEquals(68, out.get(1).getAltitude());
    }

    @Test
    void simplifyKeepsReversalPoint() {
        // 原路折返：XZ 共线但方向相反，拐点必须保留
        List<LngLatAlt> coords = new ArrayList<>(Arrays.asList(
                p(0, 0, 64), p(2, 0, 64), p(1, 0, 64)));
        assertEquals(3, GeoUtils.simplifyLineString(coords).size());
    }

    @Test
    void simplifyCollapsesStaircaseToDiagonal() {
        // 游戏内 45° 斜线是阶梯状方块：交替走一格 x、一格 z，高度恒定。
        // 应被压回成一条直斜线（仅首末两点）。
        List<LngLatAlt> stair = new ArrayList<>(Arrays.asList(
                p(15178, -12882, 100), p(15178, -12881, 100), p(15177, -12881, 100),
                p(15177, -12880, 100), p(15176, -12880, 100), p(15176, -12879, 100),
                p(15175, -12879, 100), p(15175, -12878, 100)));
        List<LngLatAlt> out = GeoUtils.simplifyLineString(stair);
        assertEquals(2, out.size(), "整段阶梯应压成一条斜线");
        assertEquals(15178, out.getFirst().getLongitude());
        assertEquals(-12882, out.getFirst().getLatitude());
        assertEquals(15175, out.getLast().getLongitude());
        assertEquals(-12878, out.getLast().getLatitude());
    }

    @Test
    void simplifyKeepsStaircaseWithRealHeightClimb() {
        // 水平阶梯 + 真实爬坡：高度逐格上升，高度拐点不能被压平
        List<LngLatAlt> stair = new ArrayList<>(Arrays.asList(
                p(0, 0, 64), p(0, 1, 64), p(1, 1, 65), p(1, 2, 65), p(2, 2, 66)));
        List<LngLatAlt> out = GeoUtils.simplifyLineString(stair);
        assertEquals(64, out.getFirst().getAltitude());
        assertEquals(66, out.getLast().getAltitude());
        // 高度非匀速变化，中间至少保留一个高度拐点
        assertTrue(out.size() > 2, "真实高度变化不应被压平");
    }

    @Test
    void crossingHigherEdgeGetsHigherLayer() {
        // a 东西走向、高度 70（高架）；b 南北走向、高度 64（地面），二者在 (5,?) 交叉
        RailEdge a = edge("a1", "a2", Arrays.asList(p(0, 5, 70), p(10, 5, 70)));
        RailEdge b = edge("b1", "b2", Arrays.asList(p(5, 0, 64), p(5, 10, 64)));
        LayerAssigner.assign(new ArrayList<>(Arrays.asList(a, b)));
        assertEquals(1, a.getLayer(), "高架应在更高 layer");
        assertEquals(0, b.getLayer(), "地面线保持 layer 0");
    }

    @Test
    void noCrossingKeepsLayerZero() {
        // 两条平行不相交的线，layer 都应为 0（尽量少用层）
        RailEdge a = edge("a1", "a2", Arrays.asList(p(0, 0, 64), p(10, 0, 64)));
        RailEdge b = edge("b1", "b2", Arrays.asList(p(0, 5, 70), p(10, 5, 70)));
        LayerAssigner.assign(new ArrayList<>(Arrays.asList(a, b)));
        assertEquals(0, a.getLayer());
        assertEquals(0, b.getLayer());
    }

    @Test
    void sharedNodeIsNotOverpass() {
        // 两段在端点相接（同坐标同高度），是平面接续不是空间交叉，不应抬层
        RailEdge a = edge("n", "a2", Arrays.asList(p(0, 0, 64), p(5, 0, 64)));
        RailEdge b = edge("n", "b2", Arrays.asList(p(0, 0, 64), p(0, 5, 64)));
        LayerAssigner.assign(new ArrayList<>(Arrays.asList(a, b)));
        assertEquals(0, a.getLayer());
        assertEquals(0, b.getLayer());
    }

    @Test
    void stackedCrossingsUseMinimalLayers() {
        // 三层叠压：c 在 b 之上、b 在 a 之上，应得 0/1/2
        RailEdge a = edge("a1", "a2", Arrays.asList(p(0, 5, 64), p(10, 5, 64)));
        RailEdge b = edge("b1", "b2", Arrays.asList(p(5, 0, 68), p(5, 10, 68)));
        RailEdge c = edge("c1", "c2", Arrays.asList(p(0, 6, 72), p(10, 6, 72)));
        // b 与 a 交叉(b高)，c 与 b 交叉(c高)
        LayerAssigner.assign(new ArrayList<>(Arrays.asList(a, b, c)));
        assertEquals(0, a.getLayer());
        assertEquals(1, b.getLayer());
        assertEquals(2, c.getLayer());
    }
}
