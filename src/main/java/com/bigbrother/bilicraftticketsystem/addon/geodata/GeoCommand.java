package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.walkingpoint.PRGeoTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GeoCommand implements CommandExecutor, TabCompleter {
    private PRGeoTask geoTask;
    private final BiliCraftTicketSystem plugin;

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
                if (geoTask == null) {
                    geoTask = new PRGeoTask(plugin);
                }
                if (args[0].equalsIgnoreCase("start") && args.length > 1) {
                    geoTask.startPathFinding(args[1].trim(), player);
                } else if (args[0].equalsIgnoreCase("stop")) {
                    geoTask.stopPathFinding();
                } else {
                    player.sendMessage(Component.text("指令格式错误！"));
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop");
        } else if (args.length == 2) {
            List<String> completerList = new ArrayList<>();
            for (Map.Entry<String, List<MermaidGraph.Node>> entry : TrainRoutes.graph.nodeTagMap.entrySet()) {
                for (MermaidGraph.Node node : entry.getValue()) {
                    if (node.isStation()) {
                        completerList.add(entry.getKey() + "-" + node.getRailwayDirection());
                    }
                }
            }
            return completerList.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
        }
        return List.of();
    }
}
