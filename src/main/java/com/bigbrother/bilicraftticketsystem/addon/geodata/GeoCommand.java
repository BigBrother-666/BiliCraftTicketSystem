package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoTask;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoWalkingPoint;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeoCommand implements CommandExecutor, TabCompleter, Listener {
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
        Objects.requireNonNull(plugin.getCommand("railgeo")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("railgeo")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("bcts.railgeo")) {
            sender.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return false;
        }

        if (sender instanceof Player player) {
            if (args.length == 0) {
                return false;
            } else {
                // 初始化
                taskMap.putIfAbsent(player.getUniqueId(), new PRGeoTask(plugin, player));
                PRGeoTask geoTask = taskMap.get(player.getUniqueId());
                switch (args[0]) {
                    case "start":
                        // 开始全网遍历
                        geoTask.setLessLog(args.length > 1 && args[1].equalsIgnoreCase("--less-log"));
                        geoTask.startFullPathFinding();
                        break;
                    case "stop":
                        // 手动停止当前任务
                        geoTask.stopPathFindingTask();
                        taskMap.remove(player.getUniqueId());
                        break;
                    case "setStartPos":
                        // 第一次遍历前，添加开始节点
                        plugin.getGeoDatabaseManager().upsertGeoNodeLoc(args[1].trim(), player.getLocation(), player.getLocation().getDirection());
                        player.sendMessage(Component.text("成功设置节点 [%s] 的起点".formatted(args[1].trim()), NamedTextColor.GREEN));
                        break;
                    case "update":
                        // 更新没遍历的节点
                        geoTask.setLessLog(args.length > 1 && args[1].equalsIgnoreCase("--less-log"));
                        geoTask.startUpdatePathFinding();
                        break;
                    case "setLine":
                        // 手动指定节点的线路
                        if (args.length > 2) {
                            PRGeoWalkingPoint.LineType lineType = PRGeoWalkingPoint.LineType.fromType(args[2].trim());
                            if (!lineType.equals(PRGeoWalkingPoint.LineType.LINE_IN) && !lineType.equals(PRGeoWalkingPoint.LineType.LINE_OUT)) {
                                player.sendMessage(Component.text("不支持的线路类型！", NamedTextColor.RED));
                                return false;
                            }
                            this.startAddLineManually(args[1].trim(), lineType, player);
                        } else {
                            player.sendMessage(Component.text("指令格式错误！", NamedTextColor.RED));
                        }
                        break;
                    case "delManualNode":
                        if (args.length > 1) {
                            int deleted = plugin.getGeoDatabaseManager().deleteGeoManualLine(args[1].trim());
                            player.sendMessage(Component.text("成功删除节点 [%s] %s 条手动指定的线路".formatted(args[1].trim(), deleted), NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("指令格式错误！", NamedTextColor.RED));
                        }
                        break;
                    default:
                        player.sendMessage(Component.text("指令格式错误！", NamedTextColor.RED));
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completerList = new ArrayList<>();
        if (args.length == 1) {
            return Stream.of("setStartPos", "start", "stop", "update", "setLine", "delManualNode").filter(s -> s.startsWith(args[0])).collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setStartPos")) {
                completerList.addAll(TrainRoutes.graph.startNodes.stream().map(MermaidGraph.Node::getPlatformTag).toList());
            } else if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("update")) {
                completerList.add("--less-log");
            } else if (args[0].equalsIgnoreCase("setLine") || args[0].equalsIgnoreCase("delManualNode")) {
                for (Map.Entry<String, List<MermaidGraph.Node>> entry : TrainRoutes.graph.nodeTagMap.entrySet()) {
                    for (MermaidGraph.Node node : entry.getValue()) {
                        if (node.isStation()) {
                            completerList.add(node.getPlatformTag());
                        }
                    }
                }
            }
            return completerList.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setLine")) {
                completerList.add(PRGeoWalkingPoint.LineType.LINE_IN.getType());
                completerList.add(PRGeoWalkingPoint.LineType.LINE_OUT.getType());
            }
            return completerList.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }
        return List.of();
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
