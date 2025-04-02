package com.bigbrother.bilicraftticketsystem.menu;

import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

@Data
public class PlayerOption {
    private Component startStation;
    private Component endStation;
    private double speed;
    private int uses;
    private boolean searchedFlag = false;

    public PlayerOption() {
        this.startStation = Component.text("未选择起始站", NamedTextColor.RED);
        this.endStation = Component.text("未选择终到站", NamedTextColor.RED);
        this.speed = 4.0;
        this.uses = 1;
    }

    public String getStartStationString() {
        String str = PlainTextComponentSerializer.plainText().serialize(startStation);
        if (str.startsWith("[") && str.endsWith("]")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    public String getEndStationString() {
        String str = PlainTextComponentSerializer.plainText().serialize(endStation);
        if (str.startsWith("[") && str.endsWith("]")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    public boolean canSearch() {
        return !getStartStationString().contains("未选择") && !getEndStationString().contains("未选择");
    }
}
