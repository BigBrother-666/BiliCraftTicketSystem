package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TCC 云轨曲线简化：云轨采样点用小容差保留弧线曲率，普通轨用默认容差压平阶梯斜线。
 * <p>
 * 坐标约定与 geojson 一致：经度=x，纬度=z，高度=y。这里在 XZ 平面（经/纬）造点。
 */
public class CoasterSimplifyTest {

    private LngLatAlt p(double x, double z) {
        return new LngLatAlt(x, z, 64);
    }

    /**
     * 一条明显的弧（四分之一圆采样）：用云轨小容差应保留中间点（画成弧），
     * 用普通轨默认容差（0.75）会被压成两端直线。
     */
    @Test
    void coasterToleranceKeepsArcVanillaToleranceFlattens() {
        // 半径 10 的四分之一圆，密采（真实浮点位置模拟）
        List<LngLatAlt> arc = new ArrayList<>();
        for (int deg = 0; deg <= 90; deg += 5) {
            double r = Math.toRadians(deg);
            arc.add(p(10 * Math.cos(r), 10 * Math.sin(r)));
        }

        List<LngLatAlt> coaster = GeoUtils.simplifyLineString(arc, GeoUtils.COASTER_HORIZONTAL_TOLERANCE);
        List<LngLatAlt> vanilla = GeoUtils.simplifyLineString(arc, GeoUtils.HORIZONTAL_TOLERANCE);

        // 弧线在云轨精度下保留多个中间点
        assertTrue(coaster.size() > 5, "云轨精度应保留弧线中间点，实际点数=" + coaster.size());
        // 云轨精度保留的顶点数应明显多于普通轨精度（普通轨把缓弧压平）
        assertTrue(coaster.size() > vanilla.size(),
                "云轨精度应比普通轨精度保留更多弧线点：coaster=" + coaster.size() + " vanilla=" + vanilla.size());
    }

    /**
     * 真实浮点采样的直斜线：无论容差大小，中间共线点都应被删除，只留首末两点（不退回阶梯）。
     */
    @Test
    void straightDiagonalCollapsesUnderBothTolerances() {
        List<LngLatAlt> line = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            line.add(p(i * 0.5, i * 0.5)); // 完全共线的 45° 直线
        }
        assertEquals(2, GeoUtils.simplifyLineString(line, GeoUtils.COASTER_HORIZONTAL_TOLERANCE).size(),
                "共线直斜线在云轨精度下应压成两点");
        assertEquals(2, GeoUtils.simplifyLineString(line, GeoUtils.HORIZONTAL_TOLERANCE).size(),
                "共线直斜线在普通轨精度下应压成两点");
    }

    /**
     * 整数坐标的阶梯状斜线（原版斜轨形态）：默认容差（0.75）应压回一条直线（两点）。
     * 这是普通轨历史行为，必须保持不变。
     */
    @Test
    void vanillaStaircaseStillCompresses() {
        // 走一格 x、再走一格 z 的阶梯逼近 45° 斜线
        List<LngLatAlt> stair = new ArrayList<>();
        int x = 0, z = 0;
        stair.add(p(x, z));
        for (int i = 0; i < 10; i++) {
            stair.add(p(++x, z));
            stair.add(p(x, ++z));
        }
        List<LngLatAlt> simplified = GeoUtils.simplifyLineString(stair, GeoUtils.HORIZONTAL_TOLERANCE);
        assertEquals(2, simplified.size(), "阶梯斜线应被默认容差压成两点，实际=" + simplified.size());
    }

    /**
     * 默认无参重载应等价于普通轨默认容差（不影响既有调用方）。
     */
    @Test
    void defaultOverloadMatchesVanillaTolerance() {
        List<LngLatAlt> stair = new ArrayList<>();
        int x = 0, z = 0;
        stair.add(p(x, z));
        for (int i = 0; i < 8; i++) {
            stair.add(p(++x, z));
            stair.add(p(x, ++z));
        }
        assertEquals(
                GeoUtils.simplifyLineString(stair, GeoUtils.HORIZONTAL_TOLERANCE).size(),
                GeoUtils.simplifyLineString(stair).size(),
                "无参重载应与默认容差一致");
    }
}
