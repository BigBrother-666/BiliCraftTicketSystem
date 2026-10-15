package com.bigbrother.bilicraftticketsystem.route.geograph;

import lombok.Getter;

/**
 * 一条边（{@link GeoLink}）的折线几何：geojson LineString 的坐标（按 from→to 排列），用于把列车实时位置
 * 投影到轨道上、求沿线已行驶比例（bossbar 实时进度）。
 * <p>
 * 坐标以扁平 {@code double[]}（x,y,z 交替）存储以节省内存，并预计算每个顶点的累计折线长度。
 * 纯数据 / 纯计算，不依赖 Bukkit。
 */
public class GeoPolyline {
    /**
     * 局部搜索窗口（线段数）：有命中提示时只在提示附近搜索，偏离过远再退化为全量扫描。
     */
    private static final int HINT_WINDOW = 16;
    /**
     * 局部搜索结果距离平方超过此值（格²）视为提示失效，退化为全量扫描。
     */
    private static final double HINT_MAX_DIST_SQ = 9.0;

    /**
     * 扁平坐标 {@code [x0,y0,z0,x1,y1,z1,...]}。
     */
    private final double[] coords;
    /**
     * 第 i 个顶点到首顶点的累计折线长度（size = 顶点数）。
     */
    private final double[] cumLen;
    /**
     * 折线总长度。
     */
    @Getter
    private final double length;

    /**
     * @param coords 扁平坐标 {@code [x0,y0,z0,...]}，长度须为 3 的倍数且至少 2 个顶点
     */
    public GeoPolyline(double[] coords) {
        if (coords == null || coords.length < 6 || coords.length % 3 != 0) {
            throw new IllegalArgumentException("polyline 至少需要 2 个三维顶点");
        }
        this.coords = coords;
        int n = coords.length / 3;
        this.cumLen = new double[n];
        for (int i = 1; i < n; i++) {
            double dx = coords[i * 3] - coords[(i - 1) * 3];
            double dy = coords[i * 3 + 1] - coords[(i - 1) * 3 + 1];
            double dz = coords[i * 3 + 2] - coords[(i - 1) * 3 + 2];
            cumLen[i] = cumLen[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        this.length = cumLen[n - 1];
    }

    /**
     * 线段数。
     *
     * @return 顶点数 - 1
     */
    public int segmentCount() {
        return cumLen.length - 1;
    }

    /**
     * 投影结果。
     *
     * @param fraction 投影点沿折线的比例（0 = from 端，1 = to 端）
     * @param segment  命中的线段下标（可作下次投影的提示）
     * @param distSq   列车位置到投影点的距离平方
     */
    public record Projection(double fraction, int segment, double distSq) {
    }

    /**
     * 把点投影到折线上（取最近线段）。
     * <p>
     * {@code hintSegment >= 0} 时先只搜提示附近 {@link #HINT_WINDOW} 段，命中距离过远再全量扫描；
     * 否则直接全量扫描。
     *
     * @param x           点 x
     * @param y           点 y
     * @param z           点 z
     * @param hintSegment 上次命中的线段下标（无则传 -1）
     * @return 投影结果
     */
    public Projection project(double x, double y, double z, int hintSegment) {
        int segs = segmentCount();
        if (hintSegment >= 0 && hintSegment < segs) {
            Projection local = scan(x, y, z, Math.max(0, hintSegment - HINT_WINDOW),
                    Math.min(segs - 1, hintSegment + HINT_WINDOW));
            if (local.distSq <= HINT_MAX_DIST_SQ) {
                return local;
            }
        }
        return scan(x, y, z, 0, segs - 1);
    }

    private Projection scan(double x, double y, double z, int fromSeg, int toSeg) {
        double bestDistSq = Double.MAX_VALUE;
        double bestAlong = 0;
        int bestSeg = fromSeg;
        for (int i = fromSeg; i <= toSeg; i++) {
            int a = i * 3;
            int b = a + 3;
            double ax = coords[a], ay = coords[a + 1], az = coords[a + 2];
            double vx = coords[b] - ax, vy = coords[b + 1] - ay, vz = coords[b + 2] - az;
            double segLenSq = vx * vx + vy * vy + vz * vz;
            double t = 0;
            if (segLenSq > 0) {
                t = ((x - ax) * vx + (y - ay) * vy + (z - az) * vz) / segLenSq;
                t = Math.max(0, Math.min(1, t));
            }
            double px = ax + vx * t - x, py = ay + vy * t - y, pz = az + vz * t - z;
            double distSq = px * px + py * py + pz * pz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestSeg = i;
                bestAlong = cumLen[i] + (cumLen[i + 1] - cumLen[i]) * t;
            }
        }
        double fraction = length > 0 ? bestAlong / length : 0;
        return new Projection(fraction, bestSeg, bestDistSq);
    }
}
