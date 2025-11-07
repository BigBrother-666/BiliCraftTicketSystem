package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

import java.io.File;

public class AddonConfig {
    public static boolean realTimeDataEnabled;

    public static void loadAddonConfig(BiliCraftTicketSystem plugin) {
        FileConfiguration addonConfig = new FileConfiguration(plugin, EnumConfig.ADDON_CONFIG.getFileName());
        addonConfig.load();
        realTimeDataEnabled = addonConfig.get("geo-data.real-time-data", true);
    }
}
