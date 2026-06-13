package com.bigbrother.bilicraftticketsystem.config;

import lombok.Getter;

@Getter
public enum EnumConfig {
    MAIN_CONFIG("config.yml"),
    MENU_MAIN("menu_main.yml"),
    MENU_LOCATION("menu_location.yml"),
    MENU_FILTER("menu_filter.yml"),
    MENU_TICKETBG("menu_ticketbg.yml"),
    MENU_CARD("menu_card.yml"),
    MENU_ITEMS("menuitems.yml"),
    ADDON_CONFIG("railway_map.yml"),
    ROUTES_CONFIG("routes.yml"),
    RAILWAY_SYSTEM_CONFIG("railway_system.yml");

    private final String fileName;

    EnumConfig(String fileName) {
        this.fileName = fileName;
    }
}
