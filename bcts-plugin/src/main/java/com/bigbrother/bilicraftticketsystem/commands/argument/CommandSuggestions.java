package com.bigbrother.bilicraftticketsystem.commands.argument;

import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.ItemsConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCCardInfo;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.checkerframework.checker.units.qual.C;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommandSuggestions {
    @Suggestions("platformTag")
    public List<String> pTagSuggestions(CommandContext<C> context, CommandInput input) {
        List<String> completerList = new ArrayList<>();
        for (Map.Entry<String, List<MermaidGraph.Node>> entry : TrainRoutes.graph.nodeTagMap.entrySet()) {
            for (MermaidGraph.Node node : entry.getValue()) {
                if (node.isStation()) {
                    completerList.add(entry.getKey() + "-" + node.getRailwayDirection());
                }
            }
        }
        return completerList;
    }

    @Suggestions("startPlatformTag")
    public List<String> startPTagSuggestions(CommandContext<C> context, CommandInput input) {
        return TrainRoutes.graph.startNodes.stream().map(MermaidGraph.Node::getPlatformTag).toList();
    }

    @Suggestions("lineType")
    public List<String> lineTypeSuggestions(CommandContext<C> context, CommandInput input) {
        return List.of("line_in", "line_out");
    }

    @Suggestions("menuItemId")
    public List<String> menuItemIdSuggestions(CommandContext<C> context, CommandInput input) {
        return ItemsConfig.itemsConfig.getKeys().stream().toList();
    }

    @Suggestions("nbtKey")
    public List<String> nbtKeySuggestions(CommandContext<C> context, CommandInput input) {
        return List.of(
                BCTransitPass.KEY_TRANSIT_PASS_PLUGIN,
                BCTransitPass.KEY_TRANSIT_PASS_TYPE,
                BCTransitPass.KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH,
                BCTicket.KEY_TICKET_NAME,
                BCTicket.KEY_TICKET_CREATION_TIME,
                BCTicket.KEY_TICKET_EXPIRATION_TIME,
                BCTicket.KEY_TICKET_NUMBER_OF_USES,
                BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES,
                BCTicket.KEY_TICKET_OWNER_UUID,
                BCTicket.KEY_TICKET_OWNER_NAME,
                BCTicket.KEY_TICKET_MAX_SPEED,
                BCTicket.KEY_TICKET_ORIGIN_PRICE,
                BCTicket.KEY_TICKET_TAGS,
                BCTicket.KEY_TICKET_START_PLATFORM_TAG,
                BCTicket.KEY_TICKET_START_STATION,
                BCTicket.KEY_TICKET_END_STATION,
                BCCard.KEY_CARD_UUID,
                BCCard.KEY_CARD_INIT_FLAG
        );
    }

    @Suggestions("nbtValue")
    public List<String> nbtValueSuggestions(CommandContext<C> context, CommandInput input) {
        String i = input.input().trim();
        String[] split = i.split(" ");
        if (split.length >= 3) {
            String key = split[2];
            if (key.trim().equals(BCTicket.KEY_TICKET_OWNER_NAME)) {
                String current = input.readString().toLowerCase();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(current)).toList();
            }
        }
        return List.of();
    }

    @Suggestions("statisticsType")
    public List<String> statisticsTypeSuggestions(CommandContext<C> context, CommandInput input) {
        return StatisticsType.getNameList();
    }

    @Suggestions("routeID")
    public List<String> routeIDSuggestions(CommandContext<Player> context, CommandInput input) {
        Player sender = context.sender();
        List<String> completerList = new ArrayList<>();
        for (String key : RailwayRoutesConfig.railwayRoutes.getKeys()) {
            if (RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(key), "").equals(sender.getUniqueId().toString())) {
                completerList.add(key);
            }
        }
        return completerList;
    }

    @Suggestions("cardUUID")
    public List<String> cardUUIDSuggestions(CommandContext<Player> context, CommandInput input) {
        return BCCardInfo.cache.keySet().stream().toList();
    }
}
