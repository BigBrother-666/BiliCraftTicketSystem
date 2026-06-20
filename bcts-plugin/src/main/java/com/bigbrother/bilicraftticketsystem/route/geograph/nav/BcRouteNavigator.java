package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.signactions.component.BossbarManager;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbarBase;

import java.util.ArrayList;
import java.util.List;

/**
 * 列车导航的读写门面：在 {@link BcRouteProperty} / {@link BcRouteIndexProperty} 之上提供
 * setRoute / currentSwitchDirection / advance / clear。
 * <p>
 * 导航序列是整条路径的<b>节点步骤序列</b>（{@link GeoRoutePath#routeSteps()}，有序、含起终点站台）：
 * 道岔步骤编码 {@code "S:dir"}（dir 为物理出向），车站步骤编码 {@code "P"}。单指针 {@link BcRouteIndexProperty}
 * 指向「下一个将经过的节点」。列车每物理经过一个节点控制牌推进一格：
 * <ul>
 *   <li>bcswitcher 进站（GROUP_ENTER）：按当前步骤的出向选向，随后推进。</li>
 *   <li>platform 出站（GROUP_LEAVE）：推进。</li>
 * </ul>
 * 由此即便整条线路没有任何 bcswitcher（全是无正线车站），指针也能随 platform 推进直到终点。
 * 状态存于 TrainCarts train property，随存档持久化。
 */
public final class BcRouteNavigator {

    private BcRouteNavigator() {
    }

    /**
     * 给列车设置导航节点步骤序列并把指针归零。
     *
     * @param group      列车
     * @param routeSteps {@link GeoRoutePath#routeSteps()} 产出的节点步骤序列
     */
    public static void setRoute(MinecartGroup group, List<String> routeSteps) {
        if (group == null) {
            return;
        }
        TrainProperties props = group.getProperties();
        props.set(BcRouteProperty.INSTANCE, routeSteps);
        props.set(BcRouteIndexProperty.INSTANCE, 0);
        props.set(BcLastAdvanceNodeProperty.INSTANCE, "");
    }

    /**
     * 取列车当前道岔步骤应走的<b>物理出向</b>（仅当当前步骤为道岔步骤 {@code "S:dir"} 时有意义）。
     * <p>
     * bcswitcher 选向与路径预测均用此方法（只读、不推进）。当前步骤不是道岔步骤（如站台步骤，
     * 表示对齐异常或当前不在道岔）时返回 null，道岔保持默认 / 回退选向。载荷为空串（旧数据无 departDir）
     * 时返回 null，道岔回退按 lineId / tag 选向。
     *
     * @param group 列车
     * @return 当前道岔应走出向（e/s/w/n 或 f/b/l/r）；非道岔步骤 / 无导航 / 已走完 / 载荷空返回 null
     */
    public static String currentSwitchDirection(MinecartGroup group) {
        // 预测模拟进行中：返回模拟游标当前 S 步骤的出向（peek，不推进）。真正的「按道岔方块推进」
        // 由 predictPathFinding 经 {@link #predictionSwitchDirection(String)} 驱动。
        if (predictionSim.get() != null) {
            return predictionSim.get().peekDirection();
        }
        String step = currentStep(group);
        if (step != null && step.startsWith(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX)) {
            String dir = step.substring(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX.length());
            return dir.isEmpty() ? null : dir;
        }
        return null;
    }

