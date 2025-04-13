package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

public class RailwayRoutesConfig {
    public static int expressTicketVersion;
    public static ConfigurationNode railwayRoutes;
    private static FileConfiguration config;
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.RAILWAY_ROUTES_CONFIG.getFileName());
        config.load();
        expressTicketVersion = config.get("express-ticket-version", 1);
        railwayRoutes = config.getNode("railway-routes");
    }

    public static void save() {
        config.save();
    }
}
