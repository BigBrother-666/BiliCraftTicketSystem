package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class GeoCommand {
    private final BiliCraftTicketSystem plugin;

    public GeoCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("遍历所有已登记线路起点，按线路分文件产出 geojson；--ignore 后跟若干 lineId 表示不遍历这些线")
    @Command("railgeo walkAll")
    @Permission("bcts.railgeo")
    public void walkAll(
            CommandSender commandSender,
            @Flag(value = "ignore", description = "不遍历的线路 id（可跟多个，空格分隔），既不展开指向这些线的道岔分支，也不校验其车站完整性", suggestions = "lineIds")
            @Nullable String[] ignore
    ) {
        Set<String> ignoreLineIds = new LinkedHashSet<>();
        if (ignore != null) {
            for (String token : ignore) {
                if (token != null && !token.isEmpty()) {
                    ignoreLineIds.add(token);
                }
            }
        }
        // 校验被忽略的线路 id 都存在，避免拼写错误静默无效
        for (String lineId : ignoreLineIds) {
            if (LineConfig.get(lineId) == null) {
                commandSender.sendMessage(Component.text("线路 [%s] 不存在。".formatted(lineId), NamedTextColor.RED));
                return;
            }
        }
        new GeoTraversalTask(plugin, commandSender, Set.of(), ignoreLineIds).runAll();
    }

    @CommandDescription("只遍历一条或多条线路及与其直接相连的联络线，增量更新这些线与 contact 的 geojson")
    @Command("railgeo walk <lineIds>")
    @Permission("bcts.railgeo")
    public void walk(
            CommandSender commandSender,
            @Greedy
            @Argument(value = "lineIds", description = "线路 id（多个用空格分隔）", suggestions = "lineIds")
            String lineIds
    ) {
        // 空格分隔多个 lineId，去重保序
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (String token : lineIds.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                targets.add(token);
            }
        }
        if (targets.isEmpty()) {
            commandSender.sendMessage(Component.text("请至少指定一个线路 id。", NamedTextColor.RED));
            return;
        }
        for (String lineId : targets) {
            if (LineConfig.get(lineId) == null) {
                commandSender.sendMessage(Component.text("线路 [%s] 不存在。".formatted(lineId), NamedTextColor.RED));
                return;
            }
        }
        new GeoTraversalTask(plugin, commandSender, targets).runLine();
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
        if (!checkLineSystemMember(player, lineId)) {
            return;
        }
        plugin.getGeoDatabaseManager().upsertGeoNodeLoc(lineId, player.getLocation(), player.getLocation().getDirection());
        player.sendMessage(Component.text("成功设置线路 [%s] 的遍历起点".formatted(lineId), NamedTextColor.GREEN));
    }

    @CommandDescription("删除某线路已登记的遍历起点")
    @Command("railgeo delStartPos <lineId>")
    @Permission("bcts.railgeo")
    public void delStartPos(
            CommandSender sender,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        if (!sender.hasPermission("bcts.bypass") || sender instanceof Player player && !checkLineSystemMember(player, lineId)) {
            return;
        }
        int deleted = plugin.getGeoDatabaseManager().deleteGeoNodeLoc(lineId);
        sender.sendMessage(Component.text("成功删除线路 [%s] 的遍历起点 %s 条".formatted(lineId, deleted), NamedTextColor.GREEN));
    }

    /**
     * 校验玩家是否为该线路所属铁路系统的成员。设置 / 删除遍历起点前调用，避免非本系统成员改动线路数据。
     *
     * @param player 操作玩家
     * @param lineId 线路 id
     * @return true 表示校验通过（是成员，可继续）；false 表示已向玩家提示原因，调用方应直接返回
     */
    private boolean checkLineSystemMember(Player player, String lineId) {
        LineInfo line = LineConfig.get(lineId);
        if (line == null) {
            player.sendMessage(Component.text("线路 [%s] 不存在。".formatted(lineId), NamedTextColor.RED));
            return false;
        }
        String systemId = line.getRailwaySystemId();
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        if (system == null) {
            player.sendMessage(Component.text(
                    "线路 [%s] 所属铁路系统 [%s] 不存在，无法校验权限。".formatted(lineId, systemId),
                    NamedTextColor.RED));
            return false;
        }
        if (!system.isMember(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "你不是该线路所属铁路系统 [%s] 的成员，无权操作其遍历起点。".formatted(systemId),
                    NamedTextColor.RED));
            return false;
        }
        return true;
    }
}
