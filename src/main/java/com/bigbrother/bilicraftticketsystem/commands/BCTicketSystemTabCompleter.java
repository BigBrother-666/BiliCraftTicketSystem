package com.bigbrother.bilicraftticketsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BCTicketSystemTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (commandSender instanceof Player) {
            if (args.length == 1) {
                return List.of("menuitem", "reload");
            } else if (args.length == 2) {
                if (args[0].equals("menuitem")) {
                    return List.of("add", "get");
                }
//                else if (args[0].equals("give")) {
//                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
//                }
            }
        }
        return List.of();
    }
}
