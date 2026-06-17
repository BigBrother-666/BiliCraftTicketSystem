package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.List;

/**
 * 为所有区间分配 geojson 的 {@code layer}（叠层层级）。
 * <p>
 * <b>layer 定义：</b>两条 LineString 若在 XZ 平面（经度=x、纬度=z）发生空间交叉，
 * 在交叉点高度（altitude=y）更高的那条必须位于更高 layer；否则前端叠加显示时高架会被
 * 平面线盖住。layer 使用个数尽量小（前端按 layer 渲染，层数越多性能越差），因此从 0 起、
 * 仅在确有"压住"约束时才抬升。
 * <p>
 * 算法：把"A 必须在 B 之上"建成有向约束（B → A），再用最长路径松弛求每条边的最小 layer
 * （layer = 其下方约束链的最长长度）。约束图理论上可能成环（同一对线在不同交叉点高低相反），
 * 用迭代上限兜底，不会死循环。
 */
public final class LayerAssigner {

    /**
     * 高度差视为"同高"的阈值（方块为整数高度，半格以内当作平交，不产生层级约束）。
     */
    private static final double ALT_EPS = 0.5;

    private LayerAssigner() {
    }

    /**
     * 计算并写回所有区间的 layer。
     *
     * @param edges 全部区间（跨文件全集，原地修改其 layer）
     */
    public static void assign(List<RailEdge> edges) {
        int n = edges.size();
        if (n == 0) {
            return;
        }
        // above[i] = 必须低于 i 的区间下标集合（约束 j -> i：i 压在 j 之上）
        List<List<Integer>> below = new ArrayList<>(n);
        for (RailEdge edge : edges) {
            below.add(new ArrayList<>());
            edge.setLayer(0);
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int rel = compareOverpass(edges.get(i), edges.get(j));
                if (rel > 0) {
                    below.get(i).add(j);   // i 在 j 之上
                } else if (rel < 0) {
                    below.get(j).add(i);   // j 在 i 之上
                }
            }
        }
        relax(edges, below);
    }

    /**
     * 最长路径松弛：反复用 {@code layer(i) = max(layer(i), layer(下方者)+1)} 抬升，
     * 直到稳定或达到迭代上限（成环时兜底）。
     */
    private static void relax(List<RailEdge> edges, List<List<Integer>> below) {
        int n = edges.size();
        for (int iter = 0; iter < n; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int want = 0;
                for (int b : below.get(i)) {
                    want = Math.max(want, edges.get(b).getLayer() + 1);
                }
                if (want > edges.get(i).getLayer()) {
                    edges.get(i).setLayer(want);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    /**
     * 判断两区间的叠压关系。
     *
     * @return 1 表示 a 整体应在 b 之上、-1 表示 b 在 a 之上、0 表示无空间交叉或交叉处同高
     */
    private static int compareOverpass(RailEdge a, RailEdge b) {
        List<LngLatAlt> ca = a.getCoordinates();
        List<LngLatAlt> cb = b.getCoordinates();
        double bestDiff = 0;
        for (int i = 0; i + 1 < ca.size(); i++) {
            for (int j = 0; j + 1 < cb.size(); j++) {
                double diff = segCrossAltDiff(ca.get(i), ca.get(i + 1), cb.get(j), cb.get(j + 1));
                if (Math.abs(diff) > Math.abs(bestDiff)) {
                    bestDiff = diff;
                }
            }
        }
        if (bestDiff > ALT_EPS) {
            return 1;
        }
        if (bestDiff < -ALT_EPS) {
            return -1;
        }
        return 0;
    }

    /**
     * 两线段在 XZ 平面相交时，返回 (a 段交点高度 - b 段交点高度)；不相交 / 平行返回 0。
     */
    private static double segCrossAltDiff(LngLatAlt p1, LngLatAlt p2, LngLatAlt p3, LngLatAlt p4) {
        double x1 = p1.getLongitude(), y1 = p1.getLatitude();
        double x2 = p2.getLongitude(), y2 = p2.getLatitude();
        double x3 = p3.getLongitude(), y3 = p3.getLatitude();
        double x4 = p4.getLongitude(), y4 = p4.getLatitude();

        double dax = x2 - x1, day = y2 - y1;
        double dbx = x4 - x3, dby = y4 - y3;
        double denom = dax * dby - day * dbx;
        if (denom == 0) {
            // 平行或共线（含完全重合的共用轨道）——不算空间交叉
            return 0;
        }
        double t = ((x3 - x1) * dby - (y3 - y1) * dbx) / denom;
        double s = ((x3 - x1) * day - (y3 - y1) * dax) / denom;
        if (t < 0 || t > 1 || s < 0 || s > 1) {
            return 0;
        }
        double altA = p1.getAltitude() + t * (p2.getAltitude() - p1.getAltitude());
        double altB = p3.getAltitude() + s * (p4.getAltitude() - p3.getAltitude());
        return altA - altB;
    }
}