    /**
     * 预测寻路时的「道岔出向模拟游标」（线程局部）。
     * <p>
     * 问题：{@link com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher#predictPathFinding}
     * 故意不推进列车真实导航指针（预测会被 TC 反复调用）。但用 {@code setFollowPredictedPath} 沿列车
     * 路径向前走（如 slowdown 减速距离预测）跨<b>多个</b>道岔时，每个 bcswitcher 都读列车真实指针所指的
     * <b>同一</b>步骤，导致第二个及之后的道岔选错向、预测路径偏离列车实际路径。
     * <p>
     * 解法：在预测行走期间装一个本地模拟游标（{@link #beginPredictionSim} / {@link #endPredictionSim}），
     * 快照列车当前指针起的剩余道岔出向序列，<b>每跨到一个新道岔铁轨方块</b>读取下一格出向，从而正确逐个
     * 选向，且不触碰列车真实导航状态。仅在主线程的预测行走中使用，故用 ThreadLocal 隔离。
     * <p>
     * 同一铁轨方块挂多块 bcswitcher（共块）会就同一物理道岔多次调用 predictPathFinding——参照真实
     * 执行用 {@link BcLastAdvanceNodeProperty} 的方块去重（见 {@code SignActionBcswitcher.execute} 顶部
     * {@code alreadyAdvancedAt} 整体跳过），本游标按<b>道岔方块</b>消重：同一方块的多次调用读同一格出向，
     * 只在方块变化时才前进一格，避免指针超前移动。
     */
    private static final ThreadLocal<PredictionSim> predictionSim = new ThreadLocal<>();

    /**
     * 道岔出向模拟游标：持有从某起点开始的剩余道岔出向序列，按<b>道岔铁轨方块</b>逐个推进。
     */
    private static final class PredictionSim {
        private final List<String> switchDirs;
        /**
         * 当前所指出向下标。初值 -1 表示「尚未定位到任一道岔方块」，第一次跨入新方块时前进到 0。
         */
        private int index = -1;
        /**
         * 上次定位到的道岔铁轨方块去重 key（同一方块多块牌 / 重复调用读同一格）。
         */
        private String lastBlockKey = null;

        private PredictionSim(List<String> switchDirs) {
            this.switchDirs = switchDirs;
        }

        /**
         * 读取当前游标所指出向（peek，不推进）。越界 / 尚未定位返回 null（道岔回退 lineId / tag 选向）。
         *
         * @return 当前出向；无则 null
         */
        private String peekDirection() {
            if (index < 0 || index >= switchDirs.size()) {
                return null;
            }
            String dir = switchDirs.get(index);
            return dir == null || dir.isEmpty() ? null : dir;
        }

        /**
         * 在一个道岔铁轨方块处取出向：仅当 {@code blockKey} 与上次不同（跨到新道岔方块）才前进一格，
         * 同一方块的多块牌 / 重复调用读同一格出向，避免指针超前。读取在前进判断<b>之后</b>，
         * 保证同方块多次调用一致。
         *
         * @param blockKey 道岔铁轨方块去重 key
         * @return 该道岔应走出向；无则 null
         */
        private String directionForBlock(String blockKey) {
            if (!java.util.Objects.equals(blockKey, lastBlockKey)) {
                lastBlockKey = blockKey;
                index++;
            }
            return peekDirection();
        }
    }

    /**
     * 开始一次预测寻路模拟：以列车当前导航指针为起点，快照其后所有道岔（{@code S:}）步骤的出向序列，
     * 装入线程局部游标。必须与 {@link #endPredictionSim} 配对（用 try/finally）。
     * <p>
     * 列车无导航序列时不装游标（{@link #currentSwitchDirection} 走原逻辑，对普通车无影响）。
     *
     * @param group 列车
     */
    public static void beginPredictionSim(MinecartGroup group) {
        if (group == null) {
            return;
        }
        List<String> route = group.getProperties().get(BcRouteProperty.INSTANCE);
        if (route == null || route.isEmpty()) {
            return;
        }
        int index = group.getProperties().get(BcRouteIndexProperty.INSTANCE);
        List<String> switchDirs = new ArrayList<>();
        for (int i = Math.max(0, index); i < route.size(); i++) {
            String step = route.get(i);
            if (step != null && step.startsWith(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX)) {
                switchDirs.add(step.substring(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX.length()));
            }
        }
        predictionSim.set(new PredictionSim(switchDirs));
    }

    /**
     * 结束预测寻路模拟，清除线程局部游标。
     */
    public static void endPredictionSim() {
        predictionSim.remove();
    }

