package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * isexpress 车种判别控制牌：列车经过时，若是<b>直达车</b>（快速车）则拉下与本牌相连的拉杆，
 * 否则复位拉杆。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [+train]        ← 也可指定进入方向等，用法同其它控制牌第一行
 *   isexpress
 * </pre>
 * 用途：把「车种」暴露成红石信号，便于用原版红石 / TC 控制牌搭出按车种分流的逻辑
 * （如直达车走正线、普通车进到发线），无需插件侧再加配置。
 * <p>
 * <b>车种判据</b>：列车是否携带导航序列（{@link BcRouteNavigator#hasRoute}）。持票 / 刷卡上车的直达车
 * 由购票流程写入导航序列，普通车（站站乐）与手动车没有——这与 slowdown / 跨站监听等处判断车种的口径
 * 完全一致。
 * <p>
 * 拉杆在列车<b>进入</b>（GROUP_ENTER）时按车种设置、<b>离开</b>（GROUP_LEAVE）时统一复位，
 * 因此信号只在列车实际压过本牌所在铁轨期间持续，不会残留。
 */
public class SignActionIsExpress extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("isexpress");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isTrainSign()) {
            return;
        }
        // 列车离开：无条件复位拉杆，避免信号残留。
        // 注意必须在 hasRailedMember() 之前处理——列车已离开本牌铁轨，该判断可能为 false，
        // 若先拦掉就再也复位不了拉杆（TC 原版 switcher 牌同样把 setLevers(false) 放在这类判断之前）。
        if (info.isAction(SignActionType.GROUP_LEAVE)) {
            info.setLevers(false);
            return;
        }
        if (!(info.isAction(SignActionType.GROUP_ENTER) || info.isAction(SignActionType.GROUP_UPDATE)) || !info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getGroup();
        if (group == null || !info.isWatchedDirection(info.getCartEnterDirection())) {
            return;
        }
        // 直达车（带导航序列）拉下拉杆，普通车 / 手动车复位
        info.setLevers(BcRouteNavigator.hasRoute(group));
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.isexpress")) {
            return false;
        }
        event.getPlayer().sendMessage(Component.text(
                "建立 isexpress 车种判别控制牌成功（直达车经过时触发拉杆）", NamedTextColor.GREEN));
        return true;
    }
}
