package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import java.util.HashMap;
import java.util.UUID;

public class RouteCommand {
    private final BiliCraftTicketSystem plugin;
    // 处于添加路径状态的玩家，uuid-routeID
    @Getter
    private final HashMap<UUID, String> addRouteMode = new HashMap<>();

    public RouteCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("添加ShowRoute控制牌线路")
    @Command("ticket addroute <routeID>")
    @Permission("bcts.ticket.addroute")
    public void addRoute(
            Player player,
            @Argument(value = "routeID", description = "线路ID") String routeID
    ) {
        if (addRouteMode.containsKey(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("当前正在处于添加路线模式。", NamedTextColor.YELLOW)));
            return;
        }

        if (RailwayRoutesConfig.railwayRoutes.contains(routeID)) {
            if (RailwayRoutesConfig.railwayRoutes.contains("%s.owner".formatted(routeID)) &&
                    !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID), "").isEmpty() &&
                    !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID)).equals(player.getUniqueId().toString())
            ) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不能修改其他玩家添加的路线。", NamedTextColor.RED)));
                return;
            }
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("开始修改路线 %s，所有输入不需要添加 / ".formatted(routeID), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("开始添加路线 %s，所有输入不需要添加 / ".formatted(routeID), NamedTextColor.GREEN)));
        }
        addRouteMode.put(player.getUniqueId(), routeID);
        RailwayRoutesConfig.railwayRoutes.set("%s.owner".formatted(routeID), player.getUniqueId().toString());
        player.sendMessage(Component.text("step1: 请输入到站时的显示内容，可用占位符{station}，表示当前车站名：", NamedTextColor.AQUA));
    }

    @CommandDescription("删除ShowRoute控制牌线路")
    @Command("ticket delroute <routeID>")
    @Permission("bcts.ticket.delroute")
    public void delRoute(
            Player player,
            @Argument(value = "routeID", description = "线路ID", suggestions = "routeID") String routeID
    ) {
        if (RailwayRoutesConfig.railwayRoutes.contains(routeID)) {
            // owner 存在且非空且和当前玩家uuid不同
            if (RailwayRoutesConfig.railwayRoutes.contains("%s.owner".formatted(routeID)) &&
                    !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID), "").isEmpty() &&
                    !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID)).equals(player.getUniqueId().toString())
            ) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不能删除其他玩家添加的路线。", NamedTextColor.RED)));
                return;
            }
            RailwayRoutesConfig.railwayRoutes.remove(routeID);
            RailwayRoutesConfig.save();
            RailwayRoutesConfig.load(plugin);
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("成功删除路径 %s".formatted(routeID), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不存在id为 %s 的路径".formatted(routeID), NamedTextColor.RED)));
        }
    }
}
