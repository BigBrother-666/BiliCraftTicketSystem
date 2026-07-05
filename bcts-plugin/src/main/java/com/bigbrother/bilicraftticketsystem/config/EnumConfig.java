package com.bigbrother.bilicraftticketsystem.config;

import lombok.Getter;

@Getter
public enum EnumConfig {
    MAIN_CONFIG("config.yml"),
    MESSAGES_CONFIG("messages.yml"),
    WEB_CONFIG("config_map.yml"),

    MENU_MAIN("menu_main.yml"),
    MENU_LOCATION("menu_location.yml"),
    MENU_TICKETBG("menu_ticketbg.yml"),
    MENU_CARD("menu_card.yml"),
    MENU_SYSTEM("menu_system.yml"),
    MENU_ITEMS("menuitems.yml"),
    ICON_ITEMS("iconitems.yml"),

    RAILWAY_ROUTES_CONFIG("railway_routes.yml"),
    RAILWAY_SYSTEM_CONFIG("railway_system.yml");

    private final String fileName;

    EnumConfig(String fileName) {
        this.fileName = fileName;
    }
}
