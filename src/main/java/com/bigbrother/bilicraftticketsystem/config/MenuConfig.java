package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class MenuConfig {
    @Getter
    @NotNull
    private static FileConfiguration filterMenuConfig;
    @Getter
    @NotNull
    private static FileConfiguration locationMenuConfig;
    @Getter
    @NotNull
    private static FileConfiguration mainMenuConfig;

    public static void loadMenuConfig(BiliCraftTicketSystem plugin) {
        filterMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_FILTER.getFileName());
        filterMenuConfig.load();
        locationMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_LOCATION.getFileName());
        locationMenuConfig.load();
        mainMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_MAIN.getFileName());
        mainMenuConfig.load();
    }
}
