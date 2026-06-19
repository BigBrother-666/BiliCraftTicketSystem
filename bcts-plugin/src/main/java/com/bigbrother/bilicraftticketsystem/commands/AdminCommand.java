package com.bigbrother.bilicraftticketsystem.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.commands.argument.StatisticsType;
import com.bigbrother.bilicraftticketsystem.database.OldDatabaseMigrator;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteGraph;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.MermaidExporter;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.SwitchTrace;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_NAME;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_UUID;

public class AdminCommand {
    private final BiliCraftTicketSystem plugin;

    public AdminCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("重载配置文件")
    @Command("ticketadmin reload")
    @Permission("bcts.ticket.reload")
    public void reload(
            CommandSender commandSender
    ) {
        if (commandSender instanceof ConsoleCommandSender) {
            plugin.getComponentLogger().info(Component.text("配置文件重载中...", NamedTextColor.GOLD));
        } else {
            commandSender.sendMessage(Component.text("配置文件重载中...", NamedTextColor.GOLD));
            plugin.getComponentLogger().info(Component.text("配置文件重载中...", NamedTextColor.GOLD));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, sender -> plugin.loadConfig(commandSender));
    }

    @CommandDescription("从旧数据库 data.db 迁移交通卡与车票背景数据到新库 bcts.db（跳过已存在，可重复执行）")
    @Command("ticketadmin migrate-olddb")
    @Permission("bcts.ticket.migrate")
    public void migrateOldDb(
            CommandSender sender
    ) {
        OldDatabaseMigrator migrator = new OldDatabaseMigrator(plugin, plugin.getTrainDatabaseManager().getDs());
        if (!migrator.oldDatabaseExists()) {
            sender.sendMessage(Component.text("未找到旧数据库 data.db，无需迁移", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("开始从 data.db 迁移数据...", NamedTextColor.AQUA));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OldDatabaseMigrator.Result result = migrator.migrate();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.failed) {
                    sender.sendMessage(Component.text("迁移过程出错，请查看控制台日志", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                        "迁移完成：card_info %d 条，ticketbg_info %d 条，ticketbg_usage_info %d 条".formatted(
                                result.cardInfo, result.ticketbgInfo, result.ticketbgUsage), NamedTextColor.GREEN));
                // 刷新交通卡缓存，使迁入的卡立即可用
                com.bigbrother.bilicraftticketsystem.ticket.BCCardInfo.reloadAllCache();
            });
        });
    }

    @CommandDescription("添加菜单物品")
    @Command("ticketadmin menuitem add <menuItemId>")
    @Permission("bcts.ticket.menuitem")
    public void addMenuItem(
            Player player,
            @Argument(value = "menuItemId", description = "菜单物品ID")
            String menuItemId
    ) {
        CommonUtils.saveItemToFile(menuItemId, player.getInventory().getItemInMainHand());
        player.sendMessage(Component.text("成功保存物品" + menuItemId, NamedTextColor.GOLD));
    }

    @CommandDescription("获取菜单物品")
    @Command("ticketadmin menuitem get <menuItemId>")
    @Permission("bcts.ticket.menuitem")
    public void getMenuItem(
            Player player,
            @Argument(value = "menuItemId", description = "菜单物品ID", suggestions = "menuItemId")
            String menuItemId
    ) {
        ItemStack itemStack = CommonUtils.loadItemFromFile(menuItemId);
        if (itemStack.getType() == Material.AIR) {
            player.sendMessage(Component.text("物品不存在", NamedTextColor.RED));
            return;
        }
        player.getInventory().addItem(itemStack);
        player.sendMessage(Component.text("成功获取物品" + menuItemId, NamedTextColor.GOLD));
    }

    @CommandDescription("查看或修改乘车凭证nbt")
    @Command("ticketdebug nbt <nbtKey> [values]")
    @Permission("bcts.ticket.nbt")
    public void nbt(
            Player player,
            @Argument(value = "nbtKey", description = "nbt键", suggestions = "nbtKey")
            String nbtKey,
            @Argument(value = "values", description = "nbt值", suggestions = "nbtValue")
            @Nullable String[] values
    ) {
        // 验证主手物品是坐车凭证
        if (BCTransitPass.isBCTransitPass(player.getInventory().getItemInMainHand())) {
            CommonItemStack mainHandTicket = CommonItemStack.of(HumanHand.getItemInMainHand(player));
            CommonTagCompound nbt = mainHandTicket.getCustomData();

            if (values != null && values.length > 0) {
                // 用空格拼接参数
                String updateValue = String.join(" ", values);

                // 更新nbt
                if (nbtKey.equals(KEY_TICKET_OWNER_NAME)) {
                    Player newOwner = Bukkit.getPlayer(updateValue);
                    if (newOwner == null) {
                        player.sendMessage(Component.text("此玩家不存在（不在线）", NamedTextColor.RED));
                        return;
                    } else {
                        mainHandTicket.updateCustomData(tag -> tag.putValue(nbtKey, updateValue));
                        mainHandTicket.updateCustomData(tag -> tag.putValue(KEY_TICKET_OWNER_UUID, newOwner.getUniqueId()));
                    }
                } else if (nbtKey.equals(KEY_TICKET_OWNER_UUID)) {
                    player.sendMessage(Component.text("请使用 %s 修改持有者".formatted(KEY_TICKET_OWNER_NAME), NamedTextColor.RED));
                } else {
                    mainHandTicket.updateCustomData(tag -> tag.putValue(nbtKey, updateValue));
                }
                player.sendMessage(Component.text("成功将 %s 的值更新为 %s".formatted(nbtKey, updateValue), NamedTextColor.GREEN));
            } else {
                // 输出nbt的值
                String v = nbt.getValue(nbtKey, "");
                if (v != null && !v.isEmpty()) {
                    player.sendMessage(Component.text("%s 的值为 %s".formatted(nbtKey, v), NamedTextColor.GREEN));
                } else {
                    if (nbtKey.equals(KEY_TICKET_OWNER_UUID)) {
                        UUID uuid = nbt.getUUID(nbtKey);
                        if (uuid != null) {
                            player.sendMessage(Component.text("%s 的值为 %s".formatted(nbtKey, uuid.toString()), NamedTextColor.GREEN));
                            return;
                        }
                    }
                    player.sendMessage(Component.text("此车票没有 %s".formatted(nbtKey), NamedTextColor.RED));
                }
            }
        } else {
            player.sendMessage(Component.text("手持的物品不是车票！", NamedTextColor.RED));
        }
    }

    @CommandDescription("调试：输出当前所坐列车的信息（车型 / 线路 / 起点站 / 路线 / 导航）")
    @Command("ticketdebug traininfo")
    @Permission("bcts.ticket.debug")
    public void trainInfo(
            Player player
    ) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(player.getVehicle());
        if (member == null || member.getGroup() == null) {
            player.sendMessage(Component.text("你当前没有坐在列车上", NamedTextColor.RED));
            return;
        }
        MinecartGroup group = member.getGroup();
        TrainProperties props = group.getProperties();

        // 车型
        String type;
        if (TrainListeners.isCommonTrain(group)) {
            type = "普通车";
        } else if (TrainListeners.isInitTrain(group)) {
            type = "初始车（已发车，尚无人持凭证上车）";
        } else {
            type = "快速车（直达车）";
        }

        player.sendMessage(Component.text("========= 列车调试信息 =========", NamedTextColor.AQUA));
        player.sendMessage(Component.text("车型：", NamedTextColor.GRAY).append(Component.text(type, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("车厢数：", NamedTextColor.GRAY).append(Component.text(String.valueOf(group.size()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("速度上限：", NamedTextColor.GRAY).append(Component.text("%.2f b/t（%.2f km/h）".formatted(props.getSpeedLimit(), CommonUtils.mpt2Kph(props.getSpeedLimit())), NamedTextColor.WHITE)));

        // 列车 tag / 营运线 / 起点站（新模型 verify 依据）
        player.sendMessage(Component.text("Tags：", NamedTextColor.GRAY).append(Component.text(
                props.getTags().isEmpty() ? "(无)" : String.join(", ", props.getTags()), NamedTextColor.WHITE)));
        String lineId = BCTransitPass.getTrainLineId(group);
        player.sendMessage(Component.text("营运线 lineId：", NamedTextColor.GRAY).append(Component.text(
                lineId.isEmpty() ? "(无)" : lineId, NamedTextColor.WHITE)));
        String startStation = BCTransitPass.getTrainStartStationName(group);
        player.sendMessage(Component.text("起点站：", NamedTextColor.GRAY).append(Component.text(
                startStation.isEmpty() ? "(未记录)" : startStation, NamedTextColor.WHITE)));

        // 已应用的凭证路线（仅快速车有）
        BCTransitPass pass = TrainListeners.trainTicketInfo.get(group);
        if (pass != null && pass.getPathInfo() != null) {
            GeoRoutePath path = pass.getPathInfo();
            player.sendMessage(Component.text("行程：", NamedTextColor.GRAY).append(Component.text(
                    "%s → %s（%.2f km）".formatted(path.getStartStationName(), path.getEndStationName(), path.getDistance()), NamedTextColor.GOLD)));
            player.sendMessage(Component.text("途经车站：", NamedTextColor.GRAY).append(Component.text(
                    String.join(" → ", path.stationSequence()), NamedTextColor.WHITE)));
            // 规划路线的完整节点序列（站台 / 道岔 + 该节点驶出段 lineId），用于对照实际经过的物理道岔
            player.sendMessage(Component.text("规划节点序列（节点 | 驶出段lineId）：", NamedTextColor.GRAY));
            List<GeoNode> nodes = path.getNodes();
            List<String> seq = path.getLineIdSequence();
            for (int i = 0; i < nodes.size(); i++) {
                GeoNode n = nodes.get(i);
                String kind = n.isStation() ? ("站台 " + n.getName()) : "道岔";
                String depart = i < seq.size() ? seq.get(i) : "(终点)";
                NamedTextColor c = n.isStation() ? NamedTextColor.AQUA : NamedTextColor.WHITE;
                player.sendMessage(Component.text("  %2d. ".formatted(i), NamedTextColor.DARK_GRAY)
                        .append(Component.text(kind, c))
                        .append(Component.text(" @" + n.getId(), NamedTextColor.DARK_GRAY))
                        .append(Component.text(" → " + depart, NamedTextColor.GOLD)));
            }
            player.sendMessage(Component.text("导航序列（各道岔应选）：", NamedTextColor.GRAY).append(Component.text(
                    path.switcherLineIds().toString(), NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("行程：", NamedTextColor.GRAY).append(Component.text("(无已应用凭证路线)", NamedTextColor.WHITE)));
        }

        // 列车实际携带的导航序列（来自 train property，可能与上面凭证的不同——比如手动改过）
        List<String> liveRoute = props.get(BcRouteProperty.INSTANCE);
        player.sendMessage(Component.text("列车实际导航序列：", NamedTextColor.GRAY).append(Component.text(
                liveRoute == null || liveRoute.isEmpty() ? "(无)" : liveRoute.toString(), NamedTextColor.WHITE)));

        // 导航序列与进度
        int[] progress = BcRouteNavigator.progress(group);
        player.sendMessage(Component.text("导航进度：", NamedTextColor.GRAY).append(Component.text(
                "%d / %d 个节点".formatted(progress[0] + 1, progress[1]), NamedTextColor.WHITE)));
        String currentStep = BcRouteNavigator.currentStep(group);
        String currentSwitch = BcRouteNavigator.currentSwitchDirection(group);
        String desc = currentStep == null ? "(无 / 已走完)"
                : (currentSwitch != null ? "道岔应走出向 " + currentSwitch : "车站(站台)");
        player.sendMessage(Component.text("当前节点步骤：", NamedTextColor.GRAY).append(Component.text(
                desc, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("==============================", NamedTextColor.AQUA));
    }

    @CommandDescription("调试：开关道岔选向追踪（开启后列车每经过 bcswitcher 打印选向到控制台）")
    @Command("ticketdebug switchtrace <state>")
    @Permission("bcts.ticket.debug")
    public void switchTrace(
            CommandSender sender,
            @Argument(value = "state", description = "on / off", suggestions = "switchTraceState")
            String state
    ) {
        boolean on = state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true") || state.equals("1");
        SwitchTrace.setEnabled(on);
        sender.sendMessage(Component.text(
                "道岔选向追踪已" + (on ? "开启（每辆列车经过 bcswitcher 会打印到控制台）" : "关闭"),
                on ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    @CommandDescription("调试：把 geojson 构造的路由图与各线路子图导出为 Mermaid 文件(.mmd) 到 mermaid/ 目录")
    @Command("ticketdebug exportmmd")
    @Permission("bcts.ticket.debug")
    public void exportMmd(
            CommandSender sender
    ) {
        GeoRouteGraph graph = GeoRouteEngine.getGraph();
        if (graph == null || graph.nodeCount() == 0) {
            sender.sendMessage(Component.text("路由图为空（尚未加载 geojson），无法导出", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("正在导出 Mermaid 文件...", NamedTextColor.AQUA));
        File outDir = new File(plugin.getDataFolder(), "mermaid");
        // 文件 IO 放异步，避免卡主线程
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int files = MermaidExporter.exportAll(graph, outDir);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        "导出完成：共 %d 个文件（节点 %d，边 %d），位于 %s".formatted(
                                files, graph.nodeCount(), graph.linkCount(), outDir.getAbsolutePath()),
                        NamedTextColor.GREEN)));
            } catch (IOException e) {
                plugin.getComponentLogger().warn(Component.text("导出 Mermaid 失败：" + e.getMessage(), NamedTextColor.RED));
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        "导出失败：" + e.getMessage() + "（详见控制台）", NamedTextColor.RED)));
            }
        });
    }

    @CommandDescription("查询n天内的某类型的统计信息")
    @Command("ticketadmin statistics <statisticsType> <days>")
    @Permission("bcts.ticket.statistics")
    public void bcspawnStatistics(
            CommandSender sender,
            @Argument(value = "statisticsType", description = "统计类型", parserName = "statisticsType")
            StatisticsType type,
            @Argument(value = "days", description = "查询的天数")
            int days
    ) {
        sender.sendMessage(Component.text("查询中...", NamedTextColor.AQUA));

        switch (type) {
            case BCSPAWN:
                sender.sendMessage(plugin.getTrainDatabaseManager().getBcspawnService().getDailySpawn(days));
                break;
            case TICKET_REVENUE:
                sender.sendMessage(plugin.getTrainDatabaseManager().getRevenueService().getDailyRevenue(days));
                break;
            case TRANSIT_PASS_USAGE:
                sender.sendMessage(plugin.getTrainDatabaseManager().getTransitPassService().getDailyTransitPassUsage(days));
                break;
            default:
                sender.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
        }

        sender.sendMessage(Component.text("查询完成", NamedTextColor.AQUA));
    }
}
