package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.signactions.component.BossbarManager;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbarBase;

import java.util.List;

/**
 * 列车导航的读写门面：在 {@link BcRouteProperty} / {@link BcRouteIndexProperty} 之上提供
 * setRoute / currentSwitchLineId / advance / clear。
 * <p>
 * 导航序列是整条路径的<b>节点步骤序列</b>（{@link GeoRoutePath#routeSteps()}，有序、含起终点站台）：
 * 道岔步骤编码 {@code "S:lineId"}，车站步骤编码 {@code "P"}。单指针 {@link BcRouteIndexProperty}
 * 指向「下一个将经过的节点」。列车每物理经过一个节点控制牌推进一格：
 * <ul>
 *   <li>bcswitcher 进站（GROUP_ENTER）：按当前步骤的 lineId 选向，随后推进。</li>
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
    }

    /**
     * 取列车当前应走的 lineId（仅当当前步骤为道岔步骤 {@code "S:lineId"} 时有意义）。
     * <p>
     * bcswitcher 选向与路径预测均用此方法（只读、不推进）。当前步骤不是道岔步骤（如站台步骤，
     * 表示对齐异常或当前不在道岔）时返回 null，道岔保持默认方向。
     *
     * @param group 列车
     * @return 当前道岔应选 lineId；非道岔步骤 / 无导航 / 已走完返回 null
     */
    public static String currentSwitchLineId(MinecartGroup group) {
        String step = currentStep(group);
        if (step != null && step.startsWith(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX)) {
            return step.substring(GeoRoutePath.ROUTE_STEP_SWITCH_PREFIX.length());
        }
        return null;
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
     * 当前指针是否指向一个车站（platform）步骤。
     *
     * @param group 列车
     * @return true 表示当前步骤为车站
     */
    public static boolean isAtPlatformStep(MinecartGroup group) {
        String step = currentStep(group);
        return GeoRoutePath.ROUTE_STEP_PLATFORM.equals(step);
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
     * 清空导航（行程结束 / 到达终点）。
     *
     * @param group 列车
     */
    public static void clear(MinecartGroup group) {
        if (group == null) {
            return;
        }
        TrainProperties props = group.getProperties();
        props.set(BcRouteProperty.INSTANCE, BcRouteProperty.INSTANCE.getDefault());
        props.set(BcRouteIndexProperty.INSTANCE, BcRouteIndexProperty.INSTANCE.getDefault());
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
