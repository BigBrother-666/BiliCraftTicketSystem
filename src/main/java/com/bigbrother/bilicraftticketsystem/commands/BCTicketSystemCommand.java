package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class BCTicketSystemCommand implements CommandExecutor {
    private final BiliCraftTicketSystem plugin;

    public BCTicketSystemCommand(final @NotNull BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
        this.plugin.getCommand("ticket-system").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("该指令必须玩家执行！");
            return false;
        }
        Player player = ((Player) commandSender).getPlayer();
        if (player == null) {
            return false;
        }
        if (args.length == 0) {
            // 打开购票界面
            player.openInventory(Menu.mainMenu.inventory);
        } else {
            switch (args[0]) {
                case "menuitem":
                    subCommandMenuitem(player, args);
                    break;
                case "give":
                    subCommandGive(player, args);
                    break;
                case "reload":
                    subCommandReload();
                    break;
            }
        }
        return true;
    }

    private void subCommandReload() {
        Menu.loadMenu(plugin);
    }

    private void subCommandGive(Player player, @NotNull String[] args) {
        if (args.length == 2) {
            // 验证主手物品是车票
            // 移除物品，给予另一名玩家，修改owner
        } else {
            player.sendMessage(Component.text("指令格式有误！"));
        }
    }

    private void subCommandMenuitem(Player player, @NotNull String[] args) {
        if (args.length > 2 && args[1].equals("add")) {
            Menu.saveItemToFile(args[2], player.getInventory().getItemInMainHand());
        } else if (args.length > 2 && args[1].equals("get")) {
            ItemStack itemStack = Menu.loadItemFromFile(args[2]);
            player.getInventory().addItem(itemStack);
        } else {
            player.sendMessage(Component.text("指令格式有误！"));
        }
    }

}
