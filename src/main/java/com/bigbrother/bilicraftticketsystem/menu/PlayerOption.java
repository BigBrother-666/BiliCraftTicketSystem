package com.bigbrother.bilicraftticketsystem.menu;

import com.bigbrother.bilicraftticketsystem.Utils;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

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

    public Component getStartStation() {
        return startStation.decoration(TextDecoration.ITALIC, true);
    }

    public Component getEndStation() {
        return endStation.decoration(TextDecoration.ITALIC, true);
    }

    public String getStartStationString() {
        return Utils.component2Str(startStation);
    }

    public String getEndStationString() {
        return Utils.component2Str(endStation);
    }

    public boolean canSearch() {
        return !getStartStationString().contains("未选择") && !getEndStationString().contains("未选择");
    }
}
