package com.bigbrother.bilicraftticketsystem.config;

import lombok.Getter;

@Getter
public enum EnumMenu {
    MAIN("menu_main.yml"), LOCATION("menu_location.yml"), ITEMS("menuitems.yml");

    private final String fileName;

    EnumMenu(String fileName) {
        this.fileName = fileName;
    }
}
