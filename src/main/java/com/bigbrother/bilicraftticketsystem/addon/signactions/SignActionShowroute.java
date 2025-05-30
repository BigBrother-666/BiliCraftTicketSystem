package com.bigbrother.bilicraftticketsystem.addon.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.MemberRemoveEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatExitEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import com.bigbrother.bilicraftticketsystem.addon.signactions.component.RouteBossbar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
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
            args = RailwayRoutesConfig.railwayRoutes.get("%s.args".formatted(splitRouteInfo[0].trim()), String.class, "RED 7 a 2 3");
        }

        String[] argsInfoSplit = args.trim().split(" ");
        if (argsInfoSplit.length != 5) {
            return;
        }

        RouteBossbar bossbar = bossbarMapping.getOrDefault(member, new RouteBossbar(splitRouteInfo[0], new RouteBossbar.Args(argsInfoSplit)));
        // 控制牌忽略直达车
        if (bossbar.getBossBar() == null || bossbar.getRouteId() == null) {
            return;
        }

        bossbar.update(argsInfoSplit, splitRouteInfo[1], splitRouteInfo[0]);
        bossbar.updateStation();

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
        String[] splitRouteInfo = signChangeActionEvent.getLine(2).trim().split(" ");
        if (splitRouteInfo.length == 2) {
            String routeId = splitRouteInfo[0];
            if (RailwayRoutesConfig.railwayRoutes.get("%s.route".formatted(routeId.trim()), String.class, null) == null) {
                signChangeActionEvent.getPlayer().sendMessage(Component.text("控制牌指定的线路id(%s)不存在".formatted(routeId), NamedTextColor.RED));
                return false;
            }
        } else {
            signChangeActionEvent.getPlayer().sendMessage(Component.text("第三行有多余的空格 或 缺少路线id或本站名参数", NamedTextColor.RED));
            return false;
        }
        signChangeActionEvent.getPlayer().sendMessage(Component.text("建立控制牌成功，该控制牌可以在boss栏显示线路信息", NamedTextColor.GREEN));
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberRemove(MemberRemoveEvent event) {
        MinecartMember<?> member = event.getMember();
        RouteBossbar bossBar = bossbarMapping.get(member);
        if (bossBar == null) {
            return;
        }
        for (Entity player : member.getEntity().getPlayerPassengers()) {
            bossBar.getBossBar().removeViewer(player);
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
    public void onMemberSeatExit(MemberBeforeSeatExitEvent event) {
        RouteBossbar bossBar = bossbarMapping.get(event.getMember());
        if (bossBar == null || bossBar.getBossBar() == null) {
            return;
        }
        // 隐藏bossbar
        bossBar.getBossBar().removeViewer(event.getEntity());
    }
}
