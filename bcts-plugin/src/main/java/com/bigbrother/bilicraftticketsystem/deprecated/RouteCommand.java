package com.bigbrother.bilicraftticketsystem.deprecated;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(since = "2.0.0")
public class RouteCommand {
    private final BiliCraftTicketSystem plugin;

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
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(Component.text("当前正在进行配置向导，请先完成或退出。", NamedTextColor.YELLOW)));
            return;
        }

        boolean editMode = RailwayRoutesConfig.railwayRoutes.contains(routeID);
        if (editMode &&
                RailwayRoutesConfig.railwayRoutes.contains("%s.owner".formatted(routeID)) &&
                !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID), "").isEmpty() &&
                !RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeID)).equals(player.getUniqueId().toString())
        ) {
            player.sendMessage(MainConfig.prefix.append(Component.text("不能修改其他玩家添加的路线。", NamedTextColor.RED)));
            return;
        }

        WizardManager.start(new ShowrouteWizard(player, routeID, editMode));
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
                player.sendMessage(MainConfig.prefix.append(Component.text("不能删除其他玩家添加的路线。", NamedTextColor.RED)));
                return;
            }
            RailwayRoutesConfig.railwayRoutes.remove(routeID);
            RailwayRoutesConfig.save();
            RailwayRoutesConfig.load(plugin);
            player.sendMessage(MainConfig.prefix.append(Component.text("成功删除路径 %s".formatted(routeID), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(MainConfig.prefix.append(Component.text("不存在id为 %s 的路径".formatted(routeID), NamedTextColor.RED)));
        }
    }
}
