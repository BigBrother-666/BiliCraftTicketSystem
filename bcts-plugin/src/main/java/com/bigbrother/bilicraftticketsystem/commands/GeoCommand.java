package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.*;

public class GeoCommand {
    private final BiliCraftTicketSystem plugin;

    public GeoCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("遍历所有已登记线路起点，按线路分文件产出 geojson")
    @Command("railgeo walkAll")
    @Permission("bcts.railgeo")
    public void walkAll(
            CommandSender commandSender
    ) {
        new GeoTraversalTask(plugin, commandSender).runAll();
    }

    @CommandDescription("停止当前正在进行的铁轨遍历任务")
    @Command("railgeo stopWalk")
    @Permission("bcts.railgeo")
    public void stopWalk(
            CommandSender commandSender
    ) {
        GeoTraversalTask.stopWalk(commandSender);
    }

    @CommandDescription("登记某线路的遍历起点，以玩家所在铁轨为起点坐标、面朝方向为起点方向")
    @Command("railgeo setStartPos <lineId>")
    @Permission("bcts.railgeo")
    public void setStartPos(
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        plugin.getGeoDatabaseManager().upsertGeoNodeLoc(lineId, player.getLocation(), player.getLocation().getDirection());
        player.sendMessage(Component.text("成功设置线路 [%s] 的遍历起点".formatted(lineId), NamedTextColor.GREEN));
    }

    @CommandDescription("删除某线路已登记的遍历起点")
    @Command("railgeo delStartPos <lineId>")
    @Permission("bcts.railgeo")
    public void delStartPos(
            CommandSender commandSender,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        int deleted = plugin.getGeoDatabaseManager().deleteGeoNodeLoc(lineId);
        commandSender.sendMessage(Component.text("成功删除线路 [%s] 的遍历起点 %s 条".formatted(lineId, deleted), NamedTextColor.GREEN));
    }
}
