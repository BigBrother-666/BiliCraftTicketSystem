package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.MemberRemoveEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatExitEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbar;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public class SignActionShowroute extends SignAction implements Listener {
    public static final Map<MinecartMember<?>, RouteBossbar> bossbarMapping = new HashMap<>();

    @Override
    public boolean match(SignActionEvent signActionEvent) {
        return signActionEvent.isType("showroute");
    }

    public void sendBossbar(SignActionEvent info, MinecartMember<?> member) {
        String routeInfo = info.getLine(2);
        if (routeInfo == null || routeInfo.isEmpty()) {
            return;
        }
        String[] splitRouteInfo = routeInfo.trim().split(" ");
        if (splitRouteInfo.length != 2) {
            return;
        }

        String args = info.getLine(3);
        if (args == null || args.isEmpty()) {
            args = MainConfig.railwayRoutes.get("%s.args".formatted(splitRouteInfo[0].trim()), String.class, "RED 7 a 2 3");
        }

        String[] argsInfoSplit = args.trim().split(" ");
        if (argsInfoSplit.length != 5) {
            return;
        }

        RouteBossbar bossbar = bossbarMapping.getOrDefault(member, new RouteBossbar(splitRouteInfo[0], new RouteBossbar.Args(argsInfoSplit)));
        bossbar.update(argsInfoSplit, splitRouteInfo[1], splitRouteInfo[0]);
        // 控制牌忽略直达车
        if (bossbar.getBossBar() == null || bossbar.getRouteId() == null) {
            return;
        }

        // 发送bossbar
        bossbarMapping.putIfAbsent(member, bossbar);
        for (Player passenger : member.getEntity().getPlayerPassengers()) {
            bossbar.getBossBar().addViewer(passenger);
        }
    }

    @Override
    public void execute(SignActionEvent signActionEvent) {
        if (signActionEvent.isTrainSign() && signActionEvent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            if (!signActionEvent.hasRailedMember() || !signActionEvent.isPowered()) {
                return;
            }
            for (MinecartMember<?> minecartMember : signActionEvent.getGroup()) {
                sendBossbar(signActionEvent, minecartMember);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent signChangeActionEvent) {
        // 检查权限和格式
        if (!signChangeActionEvent.getPlayer().hasPermission("bcts.buildsign.showroute")) {
            return false;
        }
        signChangeActionEvent.getPlayer().sendMessage(Component.text("建立控制牌成功，该控制牌可以在boss栏显示线路信息", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public boolean canSupportRC() {
        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberRemove(MemberRemoveEvent event) {
        MinecartMember<?> member = event.getMember();
        BossBar bossBar = bossbarMapping.get(member).getBossBar();
        for (Player player : member.getEntity().getPlayerPassengers()) {
            bossBar.removeViewer(player);
        }

        bossbarMapping.remove(member);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberSeatEnter(MemberSeatEnterEvent event) {
        // 显示bossbar
        if (event.getEntity() instanceof Player player && bossbarMapping.containsKey(event.getMember())) {
            bossbarMapping.get(event.getMember()).getBossBar().addViewer(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberSeatExit(MemberSeatExitEvent event) {
        RouteBossbar bossBar = bossbarMapping.get(event.getMember());
        if (bossBar == null || bossBar.getBossBar() == null) {
            return;
        }
        // 隐藏bossbar
        bossBar.getBossBar().removeViewer(event.getEntity());
    }
}
