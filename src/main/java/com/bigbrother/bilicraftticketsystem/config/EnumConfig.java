package com.bigbrother.bilicraftticketsystem.config;

import lombok.Getter;

@Getter
public enum EnumConfig {
    MAIN_CONFIG("config.yml"),
    ROUTE_MMD("routes.mmd"),
    MENU_MAIN("menu_main.yml"),
    MENU_LOCATION("menu_location.yml"),
    MENU_FILTER("menu_filter.yml"),
    MENU_TICKETBG("menu_ticketbg.yml"),
    MENU_CARD("menu_card.yml"),
    MENU_ITEMS("menuitems.yml"),
    RAILWAY_ROUTES_CONFIG("railway_routes.yml"),
    ADDON_CONFIG("addon.yml");

    private final String fileName;

    EnumConfig(String fileName) {
        this.fileName = fileName;
    }
}
