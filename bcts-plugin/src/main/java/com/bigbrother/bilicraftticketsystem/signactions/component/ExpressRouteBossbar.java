package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteTrack;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * 直达车 bossbar：买票 / 刷卡上车的快速车，正线跨越中间站直达终点，bossbar 显示「起点 → 终点」+ 进度条。
 * <p>
 * 进度条按<b>实时位置</b>刷新：定时任务（{@link #tick}）把车头投影到导航节点序列对应的 geojson 轨道折线上，
 * 进度 = 沿线已行驶距离 / 全程距离。投影只在「上一节点 → 下一节点」这一跳（导航指针附近）内搜索，
 * 节点事件推进指针后自然换到下一跳。轨迹构建失败（缺几何 / 节点间无边 / 旧格式步骤无节点 id）时
 * 退化为按节点计数（index / size）。各刷新入口都<b>只读列车导航状态</b>，不推进指针：
 * <ul>
 *   <li>{@link #onArrive}：进站时刷新进度；若已到终点节点（{@link BcRouteNavigator#finished}）切终到文案。
 *       <b>终到文案只在列车物理到达终点站台时触发</b>，不会因「过完最后一个道岔指针即达 size-1」而提前。</li>
 *   <li>{@link #onLeave}：出站时刷新进度（仅退化模式）。</li>
 *   <li>{@link #refreshProgress}：经过道岔（非进出站）时刷新进度（仅退化模式）。</li>
 *   <li>{@link #tick}：定时按实时位置刷新进度。</li>
 * </ul>
 */
public class ExpressRouteBossbar extends RouteBossbarBase {
    private final String endStation;
    private boolean ended;
    /**
     * 当前导航序列对应的轨迹缓存（序列变化即重建：走错方向重算会替换序列）。
     */
    private List<String> trackRoute;
    private GeoRouteTrack track;
    private final GeoRouteTrack.Cursor cursor = new GeoRouteTrack.Cursor();
    /**
     * 已显示过的最大沿线距离（当前轨迹内）：投影噪声 / 列车短暂回溯时进度条不倒退。路线重算后重置。
     */
    private double maxAlong;
    /**
     * 路线重算前已完成的进度比例：重算后新序列从当前节点起算，进度 = base + (1-base) × 新轨迹比例，
     * 避免重算后进度条归零。
     */
    private double baseFraction;

    /**
     * @param startStation 起点站名
     * @param endStation   终点站名
     */
    public ExpressRouteBossbar(String startStation, String endStation) {
        super(startStation == null || endStation == null ? null
                : BossBar.bossBar(
                CommonUtils.mmStr2Component(MainConfig.message.get("express-normal", "").formatted(startStation, endStation)),
                0.0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS));
        this.endStation = endStation;
    }

    @Override
    public void onArrive(MinecartGroup group, String currStation) {
        // 物理到达终点站台（指针已到末节点）：切终到文案；否则按进度刷新
        if (BcRouteNavigator.finished(group)) {
            markEnded();
        } else {
            refreshFromGroup(group);
        }
    }

    @Override
    public void onLeave(MinecartGroup group) {
        refreshFromGroup(group);
    }

    @Override
    public void refreshProgress(int index, int total) {
        // 有几何轨迹时进度交给实时投影，节点事件不再按计数覆盖（避免进度条来回跳）
        if (track == null) {
            applyProgress(index, total);
        }
    }

    @Override
    public void tick(MinecartGroup group, Location head) {
        if (bossBar == null || ended || head == null || head.getWorld() == null) {
            return;
        }
        List<String> route = BcRouteNavigator.getRoute(group);
        if (route.isEmpty()) {
            return;
        }
        if (!route.equals(trackRoute)) {
            if (trackRoute != null) {
                baseFraction = bossBar.progress();
            }
            trackRoute = route;
            track = buildTrack(route);
            maxAlong = 0;
        }
        if (track == null || track.totalDistance() <= 0 || !head.getWorld().getName().equals(track.getWorld())) {
            return;
        }
        // 指针指向「下一个将经过的节点」，列车位于第 index-1 跳（上一节点 → 下一节点）上；
        // 前后各放宽一跳，容忍控制牌触发时机与几何节点位置的偏差
        int index = BcRouteNavigator.progress(group)[0];
        double along = track.locate(index - 2, index, head.getX(), head.getY(), head.getZ(), cursor);
        if (Double.isNaN(along)) {
            return;
        }
        maxAlong = Math.max(maxAlong, along);
        double fraction = baseFraction + (1 - baseFraction) * (maxAlong / track.totalDistance());
        bossBar.progress((float) Math.min(Math.max(fraction, 0), 1));
    }

    /**
     * 从导航节点步骤序列构建轨迹；任一步骤缺节点 id（旧格式）或节点间无边返回 null。
     */
    private static GeoRouteTrack buildTrack(List<String> route) {
        List<String> nodeIds = new ArrayList<>(route.size());
        for (String step : route) {
            String nodeId = GeoRoutePath.stepNodeId(step);
            if (nodeId == null) {
                return null;
            }
            nodeIds.add(nodeId);
        }
        return GeoRouteTrack.ofNodePath(GeoRouteEngine.getGraph(), nodeIds);
    }

    /**
     * 从列车导航进度刷新进度条。
     */
    private void refreshFromGroup(MinecartGroup group) {
        if (track != null) {
            return;
        }
        int[] progress = BcRouteNavigator.progress(group);
        applyProgress(progress[0], progress[1]);
    }

    private void applyProgress(int index, int total) {
        if (bossBar == null || total <= 0 || ended) {
            return;
        }
        float progress = Math.min(Math.max((float) index / total, 0f), 1.0f);
        bossBar.progress(progress);
    }

    /**
     * 进度条置满并切换为终到文案。幂等。
     */
    private void markEnded() {
        if (bossBar == null || ended || endStation == null) {
            return;
        }
        ended = true;
        bossBar.progress(1.0f);
        bossBar.name(CommonUtils.mmStr2Component(MainConfig.message.get("express-end", "").formatted(endStation)));
    }
}
