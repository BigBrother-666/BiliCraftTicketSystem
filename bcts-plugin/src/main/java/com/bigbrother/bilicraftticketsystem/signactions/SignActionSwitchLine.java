package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.signactions.component.LineTransfer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * switchline 转线控制牌：<b>收到红石信号</b>时，把当前位于本牌所在铁轨上的列车的所属线路
 * （{@link BcLineIdProperty}）改写为牌面指定的下一条线路。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [train]                      ← 红石触发，牌头不要写 +（见下）
 *   switchline
 *   &lt;下一站线路id&gt;:&lt;下一站车站名&gt;    ← 与 railway_routes.yml 转线配置同格式，站名可省略
 * </pre>
 * 第三行沿用 {@code railway_routes.yml} 中 {@code bossbar-stations} 末项的转线写法（由
 * {@link LineTransfer#parse} 解析）：{@code <线路id>:<进入站名>}，冒号后的进入站名即转线后的
 * <b>下一站</b>（可跳过目标线路靠前的车站）；只写 {@code <线路id>} 则不指定进入站，bossbar 定位到首站。
 * <p>
 * 与 platform 的自动转线（普通车到达本线终点站后按线路配置转线）互补：本牌用于<b>由红石决定</b>是否
 * 转线的场景（如同一段轨道按信号切换交路），转线时机与目标都由建筑者控制。两者共用
 * {@link LineTransfer} 的解析与 bossbar 重建逻辑，行为一致。
 * <p>
 * <b>只作用于普通车</b>：直达车（持票 / 刷卡，带导航序列）的整条路线在购票时已算好、其 lineId 与导航
 * 序列绑定，中途改写会让导航与线路不一致（bossbar / 报站 / 道岔回退选向全部错位），故直接跳过。
 * <p>
 * 由于是红石触发（{@link SignActionType#REDSTONE_ON}），事件里没有「触发列车」，列车从本牌所在铁轨
 * 上实际存在的车厢（{@link RailPiece#members()}）解析——即<b>压在牌上的列车</b>。铁轨上没有列车时
 * 什么也不做。
 */
public class SignActionSwitchLine extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("switchline");
    }

    @Override
    public void execute(SignActionEvent info) {
        // 只在红石上升沿触发（REDSTONE_ON）；断电不回滚（转线是一次性动作，回滚语义不明确）。
        if (!info.isTrainSign() || !info.isAction(SignActionType.REDSTONE_ON)) {
            return;
        }
        LineTransfer.Target target = LineTransfer.parse(info.getLine(2));
        if (target == null) {
            return;
        }
        // 目标线路必须已配置，否则不改写（避免把列车的线路标识写成不存在的线、丢失报站与选向依据）
        if (LineConfig.get(target.lineId()) == null) {
            return;
        }
        for (MinecartGroup group : trainsOnRail(info)) {
            // 直达车的 lineId 与导航序列绑定，中途改写会让两者错位，跳过
            if (BcRouteNavigator.hasRoute(group)) {
                continue;
            }
            // 已在目标线路上：无需重复转线（红石反复触发时避免重建 bossbar 抖动）
            if (target.lineId().equals(BcLineIdProperty.read(group))) {
                continue;
            }
            BcLineIdProperty.write(group, target.lineId());
            // 转入新线：重建 bossbar 并定位到进入站（与 platform 自动转线一致）
            LineTransfer.rebuildBossbar(group, target.entryStation(), true);
        }
    }

    /**
     * 取当前位于本牌所在铁轨上的所有列车（去重，一节铁轨上通常只有一列车）。
     * <p>
     * 红石触发的事件没有「触发列车」，故从铁轨实际承载的车厢反查其所属列车。
     *
     * @param info 控制牌事件
     * @return 铁轨上的列车集合；无轨 / 无车时为空集
     */
    private Set<MinecartGroup> trainsOnRail(SignActionEvent info) {
        Set<MinecartGroup> groups = new LinkedHashSet<>();
        RailPiece rail = info.getRailPiece();
        if (rail == null || rail.isNone()) {
            return groups;
        }
        List<MinecartMember<?>> members = rail.members();
        if (members == null) {
            return groups;
        }
        for (MinecartMember<?> member : members) {
            MinecartGroup group = member.getGroup();
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.switchline")) {
            return false;
        }
        LineTransfer.Target target = LineTransfer.parse(event.getLine(2));
        if (target == null || target.entryStation() == null || target.entryStation().isEmpty()) {
            event.getPlayer().sendMessage(Component.text(
                    "switchline 控制牌格式错误，第三行需要 <下一站线路id>:<下一站车站名>", NamedTextColor.RED));
            return false;
        }
        // 线路不存在只警告不拒绝：可能先建牌后配线路
        if (LineConfig.get(target.lineId()) == null) {
            event.getPlayer().sendMessage(Component.text(
                    "警告：线路 %s 尚未在 railway_routes.yml 中配置".formatted(target.lineId()), NamedTextColor.YELLOW));
        }
        event.getPlayer().sendMessage(Component.text(
                "建立 switchline 转线控制牌成功（目标线路：%s，下一站：%s）".formatted(target.lineId(), target.entryStation()),
                NamedTextColor.GREEN));
        return true;
    }
}
