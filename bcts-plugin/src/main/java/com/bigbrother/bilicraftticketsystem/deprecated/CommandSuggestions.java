package com.bigbrother.bilicraftticketsystem.deprecated;

import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(since = "2.0.0")
public class CommandSuggestions {
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
}
