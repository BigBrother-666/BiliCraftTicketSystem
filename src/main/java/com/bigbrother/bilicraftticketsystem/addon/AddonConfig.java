package com.bigbrother.bilicraftticketsystem.addon;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;

import java.util.List;

public class AddonConfig {
    private static FileConfiguration addonConfig;
    public static boolean realTimeDataEnabled;

    public static void loadAddonConfig(BiliCraftTicketSystem plugin) {
        addonConfig = new FileConfiguration(plugin, EnumConfig.ADDON_CONFIG.getFileName());
        addonConfig.load();
        realTimeDataEnabled = addonConfig.get("geo-data.real-time-data", true);
    }

    public static void saveAddonConfig() {
        addonConfig.save();
    }
}
