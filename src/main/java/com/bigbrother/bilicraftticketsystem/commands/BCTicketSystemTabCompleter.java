package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.ItemsConfig;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BCTicketSystemTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (commandSender instanceof Player player) {
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
                if (commandSender.hasPermission("bcts.ticket.font")) {
                    completerList.add("font");
                }
                if (commandSender.hasPermission("bcts.ticket.statistics")) {
                    completerList.add("statistics");
                }
                if (commandSender.hasPermission("bcts.ticket.co")) {
                    completerList.add("co");
                }
                if (commandSender.hasPermission("bcts.ticket.uploadbg")) {
                    completerList.add("uploadbg");
                }
                if (commandSender.hasPermission("bcts.ticket.adminuploadbg")) {
                    completerList.add("adminuploadbg");
                }
                if (commandSender.hasPermission("bcts.ticket.deletebg")) {
                    completerList.add("deletebg");
                }
                if (commandSender.hasPermission("bcts.ticket.open")) {
                    completerList.add("bg");
                }
                if (commandSender.hasPermission("bcts.ticket.addroute")) {
                    completerList.add("addroute");
                }
                if (commandSender.hasPermission("bcts.ticket.delroute")) {
                    completerList.add("delroute");
                }
                return completerList.stream().filter(s -> s.contains(args[0].trim())).collect(Collectors.toList());
            } else if (args.length == 2) {
                if (args[0].equals("menuitem") && commandSender.hasPermission("bcts.ticket.menuitem")) {
                    return List.of("add", "get");
                } else if (args[0].equals("nbt") && commandSender.hasPermission("bcts.ticket.nbt")) {
                    return Stream.of(
                                    BCTicket.KEY_TICKET_NAME,
                                    BCTicket.KEY_TICKET_DISPLAY_NAME,
                                    BCTicket.KEY_TICKET_CREATION_TIME,
                                    BCTicket.KEY_TICKET_NUMBER_OF_USES,
                                    BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES,
                                    BCTicket.KEY_TICKET_OWNER_UUID,
                                    BCTicket.KEY_TICKET_OWNER_NAME,
                                    BCTicket.KEY_TICKET_MAX_SPEED,
                                    BCTicket.KEY_TICKET_ORIGIN_PRICE,
                                    BCTicket.KEY_TICKET_TAGS,
                                    BCTicket.KEY_TICKET_START_PLATFORM_TAG,
                                    BCTicket.KEY_TICKET_VERSION,
                                    BCTicket.KEY_TICKET_START_STATION,
                                    BCTicket.KEY_TICKET_END_STATION,
                                    BCTicket.KEY_TICKET_DISTANCE,
                                    BCTicket.KEY_TICKET_BACKGROUND_IMAGE_PATH
                            )
                            .filter(s -> s.contains(args[1].trim()))
                            .collect(Collectors.toList());
                } else if (args[0].equals("statistics") && commandSender.hasPermission("bcts.ticket.statistics")) {
                    return List.of("ticket", "bcspawn");
                } else if (args[0].equals("co") && commandSender.hasPermission("bcts.ticket.co")) {
                    return List.of("add", "undo");
                } else if (args[0].equals("addroute") && commandSender.hasPermission("bcts.ticket.addroute") ||
                        args[0].equals("delroute") && commandSender.hasPermission("bcts.ticket.delroute")) {
                    List<String> completerList = new ArrayList<>();
                    for (String key : RailwayRoutesConfig.railwayRoutes.getKeys()) {
                        if (RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(key), "").equals(player.getUniqueId().toString())) {
                            completerList.add(key);
                        }
                    }
                    return completerList;
                }
            } else if (args.length == 3) {
                if (args[0].equals("co") && args[1].equals("add") && commandSender.hasPermission("bcts.ticket.co")) {
                    List<String> completerList = new ArrayList<>();
                    for (Map.Entry<String, List<String>> entry : TrainRoutes.getStationTagMap().entrySet()) {
                        for (String s : entry.getValue()) {
                            String[] split = s.split("-");
                            if (split.length == 3) {
                                completerList.add(entry.getKey() + "-" + split[2]);
                            }
                        }
                    }
                    return completerList.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
                } else if (args[0].equals("menuitem") && commandSender.hasPermission("bcts.ticket.menuitem")) {
                    return ItemsConfig.itemsConfig.getKeys().stream().toList();
                }
            }
        }
        return List.of();
    }
}
