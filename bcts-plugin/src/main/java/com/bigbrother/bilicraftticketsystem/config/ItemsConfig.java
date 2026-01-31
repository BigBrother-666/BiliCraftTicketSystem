package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

public class ItemsConfig {
    public static FileConfiguration itemsConfig;

    public static void loadItemsConfig(BiliCraftTicketSystem plugin) {
        itemsConfig = new FileConfiguration(plugin, EnumConfig.MENU_ITEMS.getFileName());
        itemsConfig.load();
    }
}
