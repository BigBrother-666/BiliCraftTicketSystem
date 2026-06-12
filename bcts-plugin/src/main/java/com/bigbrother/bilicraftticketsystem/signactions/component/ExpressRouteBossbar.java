package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.bossbar.BossBar;

/**
 * 直达车 bossbar：买票 / 刷卡上车的快速车，正线跨越中间站直达终点，bossbar 显示「起点 → 终点」+ 进度条。
 * <p>
 * 直达车不触发中间站的 platform 控制牌，进度由导航序列推进驱动（见
 * {@link com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator#progress}）：列车每经过一个
 * bcswitcher，index 前进一格，进度 = index / total；走完（index >= total）显示终到文案。
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

    /**
     * 按导航进度刷新进度条；到达终点时切换为终到文案。
     *
     * @param index 当前已推进到的道岔下标
     * @param total 导航序列总长（道岔总数）
     */
    public void refreshProgress(int index, int total) {
        if (bossBar == null || total <= 0) {
            return;
        }
        float progress = Math.min(Math.max((float) index / total, 0f), 1.0f);
        bossBar.progress(progress);
        if (index >= total && !ended && endStation != null) {
            ended = true;
            bossBar.progress(1.0f);
            bossBar.name(CommonUtils.mmStr2Component(MainConfig.message.get("express-end", "").formatted(endStation)));
        }
    }

    /**
     * 直达车 bossbar 进度由导航推进驱动，进出站控制牌不参与。
     */
    @Override
    public void onArrive(String currStation) {
        // no-op：直达车跨越中间站，不在站台牌处更新
    }

    @Override
    public void onLeave() {
        // no-op
    }
}
