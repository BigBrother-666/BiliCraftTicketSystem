package com.bigbrother.bilicraftticketsystem.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.commands.argument.StatisticsType;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_NAME;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_UUID;

public class AdminCommand {
    private final BiliCraftTicketSystem plugin;

    public AdminCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("重载配置文件")
    @Command("ticket reload")
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

    @CommandDescription("添加菜单物品")
    @Command("ticket menuitem add <menuItemId>")
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
    @Command("ticket menuitem get <menuItemId>")
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
    @Command("ticket nbt <nbtKey> [values]")
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

    @CommandDescription("查询系统注册的字体列表")
    @Command("ticket font")
    @Permission("bcts.ticket.font")
    public void getFont(
            CommandSender sender
    ) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        sender.sendMessage(Component.text(String.join(", ", fontNames), NamedTextColor.GREEN));
    }

    @CommandDescription("从CoreProtect数据库导入发车信息，指针对准发车按钮输入此指令")
    @Command("ticket co add <platformTag>")
    @Permission("bcts.ticket.co")
    public void co(
            Player player,
            @Argument(value = "platformTag", description = "当前所在的站台tag", parserName = "platformTag")
            MermaidGraph.Node platformTag
    ) {
        CoreProtectAPI coreProtectAPI = plugin.getCoreProtectAPI();
        if (coreProtectAPI == null) {
            player.sendMessage(Component.text("未检测到CoreProtect插件！", NamedTextColor.RED));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null && (targetBlock.getType().toString().toUpperCase().endsWith("BUTTON"))) {
            int cnt = 0;
            java.util.List<String[]> resultList = coreProtectAPI.blockLookup(targetBlock, (int) (System.currentTimeMillis() / 1000L));
            List<String> dateTime = new ArrayList<>();
            for (String[] s : resultList) {
                CoreProtectAPI.ParseResult parsed = coreProtectAPI.parseResult(s);
                if (parsed.getActionId() == 2) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getDefault());
                    cnt += 1;
                    dateTime.add(sdf.format(new Timestamp(parsed.getTimestamp())));
                }
            }
            plugin.getTrainDatabaseManager().getBcspawnService().addBcspawnInfo(platformTag, dateTime);
            player.sendMessage(Component.text("成功添加 %d 条数据".formatted(cnt), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("目标方块不是按钮！", NamedTextColor.RED));
        }
    }

    @CommandDescription("查询n天内的某类型的统计信息")
    @Command("ticket statistics <statisticsType> <days>")
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
                sender.sendMessage(plugin.getTrainDatabaseManager().getTransitPassService().getDailyRevenue(days));
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
