package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

public class RailwayMapConfig {
    private static FileConfiguration railwayGeoConfig;

    public static void loadRailwayGeoConfig(BiliCraftTicketSystem plugin) {
        railwayGeoConfig = new FileConfiguration(plugin, EnumConfig.GEO_CONFIG.getFileName());
        railwayGeoConfig.load();
    }

    public static void saveRailwayGeoConfig() {
        railwayGeoConfig.save();
    }
}
