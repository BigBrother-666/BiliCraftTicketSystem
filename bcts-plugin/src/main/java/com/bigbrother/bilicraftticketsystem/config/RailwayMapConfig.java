package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

public class RailwayMapConfig {
    private static FileConfiguration railwayMapConfig;
    public static boolean realTimeDataEnabled;

    public static void loadRailwayMapConfig(BiliCraftTicketSystem plugin) {
        railwayMapConfig = new FileConfiguration(plugin, EnumConfig.ADDON_CONFIG.getFileName());
        railwayMapConfig.load();
        realTimeDataEnabled = railwayMapConfig.get("geo-data.real-time-data", true);
    }

    public static void saveAddonConfig() {
        railwayMapConfig.save();
    }
}
