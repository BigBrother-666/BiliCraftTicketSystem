package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import org.geojson.LngLatAlt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为所有区间分配 geojson 的 {@code layer}（叠层层级）。
 * <p>
 * <b>layer 定义：</b>两条 LineString 若在 XZ 平面（经度=x、纬度=z）发生空间交叉，
 * 在交叉点高度（altitude=y）更高的那条必须位于更高 layer；否则前端叠加显示时高架会被
 * 平面线盖住。layer 从 0 起、仅在确有"压住"约束时才抬升（前端只用作 {@code line-sort-key} 绘制顺序）。
 * <p>
 * 算法：
 * <ol>
 *   <li>同 id 区间去重；按 {@link #CELL_SIZE} 建 XZ 均匀网格，只对同格线段做相交检测，远距离区间不再两两比较。</li>
 *   <li>把"A 必须在 B 之上"建成有向约束（B → A），Tarjan 缩强连通分量后按拓扑序求最长路径，
 *       得到每条边的最小 layer。约束成环（同一组线在不同交叉点高低相反）时环内区间取同一 layer。</li>
 * </ol>
 * <p>
 * <b>特殊规则（联络线）：</b>属于联络线系统（{@code railwaySystemId == "contact"}）的区间与其它系统
 * 线路在 XZ 平面<b>同一高度</b>相交（平交）时，联络线在下层。高度明显不同时仍按高度规则（高的在上层），
 * 联络线不例外。这样换乘交汇处的同高平交不会让联络线盖住营运线。
 * <p>
 * <b>重合线修正：</b>两条线在 XZ 平面完全重合（共线且投影区间重叠）但高度不同（如地面线正上方架高线）时，
 * 旧实现按"平行/共线不算交叉"跳过，导致高低压盖关系丢失。现按共线重叠区间的高度差建立约束。
 */
public final class LayerAssigner {

    /**
     * 高度差视为"同高"的阈值（方块为整数高度，半格以内当作平交，不产生层级约束）。
     */
    private static final double ALT_EPS = 0.5;

    /**
     * 判定两线段是否共线的叉积阈值（XZ 平面，方块为整数坐标，容一点浮点误差）。
     */
    private static final double COLLINEAR_EPS = 1e-6;

    private LayerAssigner() {
    }

    /**
     * 空间网格边长（方块）。线段按 XZ 包围盒登记进覆盖到的格子，只有同格线段才做相交检测。
     */
    private static final double CELL_SIZE = 64.0;

    /**
     * 计算并写回所有区间的 layer。
     * <p>
     * 同 id 区间（共用轨道 / 越界边被多个文件各产出一份）几何相同，只算一次，结果写回每一份。
     * 不同世界的区间互不比较。
     *
     * @param edges 全部区间（跨文件全集，原地修改其 layer）
     * @return 落在约束环里的区间数（同一组线在不同交叉点高低相反，无法同时满足；环内区间取同一 layer）
     */
    public static int assign(List<RailEdge> edges) {
        if (edges.isEmpty()) {
            return 0;
        }
        Map<String, RailEdge> byId = new LinkedHashMap<>();
        for (RailEdge edge : edges) {
            byId.putIfAbsent(edge.getId(), edge);
        }
        List<RailEdge> uniq = new ArrayList<>(byId.values());
        int n = uniq.size();

        // above[i] = 必须位于 i 之上的区间下标
        List<List<Integer>> above = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            above.add(new ArrayList<>());
        }
        for (Map.Entry<Long, Double> entry : findCrossings(uniq).entrySet()) {
            int i = (int) (entry.getKey() / n);
            int j = (int) (entry.getKey() % n);
            int rel = decide(uniq.get(i), uniq.get(j), entry.getValue());
            if (rel > 0) {
                above.get(j).add(i);   // i 在 j 之上
            } else if (rel < 0) {
                above.get(i).add(j);   // j 在 i 之上
            }
        }

        int[] layers = new int[n];
        int cyclic = solveLayers(above, layers);
        for (int i = 0; i < n; i++) {
            uniq.get(i).setLayer(layers[i]);
        }
        for (RailEdge edge : edges) {
            edge.setLayer(byId.get(edge.getId()).getLayer());
        }
        return cyclic;
    }

    /**
     * 用空间网格找出所有在 XZ 平面相交的区间对。
     *
     * @param edges 去重后的区间
     * @return key = {@code i * n + j}（i &lt; j），value = 交叉处 (i 高度 - j 高度) 绝对值最大的一处
     */
    private static Map<Long, Double> findCrossings(List<RailEdge> edges) {
        int n = edges.size();
        // 世界 -> 格子 -> 线段引用（高 32 位区间下标，低 32 位线段下标）
        Map<String, Map<Long, List<Long>>> grids = new HashMap<>();
        for (int e = 0; e < n; e++) {
            List<LngLatAlt> c = edges.get(e).getCoordinates();
            Map<Long, List<Long>> grid = grids.computeIfAbsent(String.valueOf(edges.get(e).getWorld()), k -> new HashMap<>());
            for (int s = 0; s + 1 < c.size(); s++) {
                int[] r = cellRange(c.get(s), c.get(s + 1));
                long ref = ((long) e << 32) | s;
                for (int cx = r[0]; cx <= r[1]; cx++) {
                    for (int cz = r[2]; cz <= r[3]; cz++) {
                        grid.computeIfAbsent(cellKey(cx, cz), k -> new ArrayList<>()).add(ref);
                    }
                }
            }
        }

        Map<Long, Double> crossings = new HashMap<>();
        for (Map<Long, List<Long>> grid : grids.values()) {
            for (Map.Entry<Long, List<Long>> cell : grid.entrySet()) {
                long key = cell.getKey();
                List<Long> segs = cell.getValue();
                for (int a = 0; a < segs.size(); a++) {
                    for (int b = a + 1; b < segs.size(); b++) {
                        checkSegmentPair(edges, segs.get(a), segs.get(b), key, crossings);
                    }
                }
            }
        }
        return crossings;
    }

    /**
     * 检测一对线段并把结果并入 {@code crossings}。两段包围盒跨多个格子时只在重叠区域的第一个格子里算，避免重复。
     */
    private static void checkSegmentPair(List<RailEdge> edges, long refA, long refB, long cellKey,
                                         Map<Long, Double> crossings) {
        int ea = (int) (refA >>> 32), eb = (int) (refB >>> 32);
        if (ea == eb) {
            return;
        }
        // 低下标区间作为 a，与旧实现 compareOverpass(edges[i], edges[j]) 的参数顺序一致
        if (ea > eb) {
            long t = refA;
            refA = refB;
            refB = t;
            int te = ea;
            ea = eb;
            eb = te;
        }
        List<LngLatAlt> ca = edges.get(ea).getCoordinates();
        List<LngLatAlt> cb = edges.get(eb).getCoordinates();
        int sa = (int) refA, sb = (int) refB;
        LngLatAlt p1 = ca.get(sa), p2 = ca.get(sa + 1), p3 = cb.get(sb), p4 = cb.get(sb + 1);

        int[] ra = cellRange(p1, p2);
        int[] rb = cellRange(p3, p4);
        if (cellKey(Math.max(ra[0], rb[0]), Math.max(ra[2], rb[2])) != cellKey) {
            return;
        }
        double diff = segCrossAltDiff(p1, p2, p3, p4);
        if (Double.isNaN(diff)) {
            return;
        }
        long pairKey = (long) ea * edges.size() + eb;
        crossings.merge(pairKey, diff, (old, cur) -> Math.abs(cur) > Math.abs(old) ? cur : old);
    }

    /**
     * 线段 XZ 包围盒覆盖的格子范围 {@code [minCx, maxCx, minCz, maxCz]}。
     */
    private static int[] cellRange(LngLatAlt p, LngLatAlt q) {
        return new int[]{
                cell(Math.min(p.getLongitude(), q.getLongitude())),
                cell(Math.max(p.getLongitude(), q.getLongitude())),
                cell(Math.min(p.getLatitude(), q.getLatitude())),
                cell(Math.max(p.getLatitude(), q.getLatitude()))
        };
    }

    private static int cell(double v) {
        return (int) Math.floor(v / CELL_SIZE);
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    /**
     * 单线增量遍历用：只计算并写回 {@code toSolve} 里各区间的 layer，把 {@code fixed} 里的区间当作
     * <b>不可改动的固定障碍</b>（其 layer 保持磁盘上的既有值）。
     * <p>
     * 约束来源两类：
     * <ul>
     *   <li>toSolve ↔ fixed：若某待解区间在交叉处高于某固定区间，则它至少要在该固定区间 layer 之上
     *       （{@code layer >= fixedLayer + 1}）；低于固定区间时不产生约束（不下压固定区间）。</li>
     *   <li>toSolve ↔ toSolve：与 {@link #assign} 同样的相互叠压约束。</li>
     * </ul>
     * 局限：本命令不改动 fixed 文件，故「待解区间从上方新穿过某既有区间、按理应把既有区间下压」的情况
     * 不会反映到既有文件——极少见，可用全量 {@code walkAll} 校正。
     *
     * @param toSolve 待计算 layer 的区间（原地修改其 layer）
     * @param fixed   固定障碍区间（只读其坐标与 layer，不修改）
     */
    public static void assignRelative(List<RailEdge> toSolve, List<RailEdge> fixed) {
        int n = toSolve.size();
        if (n == 0) {
            return;
        }
        List<List<Integer>> below = new ArrayList<>(n);
        // baseFloor[i] = 因压在固定障碍之上而要求的最小 layer（fixedLayer + 1 的最大值）
        int[] baseFloor = new int[n];
        for (RailEdge railEdge : toSolve) {
            below.add(new ArrayList<>());
            railEdge.setLayer(0);
        }
        // toSolve ↔ fixed
        for (int i = 0; i < n; i++) {
            for (RailEdge fx : fixed) {
                int rel = compareOverpass(toSolve.get(i), fx);
                if (rel > 0) {
                    baseFloor[i] = Math.max(baseFloor[i], fx.getLayer() + 1);
                }
            }
            toSolve.get(i).setLayer(baseFloor[i]);
        }
        // toSolve ↔ toSolve
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int rel = compareOverpass(toSolve.get(i), toSolve.get(j));
                if (rel > 0) {
                    below.get(i).add(j);
                } else if (rel < 0) {
                    below.get(j).add(i);
                }
            }
        }
        relaxWithFloor(toSolve, below, baseFloor);
    }

    /**
     * 带下限的最长路径松弛：{@code layer(i) = max(baseFloor(i), layer(下方者)+1)}。
     */
    private static void relaxWithFloor(List<RailEdge> edges, List<List<Integer>> below, int[] baseFloor) {
        int n = edges.size();
        for (int iter = 0; iter < n; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int want = baseFloor[i];
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
     * 按约束图求每个区间的最小 layer：Tarjan 缩强连通分量（环内区间取同一 layer），
     * 再按缩点后的拓扑序求最长路径。整体 O(区间数 + 约束数)。
     *
     * @param above  above[i] = 必须位于 i 之上的区间下标
     * @param layers 输出：每个区间的 layer
     * @return 落在约束环（大小 &gt; 1 的强连通分量）里的区间数
     */
    private static int solveLayers(List<List<Integer>> above, int[] layers) {
        int n = above.size();
        int[] index = new int[n];
        int[] low = new int[n];
        int[] iterPos = new int[n];
        int[] comp = new int[n];
        boolean[] onStack = new boolean[n];
        Arrays.fill(index, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        Deque<Integer> call = new ArrayDeque<>();
        List<List<Integer>> members = new ArrayList<>();
        int counter = 0;

        // 迭代式 Tarjan，避免区间多时递归栈溢出
        for (int s = 0; s < n; s++) {
            if (index[s] != -1) {
                continue;
            }
            index[s] = low[s] = counter++;
            stack.push(s);
            onStack[s] = true;
            call.push(s);
            while (!call.isEmpty()) {
                int v = call.peek();
                List<Integer> adj = above.get(v);
                if (iterPos[v] < adj.size()) {
                    int w = adj.get(iterPos[v]++);
                    if (index[w] == -1) {
                        index[w] = low[w] = counter++;
                        stack.push(w);
                        onStack[w] = true;
                        call.push(w);
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], index[w]);
                    }
                    continue;
                }
                call.pop();
                if (!call.isEmpty()) {
                    int u = call.peek();
                    low[u] = Math.min(low[u], low[v]);
                }
                if (low[v] == index[v]) {
                    List<Integer> group = new ArrayList<>();
                    int w;
                    do {
                        w = stack.pop();
                        onStack[w] = false;
                        comp[w] = members.size();
                        group.add(w);
                    } while (w != v);
                    members.add(group);
                }
            }
        }

        // Tarjan 按逆拓扑序产出分量（上方的分量先产出），故从最后一个分量往前即为自下而上
        int[] compLayer = new int[members.size()];
        int cyclic = 0;
        for (int c = members.size() - 1; c >= 0; c--) {
            List<Integer> group = members.get(c);
            if (group.size() > 1) {
                cyclic += group.size();
            }
            for (int v : group) {
                layers[v] = compLayer[c];
                for (int w : above.get(v)) {
                    if (comp[w] != c) {
                        compLayer[comp[w]] = Math.max(compLayer[comp[w]], compLayer[c] + 1);
                    }
                }
            }
        }
        return cyclic;
    }

    /**
     * 判断两区间的叠压关系。
     * <p>
     * 先逐段做 XZ 平面相交检测（含共线重叠）：记录是否相交、以及交叉处高度差最大的一处。
     * 高度差超过阈值时按高度定高低（高的在上层）；仅在同高平交、且只有一方属联络线系统时，联络线在下层。
     *
     * @return 1 表示 a 整体应在 b 之上、-1 表示 b 在 a 之上、0 表示无平面交叉（不产生约束）
     */
    private static int compareOverpass(RailEdge a, RailEdge b) {
        List<LngLatAlt> ca = a.getCoordinates();
        List<LngLatAlt> cb = b.getCoordinates();
        double bestDiff = 0;
        boolean intersects = false;
        for (int i = 0; i + 1 < ca.size(); i++) {
            for (int j = 0; j + 1 < cb.size(); j++) {
                double diff = segCrossAltDiff(ca.get(i), ca.get(i + 1), cb.get(j), cb.get(j + 1));
                if (!Double.isNaN(diff)) {
                    intersects = true;
                    if (Math.abs(diff) > Math.abs(bestDiff)) {
                        bestDiff = diff;
                    }
                }
            }
        }
        if (!intersects) {
            return 0;
        }
        return decide(a, b, bestDiff);
    }

    /**
     * 已知两区间在 XZ 平面相交、交叉处最大高度差为 {@code bestDiff}（a - b）时，判定叠压关系。
     *
     * @return 1 表示 a 在 b 之上、-1 表示 b 在 a 之上、0 表示同高平交不产生约束
     */
    private static int decide(RailEdge a, RailEdge b, double bestDiff) {
        // 高度规则优先：交叉处高度明显不同时，高的在上层（联络线也不例外）
        if (bestDiff > ALT_EPS) {
            return 1;
        }
        if (bestDiff < -ALT_EPS) {
            return -1;
        }
        // 同高平面相交（平交）：只有一方是联络线时，联络线在下层
        boolean aContact = RailwaySystemConfig.CONTACT_ID.equals(a.getRailwaySystemId());
        boolean bContact = RailwaySystemConfig.CONTACT_ID.equals(b.getRailwaySystemId());
        if (aContact != bContact) {
            return aContact ? -1 : 1;
        }
        return 0;
    }

    /**
     * 两线段在 XZ 平面相交时，返回 (a 段交点高度 - b 段交点高度)；不相交返回 {@link Double#NaN}。
     * <p>
     * 相交分两种：①普通横穿（参数 t、s 均落在 [0,1]）；②共线且 XZ 投影区间重叠（完全 / 部分重合的
     * 共用轨道段）——取重叠区间中点比高度，从而正确处理"一条线正上方架空另一条线"的叠压。
     */
    private static double segCrossAltDiff(LngLatAlt p1, LngLatAlt p2, LngLatAlt p3, LngLatAlt p4) {
        double x1 = p1.getLongitude(), y1 = p1.getLatitude();
        double x2 = p2.getLongitude(), y2 = p2.getLatitude();
        double x3 = p3.getLongitude(), y3 = p3.getLatitude();
        double x4 = p4.getLongitude(), y4 = p4.getLatitude();

        double dax = x2 - x1, day = y2 - y1;
        double dbx = x4 - x3, dby = y4 - y3;
        double denom = dax * dby - day * dbx;
        if (Math.abs(denom) < COLLINEAR_EPS) {
            // 平行或共线：只有共线且 XZ 投影重叠才算交叉（含完全重合的共用轨道）
            return collinearOverlapAltDiff(p1, p2, p3, p4);
        }
        double t = ((x3 - x1) * dby - (y3 - y1) * dbx) / denom;
        double s = ((x3 - x1) * day - (y3 - y1) * dax) / denom;
        if (t < 0 || t > 1 || s < 0 || s > 1) {
            return Double.NaN;
        }
        double altA = p1.getAltitude() + t * (p2.getAltitude() - p1.getAltitude());
        double altB = p3.getAltitude() + s * (p4.getAltitude() - p3.getAltitude());
        return altA - altB;
    }

    /**
     * 两线段在 XZ 平面平行 / 共线时的重叠判定：不共线返回 NaN；共线但投影区间不重叠返回 NaN；
     * 共线且重叠则取重叠区间中点，返回 (a 段该处高度 - b 段该处高度)。
     */
    private static double collinearOverlapAltDiff(LngLatAlt p1, LngLatAlt p2, LngLatAlt p3, LngLatAlt p4) {
        double x1 = p1.getLongitude(), y1 = p1.getLatitude();
        double x2 = p2.getLongitude(), y2 = p2.getLatitude();
        double dax = x2 - x1, day = y2 - y1;
        double lenSq = dax * dax + day * day;
        if (lenSq < COLLINEAR_EPS) {
            // a 段退化为一点：无法定义共线区间
            return Double.NaN;
        }
        // p3 是否落在过 p1、p2 的直线上（叉积 ~ 0）
        double cross3 = dax * (p3.getLatitude() - y1) - day * (p3.getLongitude() - x1);
        double cross4 = dax * (p4.getLatitude() - y1) - day * (p4.getLongitude() - x1);
        if (Math.abs(cross3) > COLLINEAR_EPS || Math.abs(cross4) > COLLINEAR_EPS) {
            return Double.NaN; // 平行但不共线
        }
        // 把 b 段两端点投影到 a 段参数 t，求与 [0,1] 的重叠
        double t3 = ((p3.getLongitude() - x1) * dax + (p3.getLatitude() - y1) * day) / lenSq;
        double t4 = ((p4.getLongitude() - x1) * dax + (p4.getLatitude() - y1) * day) / lenSq;
        double loB = Math.min(t3, t4), hiB = Math.max(t3, t4);
        double lo = Math.max(0.0, loB), hi = Math.min(1.0, hiB);
        if (lo > hi) {
            return Double.NaN; // 投影区间不重叠
        }
        double tMid = (lo + hi) / 2.0;                       // a 段上的重叠中点参数
        double altA = p1.getAltitude() + tMid * (p2.getAltitude() - p1.getAltitude());
        // 该点在 b 段上的参数（b 段可能反向，故按 t3->t4 线性还原）
        double sMid = (t4 - t3) == 0 ? 0 : (tMid - t3) / (t4 - t3);
        sMid = Math.max(0.0, Math.min(1.0, sMid));
        double altB = p3.getAltitude() + sMid * (p4.getAltitude() - p3.getAltitude());
        return altA - altB;
    }
}
