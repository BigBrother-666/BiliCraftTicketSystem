package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.bossbar.BossBar;

/**
 * 直达车 bossbar：买票 / 刷卡上车的快速车，正线跨越中间站直达终点，bossbar 显示「起点 → 终点」+ 进度条。
 * <p>
 * 进度条由导航推进驱动（每经过一个节点 index 前进一格，进度 = index / (size-1)）。三个刷新入口都<b>只读
 * 列车导航状态</b>，不推进指针：
 * <ul>
 *   <li>{@link #onArrive}：进站时刷新进度；若已到终点节点（{@link BcRouteNavigator#finished}）切终到文案。
 *       <b>终到文案只在列车物理到达终点站台时触发</b>，不会因「过完最后一个道岔指针即达 size-1」而提前。</li>
 *   <li>{@link #onLeave}：出站时刷新进度。</li>
 *   <li>{@link #refreshProgress}：经过道岔（非进出站）时刷新进度。</li>
 * </ul>
 */
public class ExpressRouteBossbar extends RouteBossbarBase {
    private final String endStation;
    private boolean ended;

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
        applyProgress(index, total);
    }

    /**
     * 从列车导航进度刷新进度条。
     */
    private void refreshFromGroup(MinecartGroup group) {
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
