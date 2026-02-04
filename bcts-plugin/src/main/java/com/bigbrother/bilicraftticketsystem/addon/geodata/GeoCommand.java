package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoTask;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoWalkingPoint;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.incendo.cloud.annotations.*;

import java.util.*;

public class GeoCommand implements Listener {
    @Getter
    private final Map<UUID, PRGeoTask> taskMap = new HashMap<>();
    private final BiliCraftTicketSystem plugin;

    // 自定义路线用的起点和终点
    private boolean selectMode = false;
    private Location startRail;
    private Vector startDirection;
    private Location endRail;
    public final Set<UUID> selectModePlayers = new HashSet<>();

    public GeoCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("铁轨全网遍历")
    @Command("railgeo start")
    @Permission("bcts.railgeo")
    public void start(
            Player player,
            @Flag(value = "less-log", description = "减少日志输出")
            boolean lessLog
    ) {
        PRGeoTask geoTask = initTask(player);
        geoTask.setLessLog(lessLog);
        geoTask.startFullPathFinding();
    }

    @CommandDescription("手动停止当前遍历任务")
    @Command("railgeo stop")
    @Permission("bcts.railgeo")
    public void stop(
            Player player
    ) {
        PRGeoTask geoTask = initTask(player);
        geoTask.stopPathFindingTask();
        taskMap.remove(player.getUniqueId());
    }

    @CommandDescription("添加遍历开始节点坐标，以玩家位置的铁轨为开始坐标，面朝方向为开始方向")
    @Command("railgeo setStartPos <platformTag>")
    @Permission("bcts.railgeo")
    public void setStartPos(
            Player player,
            @Argument(value = "platformTag", description = "当前所在的站台tag", suggestions = "startPlatformTag")
            String pTag
    ) {
        plugin.getGeoDatabaseManager().upsertGeoNodeLoc(pTag, player.getLocation(), player.getLocation().getDirection());
        player.sendMessage(Component.text("成功设置节点 [%s] 的起点".formatted(pTag), NamedTextColor.GREEN));
    }

    @CommandDescription("更新新的节点（新建铁路/车站后）")
    @Command("railgeo update")
    @Permission("bcts.railgeo")
    public void update(
            Player player,
            @Flag(value = "less-log", description = "减少日志输出") boolean lessLog
    ) {
        PRGeoTask geoTask = initTask(player);
        geoTask.setLessLog(lessLog);
        geoTask.startUpdatePathFinding();
    }

    @CommandDescription("手动指定某节点的进站/出站线路")
    @Command("railgeo setLine <platformTag> <lineType>")
    @Permission("bcts.railgeo")
    public void setLine(
            Player player,
            @Argument(value = "platformTag", description = "当前所在的站台tag", suggestions = "platformTag")
            String pTag,
            @Argument(value = "lineType", description = "线路类型", parserName = "lineType")
            PRGeoWalkingPoint.LineType lineType
    ) {
        if (!lineType.equals(PRGeoWalkingPoint.LineType.LINE_IN) && !lineType.equals(PRGeoWalkingPoint.LineType.LINE_OUT)) {
            player.sendMessage(Component.text("不支持的线路类型！仅支持 %s, %s".formatted(PRGeoWalkingPoint.LineType.LINE_IN.getType(), PRGeoWalkingPoint.LineType.LINE_OUT.getType()), NamedTextColor.RED));
        }
        this.startAddLineManually(pTag, lineType, player);
    }

    @CommandDescription("删除手动指定某节点的进站/出站线路")
    @Command("railgeo delManualNode <platformTag>")
    @Permission("bcts.railgeo")
    public void delManualNode(
            Player player,
            @Argument(value = "platformTag", description = "当前所在的站台tag", suggestions = "platformTag")
            String pTag
    ) {
        int deleted = plugin.getGeoDatabaseManager().deleteGeoManualLine(pTag);
        player.sendMessage(Component.text("成功删除节点 [%s] %s 条手动指定的线路".formatted(pTag, deleted), NamedTextColor.GREEN));
    }

    private PRGeoTask initTask(Player player) {
        taskMap.putIfAbsent(player.getUniqueId(), new PRGeoTask(plugin, player));
        return taskMap.get(player.getUniqueId());
    }

    public void startAddLineManually(String platformTag, PRGeoWalkingPoint.LineType lineType, Player sender) {
        selectMode = !selectMode;
        this.selectModePlayers.add(sender.getUniqueId());
        if (selectMode) {
            sender.sendMessage(Component.text("已进入自定义线路标记模式，左键选择起始铁轨，右键选择结束铁轨，选择后再次输入此命令保存", NamedTextColor.DARK_AQUA));
        } else {
            if (startRail != null && startDirection != null && endRail != null) {
                plugin.getGeoDatabaseManager().upsertGeoManualLine(
                        platformTag,
                        lineType,
                        startRail,
                        startDirection,
                        endRail
                );
                sender.sendMessage(Component.text("成功添加线路：\n站台tag -> %s\n类型 -> %s\n起点坐标 -> %s\n起点方向向量 -> %s\n终点坐标 -> %s"
                                .formatted(
                                        platformTag,
                                        lineType.getType(),
                                        GeoUtils.formatLoc(startRail),
                                        GeoUtils.formatVector(startDirection),
                                        GeoUtils.formatLoc(endRail)
                                ),
                        NamedTextColor.GREEN
                ));
            } else {
                sender.sendMessage(Component.text("已退出自定义线路标记模式", NamedTextColor.DARK_AQUA));
            }
            startRail = null;
            startDirection = null;
            endRail = null;
            this.selectModePlayers.remove(sender.getUniqueId());
        }
    }

    @EventHandler
    public void onClickRailBlock(PlayerInteractEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (this.selectModePlayers.contains(playerUuid)) {
            event.setCancelled(true);

            Action action = event.getAction();

            if (event.getClickedBlock() == null) {
                return;
            }

            Block block = event.getClickedBlock();

            // 判断是否为铁轨
            if (!GeoUtils.isRail(block.getType())) {
                return;
            }

            Player player = event.getPlayer();
            Location loc = block.getLocation();

            if (action == Action.LEFT_CLICK_BLOCK) {
                // 左键：起点
                this.startRail = loc;
                Vector startDirection = player.getLocation().getDirection().clone();
                startDirection.setX((int) (Math.signum(startDirection.getX()) * Math.round(Math.abs(startDirection.getX()))));
                startDirection.setY(0);
                startDirection.setZ((int) (Math.signum(startDirection.getZ()) * Math.round(Math.abs(startDirection.getZ()))));
                this.startDirection = startDirection;
                player.sendMessage(Component.text("已设置遍历起点: " + GeoUtils.formatLoc(loc), NamedTextColor.DARK_AQUA));
                player.sendMessage(Component.text("已设置遍历起点方向向量: " + GeoUtils.formatVector(startDirection), NamedTextColor.DARK_AQUA));
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                // 右键：终点
                this.endRail = loc;
                player.sendMessage(Component.text("已设置遍历终点: " + GeoUtils.formatLoc(loc), NamedTextColor.DARK_AQUA));
            }
        }
    }
}
