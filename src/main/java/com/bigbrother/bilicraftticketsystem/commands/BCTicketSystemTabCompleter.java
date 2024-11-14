package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BCTicketSystemTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (commandSender instanceof Player) {
            if (args.length == 1) {
                List<String> completerList = new ArrayList<>();
                if (commandSender.hasPermission("bcts.ticket.menuitem")) {
                    completerList.add("menuitem");
                }
                if (commandSender.hasPermission("bcts.ticket.reload")) {
                    completerList.add("reload");
                }
                if (commandSender.hasPermission("bcts.ticket.nbt")) {
                    completerList.add("nbt");
                }
                return completerList;
            } else if (args.length == 2) {
                if (args[0].equals("menuitem") && commandSender.hasPermission("bcts.ticket.menuitem")) {
                    return List.of("add", "get");
                } else if (args[0].equals("nbt") && commandSender.hasPermission("bcts.ticket.nbt")) {
                    return List.of(
                            BCTicket.KEY_TICKET_NAME,
                            BCTicket.KEY_TICKET_CREATION_TIME,
                            BCTicket.KEY_TICKET_NUMBER_OF_USES,
                            BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES,
                            BCTicket.KEY_TICKET_OWNER_UUID,
                            BCTicket.KEY_TICKET_OWNER_NAME,
                            BCTicket.KEY_TICKET_MAX_SPEED,
                            BCTicket.KEY_TICKET_ORIGIN_PRICE,
                            BCTicket.KEY_TICKET_ITEM_NAME,
                            BCTicket.KEY_TICKET_TAGS,
                            BCTicket.KEY_TICKET_START_PLATFORM_TAG
                    );
                }
            }
        }
        return List.of();
    }
}
