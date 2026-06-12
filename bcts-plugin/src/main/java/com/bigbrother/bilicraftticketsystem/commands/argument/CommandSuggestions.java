package com.bigbrother.bilicraftticketsystem.commands.argument;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
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

import java.util.List;

public class CommandSuggestions {
    @Suggestions("lineId")
    public List<String> lineIdSuggestions(CommandContext<C> context, CommandInput input) {
        return LineConfig.getNormalLineIds();
    }

    @Suggestions("switchTraceState")
    public List<String> switchTraceStateSuggestions(CommandContext<C> context, CommandInput input) {
        return List.of("on", "off");
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
                BCTicket.KEY_TICKET_START_STATION,
                BCTicket.KEY_TICKET_END_STATION,
                BCTicket.KEY_TICKET_DISTANCE,
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

    @Suggestions("cardUUID")
    public List<String> cardUUIDSuggestions(CommandContext<Player> context, CommandInput input) {
        return BCCardInfo.cache.keySet().stream().toList();
    }
}
