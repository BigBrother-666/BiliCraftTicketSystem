package com.bigbrother.bilicraftticketsystem.route.geodata;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
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
            Player player
    ) {
        new GeoTraversalTask(plugin, player).runAll();
    }

    @CommandDescription("单线调试遍历：从玩家当前位置和朝向起步，按指定线路 id 走一条线")
    @Command("railgeo walk <lineId>")
    @Permission("bcts.railgeo")
    public void walk(
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        Block rail = findRailAt(player.getLocation());
        if (rail == null) {
            player.sendMessage(Component.text("脚下/附近没有找到铁轨", NamedTextColor.RED));
            return;
        }
        new GeoTraversalTask(plugin, player).runSingle(rail, horizontalDirection(player.getLocation().getDirection()), lineId);
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
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        int deleted = plugin.getGeoDatabaseManager().deleteGeoNodeLoc(lineId);
        player.sendMessage(Component.text("成功删除线路 [%s] 的遍历起点 %s 条".formatted(lineId, deleted), NamedTextColor.GREEN));
    }

    /**
     * 在玩家位置寻找铁轨方块（脚下或下方一格）。
     *
     * @param loc 玩家位置
     * @return 铁轨方块，找不到返回 null
     */
    private Block findRailAt(Location loc) {
        Block block = loc.getBlock();
        if (GeoUtils.isRail(block.getType())) {
            return block;
        }
        Block below = block.getRelative(0, -1, 0);
        if (GeoUtils.isRail(below.getType())) {
            return below;
        }
        return null;
    }

    /**
     * 把朝向向量规整为水平主轴方向。
     *
     * @param dir 原始朝向
     * @return 水平方向向量
     */
    private Vector horizontalDirection(Vector dir) {
        Vector d = dir.clone();
        d.setY(0);
        d.setX((int) (Math.signum(d.getX()) * Math.round(Math.abs(d.getX()))));
        d.setZ((int) (Math.signum(d.getZ()) * Math.round(Math.abs(d.getZ()))));
        if (d.lengthSquared() == 0) {
            d.setX(1);
        }
        return d;
    }
}
