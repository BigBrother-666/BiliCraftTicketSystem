package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import java.util.List;

/**
 * 列车导航的读写门面：在 {@link BcRouteProperty} / {@link BcRouteIndexProperty} 之上提供
 * setRoute / currentLineId / advance / clear。
 * <p>
 * 导航序列是「列车依次经过的各 bcswitcher 应选的 lineId」（有序、不去重），由
 * {@link com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath#switcherLineIds()} 导出。
 * 列车每经过一个 bcswitcher 推进一格，bcswitcher 据 {@link #currentLineId} 选向。
 * 状态存于 TrainCarts train property，随存档持久化。
 */
public final class BcRouteNavigator {

    private BcRouteNavigator() {
    }

    /**
     * 给列车设置导航序列并把指针归零。
     *
     * @param group           列车
     * @param switcherLineIds 各道岔应选 lineId 的有序序列
     */
    public static void setRoute(MinecartGroup group, List<String> switcherLineIds) {
        if (group == null) {
            return;
        }
        TrainProperties props = group.getProperties();
        props.set(BcRouteProperty.INSTANCE, switcherLineIds);
        props.set(BcRouteIndexProperty.INSTANCE, 0);
    }

    /**
     * 取列车当前应走的 lineId（序列[index]）。
     *
     * @param group 列车
     * @return 当前 lineId；无导航 / 已走完返回 null
     */
    public static String currentLineId(MinecartGroup group) {
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
     * 推进指针一格（列车经过一个 bcswitcher 时调用）。
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
     * 取导航进度 {@code [index, size]}，供直达车 bossbar 按 index/size 计算进度。
     * <p>
     * index 为当前已推进到的道岔下标（0 起步，每经一个 bcswitcher +1，可等于 size 表示走完）；
     * size 为导航序列总长（应选向的 bcswitcher 总数）。
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
     * @return 导航是否结束
     */
    public static boolean finished(MinecartGroup group) {
        int[] progress = BcRouteNavigator.progress(group);
        return progress[0] >= progress[1];
    }
}
