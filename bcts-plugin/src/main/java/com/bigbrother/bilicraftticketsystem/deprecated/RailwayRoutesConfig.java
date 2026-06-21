package com.bigbrother.bilicraftticketsystem.deprecated;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(since = "2.0.0")
public class RailwayRoutesConfig {
    public static ConfigurationNode railwayRoutes;
    private static FileConfiguration config;
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, "railway_routes_old.yml");
        config.load();
        railwayRoutes = config.getNode("railway-routes");
    }

    public static void save() {
        config.save();
    }
}