    /**
     * 预测寻路在一个道岔方块处取应走出向（仅当预测模拟进行中有效）。供 bcswitcher 的
     * {@code predictPathFinding} 选向时调用：按道岔方块消重逐格推进，使下一个道岔读到下一步出向，
     * 同一方块的多块牌读同一格（不超前）。
     *
     * @param blockKey 道岔铁轨方块去重 key（{@link com.bigbrother.bilicraftticketsystem.route.NodeId#ofBlock}）
     * @return 该道岔应走出向；无导航模拟 / 越界 / 载荷空返回 null
     */
    public static String predictionSwitchDirection(String blockKey) {
        PredictionSim sim = predictionSim.get();
        return sim == null ? null : sim.directionForBlock(blockKey);
    }

    /**
     * 预测寻路模拟是否进行中。
     *
     * @return true 表示当前线程正在进行预测模拟
     */
    public static boolean isPredictionSim() {
        return predictionSim.get() != null;
    }

    /**
     * 取列车当前指针所指的节点步骤（原始编码）。
     *
     * @param group 列车
     * @return 当前步骤；越界 / 无导航返回 null
     */
    public static String currentStep(MinecartGroup group) {
        if (group == null) {
            return null;
        }
        TrainProperties props = group.getProperties();
        List<String> route = props.get(BcRouteProperty.INSTANCE);
        if (route == null || route.isEmpty()) {
            return null;
        }
        int index = props.get(BcRouteIndexProperty.INSTANCE);
        if (index < 0 || index >= route.size()) {
            return null;
        }
        return route.get(index);
    }

    /**
     * 当前指针是否指向一个道岔步骤。
     *
     * @param group 列车
     * @return true 表示当前步骤为道岔
     */
    public static boolean isAtSwitchStep(MinecartGroup group) {
        String step = currentStep(group);
        return step != null && step.startsWith(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX);
    }

    /**
     * 推进指针一格（列车经过一个节点控制牌时调用）。
     *
     * @param group 列车
     */
    public static void advance(MinecartGroup group) {
        if (group == null) {
            return;
        }
        TrainProperties props = group.getProperties();
        List<String> route = props.get(BcRouteProperty.INSTANCE);
        if (route == null || route.isEmpty()) {
            return;
        }
        int index = props.get(BcRouteIndexProperty.INSTANCE);
        props.set(BcRouteIndexProperty.INSTANCE, index + 1);
    }

    /**
     * 按<b>节点 id 去重</b>推进指针一格：仅当 {@code nodeId} 与上次推进的节点不同才推进。
     * <p>
     * 解决「一个铁轨方块挂多个控制牌」（多个 bcswitcher 共块、道岔与车站共块）导致同一物理节点
     * 触发多次 GROUP_ENTER/LEAVE、指针被重复推进的问题——保证一个物理节点只推进一格。
     * 环线安全见 {@link BcLastAdvanceNodeProperty}。
     *
     * @param group  列车
     * @param nodeId 当前控制牌所在节点 id（{@link com.bigbrother.bilicraftticketsystem.route.NodeId#ofBlock}）
     * @return true 表示本次确实推进了；false 表示同一节点重复触发被忽略
     */
    public static boolean advance(MinecartGroup group, String nodeId) {
        if (group == null) {
            return false;
        }
        TrainProperties props = group.getProperties();
        List<String> route = props.get(BcRouteProperty.INSTANCE);
        if (route == null || route.isEmpty()) {
            return false;
        }
        if (nodeId != null && nodeId.equals(props.get(BcLastAdvanceNodeProperty.INSTANCE))) {
            // 同一物理节点的另一块控制牌重复触发，已推进过，忽略
            return false;
        }
        int index = props.get(BcRouteIndexProperty.INSTANCE);
        props.set(BcRouteIndexProperty.INSTANCE, index + 1);
        if (nodeId != null) {
            props.set(BcLastAdvanceNodeProperty.INSTANCE, nodeId);
        }
        return true;
    }

