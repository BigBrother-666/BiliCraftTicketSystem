package com.bigbrother.bilicraftticketsystem.utils;

import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import org.bukkit.Material;
import org.geojson.LngLatAlt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GeoUtils {
    /**
     * 水平方向（XZ 平面）简化容差，单位为 mc 方块。
     * <p>
     * 游戏内的斜线是用阶梯状的方块逼近的（走一格 x 再走一格 z），相邻方块离理想斜线最多约半格。
     * 该容差大于半格即可把整段阶梯压成一条直斜线；同时小于 1，保证 1 格以上的真实折返 / 拐弯被保留。
     */
    private static final double HORIZONTAL_TOLERANCE = 0.75;
    /**
     * 高度方向（mc 的 y）简化容差，单位为 mc 方块。
     * <p>
     * 远小于一格，任何真实的爬坡 / 下坡拐点都会被保留，只吸收浮点噪声，不会把不同高度压平。
     */
    private static final double ALTITUDE_TOLERANCE = 0.1;

    /**
     * 解析 bcswitcher 控制牌的一行出向声明 {@code <方向>@<线路id>;[线路id]...}，返回道岔分支。
     * <p>
     * {@code @} 后可用分号分隔多个线路 id，表示该出向轨道被多条线路共用
     * （如 {@code r@pr-cw;pr-s1}）。
     *
     * @param line bcswitcher 控制牌的一行（第三或第四行）
     * @return 解析出的道岔分支；该行为空、缺少 '@' 或无有效线路 id 时返回 null
     */
    public static BcSwitcherBranch parseBcSwitcherBranch(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        int idx = trimmed.indexOf('@');
        if (idx <= 0 || idx == trimmed.length() - 1) {
            return null;
        }
        String directionStr = trimmed.substring(0, idx).trim();
        String lineIdsPart = trimmed.substring(idx + 1).trim();
        if (directionStr.isEmpty() || lineIdsPart.isEmpty()) {
            return null;
        }
        List<String> lineIds = new ArrayList<>();
        for (String part : lineIdsPart.split(";")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                lineIds.add(id);
            }
        }
        if (lineIds.isEmpty()) {
            return null;
        }
        return new BcSwitcherBranch(directionStr, lineIds);
    }

    /**
     * 简化折线：先去除相邻重复点，再用<b>各向异性的 Ramer–Douglas–Peucker</b> 算法抽稀顶点。
     * <p>
     * 游戏内的斜线/曲线实际是由方块拼出的阶梯状折线（如先走一格 x、再走一格 z），
     * 若按真实顶点输出会产生大量 90° 拐点。本方法把这种阶梯压回成一条平滑斜线，
     * 显著降低 LineString 的顶点数。
     * <p>
     * 由于阶梯只发生在<b>水平</b>方向、而高度（爬坡 / 下坡）必须如实保留，
     * 这里对水平与高度采用不同容差：水平 {@link #HORIZONTAL_TOLERANCE} 大于半格以吸收阶梯，
     * 高度 {@link #ALTITUDE_TOLERANCE} 远小于一格以保住任何真实的高度拐点。
     * <p>
     * 距离按“点到<b>线段</b>”计算（而非到无限直线），因此原路折返的折返点离线段两端足够远，
     * 一定会被保留。
     */
    public static List<LngLatAlt> simplifyLineString(List<LngLatAlt> coords) {
        if (coords.size() <= 2) return coords;

        // 先去掉连续重复点，避免零长线段干扰距离计算
        List<LngLatAlt> dedup = new ArrayList<>(coords.size());
        dedup.add(coords.getFirst());
        for (int i = 1; i < coords.size(); i++) {
            if (!isSamePoint(dedup.getLast(), coords.get(i))) {
                dedup.add(coords.get(i));
            }
        }
        if (dedup.size() <= 2) return dedup;

        // RDP：标记保留哪些顶点。用显式栈避免递归过深（铁路线段可能很长）。
        boolean[] keep = new boolean[dedup.size()];
        keep[0] = true;
        keep[dedup.size() - 1] = true;

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, dedup.size() - 1});
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int start = seg[0];
            int end = seg[1];
            if (end - start < 2) continue; // 中间没有点可丢

            LngLatAlt a = dedup.get(start);
            LngLatAlt b = dedup.get(end);

            // 找到对“起止线段”归一化偏差最大的中间点
            int splitIdx = -1;
            double maxRatio = 0;
            for (int i = start + 1; i < end; i++) {
                double ratio = normalizedDeviation(dedup.get(i), a, b);
                if (ratio > maxRatio) {
                    maxRatio = ratio;
                    splitIdx = i;
                }
            }

            // 偏差超过容差（归一化后 > 1）则必须保留该点，并对两侧子段递归
            if (splitIdx != -1 && maxRatio > 1.0) {
                keep[splitIdx] = true;
                stack.push(new int[]{start, splitIdx});
                stack.push(new int[]{splitIdx, end});
            }
        }

        List<LngLatAlt> result = new ArrayList<>();
        for (int i = 0; i < dedup.size(); i++) {
            if (keep[i]) {
                result.add(dedup.get(i));
            }
        }
        return result;
    }

    /** 判断两点是否完全相同 */
    private static boolean isSamePoint(LngLatAlt a, LngLatAlt b) {
        return Double.compare(a.getLongitude(), b.getLongitude()) == 0 &&
                Double.compare(a.getLatitude(), b.getLatitude()) == 0 &&
                Double.compare(a.getAltitude(), b.getAltitude()) == 0;
    }

    /**
     * 计算点 {@code p} 到线段 {@code a-b} 的<b>各向异性归一化</b>偏差。
     * <p>
     * x = longitude（mc 的 x）、y = latitude（mc 的 z）为水平面，z = altitude（mc 的 y）为高度。
     * 先把 p 投影到线段（投影落在端点外则取最近端点），得到水平偏差与高度偏差，
     * 再分别除以 {@link #HORIZONTAL_TOLERANCE} 与 {@link #ALTITUDE_TOLERANCE} 归一化，取较大者。
     * 返回值 &gt; 1 表示在某一方向上超出了容差、该点不可丢弃。
     */
    private static double normalizedDeviation(LngLatAlt p, LngLatAlt a, LngLatAlt b) {
        double ax = a.getLongitude(), ay = a.getLatitude(), az = a.getAltitude();
        double bx = b.getLongitude(), by = b.getLatitude(), bz = b.getAltitude();
        double px = p.getLongitude(), py = p.getLatitude(), pz = p.getAltitude();

        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double lenSq = dx * dx + dy * dy + dz * dz;

        // 把 p 投影到线段 a-b，clamp 到 [0,1] 保证用的是到“线段”而非无限直线的距离
        double t = lenSq == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / lenSq;
        if (t < 0) t = 0;
        else if (t > 1) t = 1;

        // 投影点坐标
        double cx = ax + t * dx, cy = ay + t * dy, cz = az + t * dz;

        // 水平偏差（XZ 平面）与高度偏差分开衡量
        double horiz = Math.hypot(px - cx, py - cy);
        double alt = Math.abs(pz - cz);

        return Math.max(horiz / HORIZONTAL_TOLERANCE, alt / ALTITUDE_TOLERANCE);
    }

    public static boolean isRail(Material type) {
        return type == Material.RAIL
                || type == Material.POWERED_RAIL
                || type == Material.DETECTOR_RAIL
                || type == Material.ACTIVATOR_RAIL;
    }
}
