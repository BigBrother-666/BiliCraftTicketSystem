package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 直达车跨站监听器：让直达车（带导航序列的快速车）跨越<b>中途站</b>时忽略指定类型的控制牌，
 * 只在<b>终点站</b>放行这些牌正常生效（停车 / 播报）。
 * <p>
 * 原理：TC 在执行任何控制牌动作前会把 {@link SignActionEvent} 作为可取消的 Bukkit 事件广播，
 * 一旦被取消就跳过该牌的全部 SignAction（含 TC 原版 station 牌）。本监听器即在此统一拦截，
 * 无需改动各个 SignAction 的 execute。
 * <p>
 * 忽略的牌类型由 {@code config.yml} 的 {@code express-skip-signs} 配置（按控制牌第二行关键字前缀匹配）。
 * <p>
 * 「是否终点站」依据<b>导航进度</b>判断：直达车每经过一个 bcswitcher 推进一格，走完全部道岔
 * （{@code index >= total}）即视为已到达终点区段，此时不再拦截。这样不依赖 station 牌是否写了站名，
 * 且与现有 {@link BcRouteNavigator} 导航机制一致。
 */
public class ExpressSkipListener implements Listener {

    /**
     * 在 TC 处理控制牌之前（LOWEST）拦截。{@code ignoreCancelled=true}：已被别处取消的事件不重复处理。
     *
     * @param event 控制牌事件
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignAction(SignActionEvent event) {
        // 只处理列车经过控制牌的进入动作；其余动作（红石、建造、卸载等）不干预
        if (!event.isAction(SignActionType.GROUP_ENTER)) {
            return;
        }
        if (!event.hasRailedMember()) {
            return;
        }
        MinecartGroup group = event.getGroup();
        if (group == null) {
            return;
        }

        // 仅直达车：带导航序列的才是买票 / 刷卡上车的快速车，普通车 / 手动车无导航序列，不受影响
        if (!BcRouteNavigator.hasRoute(group)) {
            return;
        }

        // 已到达终点区段（导航走完全部道岔）：放行，让终点站的控制牌正常停车 / 播报
        if (BcRouteNavigator.finished(group)) {
            return;
        }

        // 中途站：若该控制牌类型在忽略名单内，则取消事件，使其不触发
        if (isSkipSign(event)) {
            event.setCancelled(true);
        }
    }

    /**
     * 判断控制牌类型是否在忽略名单中（按第二行关键字前缀匹配，与 TC isType 语义一致）。
     *
     * @param event 控制牌事件
     * @return true 表示应忽略
     */
    private boolean isSkipSign(SignActionEvent event) {
        for (String type : MainConfig.expressSkipSigns) {
            if (!type.isEmpty() && event.isType(type)) {
                return true;
            }
        }
        return false;
    }
}