    /**
     * 当前物理节点是否已被推进过（同一节点的另一块控制牌重复触发时为 true）。
     * <p>
     * 道岔选向逻辑用它判断：重复触发时指针已前移，不能再读「当前步骤」选向（会读成下一步、切错道岔），
     * 应整体跳过本次选向 + 推进。
     *
     * @param group  列车
     * @param nodeId 当前控制牌所在节点 id
     * @return true 表示该节点上次已推进过
     */
    public static boolean alreadyAdvancedAt(MinecartGroup group, String nodeId) {
        if (group == null || nodeId == null) {
            return false;
        }
        return nodeId.equals(group.getProperties().get(BcLastAdvanceNodeProperty.INSTANCE));
    }

    /**
     * 取列车当前的导航节点步骤序列（只读快照）。
     *
     * @param group 列车
     * @return 节点步骤序列；无导航返回空列表
     */
    public static List<String> getRoute(MinecartGroup group) {
        if (group == null) {
            return java.util.Collections.emptyList();
        }
        List<String> route = group.getProperties().get(BcRouteProperty.INSTANCE);
        return route == null ? java.util.Collections.emptyList() : route;
    }

    /**
     * 判断给定节点步骤序列是否与列车当前导航序列<b>完全相同</b>（顺序、内容逐项相等）。
     * <p>
     * 用于上车校验「路线相同」：后续玩家的车票重算出的路线须与列车正在行驶的路线一致才能上车。
     *
     * @param group 列车
     * @param steps 待比对的节点步骤序列（如车票 {@code pathInfo.routeSteps()}）
     * @return 两序列逐项相等返回 true
     */
    public static boolean routeEquals(MinecartGroup group, List<String> steps) {
        if (steps == null) {
            return false;
        }
        return getRoute(group).equals(steps);
    }

    /**
     * 列车是否带有导航序列。
     *
     * @param group 列车
     * @return true 表示有非空导航序列
     */
    public static boolean hasRoute(MinecartGroup group) {
        if (group == null) {
            return false;
        }
        List<String> route = group.getProperties().get(BcRouteProperty.INSTANCE);
        return route != null && !route.isEmpty();
    }

    /**
     * 取导航进度 {@code [index, size]}：index 为当前已推进到的节点下标（0 起步，每经一个节点 +1），
     * size 为节点步骤序列总长。
     *
     * @param group 列车
     * @return 长度为 2 的数组 {@code {index, size}}；无导航返回 {@code {0, 0}}
     */
    public static int[] progress(MinecartGroup group) {
        if (group == null) {
            return new int[]{0, 0};
        }
        TrainProperties props = group.getProperties();
        List<String> route = props.get(BcRouteProperty.INSTANCE);
        if (route == null || route.isEmpty()) {
            return new int[]{0, 0};
        }
        int index = props.get(BcRouteIndexProperty.INSTANCE);
        return new int[]{index, route.size()};
    }

    /**
     * 导航是否到达终点区段：指针已抵达最后一个节点（终点站台）。
     * <p>
     * 用 {@code index >= size - 1} 判断（最后一个节点下标为 size-1）：直达车据此在终点站正常停车 /
     * 播报、跨站监听据此不再跳过终点站控制牌。无导航返回 false。
     *
     * @param group 列车
     * @return 是否到达终点区段
     */
    public static boolean finished(MinecartGroup group) {
        int[] progress = BcRouteNavigator.progress(group);
        int size = progress[1];
        return size > 0 && progress[0] >= size - 1;
    }

    /**
     * 按当前导航进度刷新列车上所有 bossbar 的进度（供列车经过<b>道岔</b>时调用）。
     * <p>
     * 进站 / 出站的刷新由 {@link com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform}
     * 通过 bossbar 的 onArrive / onLeave 处理；本方法只覆盖道岔这一非进出站场景。普通车 bossbar 的
     * refreshProgress 为默认空实现，故无需类型判断。
     *
     * @param group 列车
     */
    public static void refreshExpressBossbar(MinecartGroup group) {
        if (group == null) {
            return;
        }
        int[] progress = progress(group);
        for (MinecartMember<?> member : group) {
            RouteBossbarBase bossbar = BossbarManager.get(member);
            if (bossbar != null) {
                bossbar.refreshProgress(progress[0], progress[1]);
            }
        }
    }
}
