package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Getter;

public class MenuConfig {
    @Getter
    private static FileConfiguration locationMenuConfig;
    @Getter
    private static FileConfiguration mainMenuConfig;
    @Getter
    private static FileConfiguration ticketbgMenuConfig;
    @Getter
    private static FileConfiguration cardMenuConfig;
    @Getter
    private static FileConfiguration systemMenuConfig;

    public static void loadMenuConfig(BiliCraftTicketSystem plugin) {
        locationMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_LOCATION.getFileName());
        locationMenuConfig.load();
        mainMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_MAIN.getFileName());
        mainMenuConfig.load();
        ticketbgMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_TICKETBG.getFileName());
        ticketbgMenuConfig.load();
        cardMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_CARD.getFileName());
        cardMenuConfig.load();
        systemMenuConfig = new FileConfiguration(plugin, EnumConfig.MENU_SYSTEM.getFileName());
        systemMenuConfig.load();
    }
}
