package com.bigbrother.bilicraftticketsystem.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_NAME;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.KEY_TICKET_OWNER_UUID;

public class BCTicketSystemCommand implements CommandExecutor {
    private final BiliCraftTicketSystem plugin;

    public BCTicketSystemCommand(final @NotNull BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
        this.plugin.getCommand("ticket").setExecutor(this);
        this.plugin.getCommand("ticket").setTabCompleter(new BCTicketSystemTabCompleter());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            subCommandReload(commandSender);
            return true;
        }

        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(Component.text("该指令必须玩家执行", NamedTextColor.RED));
            return false;
        }
        Player player = ((Player) commandSender).getPlayer();
        if (player == null) {
            return false;
        }
        if (args.length == 0) {
            if (!player.hasPermission("bcts.ticket.open")) {
                return false;
            }
            // 打开购票界面
            player.openInventory(Menu.getMenu(player).mainMenu.inventory);
        } else {
            switch (args[0]) {
                case "menuitem" -> subCommandMenuitem(player, args);
                case "nbt" -> subCommandNbt(player, args);
                case "font" -> subCommandFont(player);
            }
        }
        return true;
    }

    private void subCommandFont(Player player) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        player.sendMessage(Component.text(String.join(", ", fontNames), NamedTextColor.GREEN));
    }

    private void subCommandNbt(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.nbt")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2) {
            // 验证主手物品是车票
            if (TicketStore.isTicketItem(player.getInventory().getItemInMainHand())) {
                CommonItemStack mainHandTicket = CommonItemStack.of(HumanHand.getItemInMainHand(player));
                CommonTagCompound nbt = mainHandTicket.getCustomData();
                String cleandTagString = args[1].trim();

                if (args.length > 2 && !args[2].trim().isEmpty()) {
                    // 用空格拼接参数
                    String updateValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                    // 更新nbt
                    if (cleandTagString.equals(KEY_TICKET_OWNER_NAME)) {
                        Player newOwner = Bukkit.getPlayer(updateValue);
                        if (newOwner == null) {
                            player.sendMessage(Component.text("此玩家不存在", NamedTextColor.RED));
                            return;
                        } else {
                            mainHandTicket.updateCustomData(tag -> tag.putValue(cleandTagString, updateValue));
                            mainHandTicket.updateCustomData(tag -> tag.putValue(KEY_TICKET_OWNER_UUID, newOwner.getUniqueId()));
                        }
                    } else if (cleandTagString.equals(KEY_TICKET_OWNER_UUID)) {
                        player.sendMessage(Component.text("请通过 %s 修改车票的持有者".formatted(KEY_TICKET_OWNER_NAME), NamedTextColor.RED));
                        return;
                    } else {
                        mainHandTicket.updateCustomData(tag -> tag.putValue(cleandTagString, updateValue));
                    }
                    player.sendMessage(Component.text("成功将 %s 的值更新为 %s".formatted(cleandTagString, updateValue), NamedTextColor.GREEN));
                } else {
                    // 输出nbt的值
                    String value = nbt.getValue(cleandTagString, "");
                    if (value != null && !value.isEmpty()) {
                        player.sendMessage(Component.text("%s 的值为 %s".formatted(cleandTagString, value), NamedTextColor.GREEN));
                    } else {
                        if (cleandTagString.equals(KEY_TICKET_OWNER_UUID)) {
                            UUID uuid = nbt.getUUID(cleandTagString);
                            if (uuid != null) {
                                player.sendMessage(Component.text("%s 的值为 %s".formatted(cleandTagString, uuid.toString()), NamedTextColor.GREEN));
                                return;
                            }
                        }
                        player.sendMessage(Component.text("此车票没有 %s".formatted(cleandTagString), NamedTextColor.RED));
                    }
                }

            } else {
                player.sendMessage(Component.text("手持的物品不是车票！", NamedTextColor.RED));
            }

        } else {
            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
        }
    }

    private void subCommandReload(CommandSender commandSender) {
        if (!commandSender.hasPermission("bcts.ticket.reload")) {
            commandSender.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        commandSender.sendMessage(Component.text("配置文件重载中...", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, sender -> plugin.loadConfig(commandSender));
    }

    private void subCommandMenuitem(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.menuitem")) {
            player.sendMessage(Component.text("你没有权限使用这条命令喵~", NamedTextColor.RED));
            return;
        }
        if (args.length > 2 && args[1].equals("add")) {
            Menu.saveItemToFile(args[2], player.getInventory().getItemInMainHand());
            player.sendMessage(Component.text("成功保存物品" + args[2], NamedTextColor.GREEN));
        } else if (args.length > 2 && args[1].equals("get")) {
            ItemStack itemStack = Menu.loadItemFromFile(args[2]);
            if (itemStack.getType() == Material.AIR) {
                player.sendMessage(Component.text("物品不存在", NamedTextColor.RED));
                return;
            }
            player.getInventory().addItem(itemStack);
            player.sendMessage(Component.text("成功获取物品" + args[2], NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
        }
    }
}
