package com.bigbrother.bilicraftticketsystem.commands;

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
            if (args[0].equals("menuitem")) {
                subCommandMenuitem(player, args);
                //                case "give":
//                    subCommandGive(player, args);
//                    break;
            }
        }
        return true;
    }

    private void subCommandReload(CommandSender commandSender) {
        if (!commandSender.hasPermission("bcts.ticket.reload")) {
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, plugin::loadConfig, 20);
        commandSender.sendMessage(Component.text("配置文件重载中...", NamedTextColor.GREEN));
    }

//    private void subCommandGive(Player player, @NotNull String[] args) {
//        if (!player.hasPermission("bcts.ticket.give")) {
//            return;
//        }
//        if (args.length == 2) {
//            // 验证主手物品是车票
//            if (TicketStore.isTicketItem(player.getInventory().getItemInMainHand())) {
//                // 移除物品，给予另一名玩家，修改owner
//                Player other = Bukkit.getPlayer(args[1]);
//            } else {
//                player.sendMessage(Component.text("手持的物品不是车票！", NamedTextColor.RED));
//            }
//        } else {
//            player.sendMessage(Component.text("指令格式有误", NamedTextColor.RED));
//        }
//    }

    private void subCommandMenuitem(Player player, @NotNull String[] args) {
        if (!player.hasPermission("bcts.ticket.menuitem")) {
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
