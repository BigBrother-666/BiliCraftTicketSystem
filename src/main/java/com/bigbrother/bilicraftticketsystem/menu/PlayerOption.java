package com.bigbrother.bilicraftticketsystem.menu;

import com.bigbrother.bilicraftticketsystem.Utils;
import lombok.Data;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@Data
public class PlayerOption {
    public static final String NOT_AVALIABLE = "N/A";
    public static final Component NOT_AVALIABLE_COMPONENT = Component.text(NOT_AVALIABLE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET);
    public static final String NOT_AVALIABLE_MM = Utils.component2Str(NOT_AVALIABLE_COMPONENT);

    @Getter
    protected Component startStation;
    @Getter
    protected Component endStation;
    protected String startStationString;
    protected String endStationString;

    protected double speed;
    private int uses;
    private boolean searchedFlag = false;


    public PlayerOption(String mmStartStation, String mmEndStation) {
        this(Utils.mmStr2Component(mmStartStation), Utils.mmStr2Component(mmEndStation));
    }

    public PlayerOption(Component startStation, Component endStation) {
        setStartStation(startStation);
        setEndStation(endStation);
    }

    public PlayerOption() {
        this.startStation = NOT_AVALIABLE_COMPONENT;
        this.endStation = NOT_AVALIABLE_COMPONENT;
        this.startStationString = NOT_AVALIABLE;
        this.endStationString = NOT_AVALIABLE;
        this.speed = 4.0;
        this.uses = 1;
    }

    public void setStartStation(Component startStation) {
        if (startStation != null) {
            this.startStation = startStation;
            this.startStationString = Utils.component2Str(startStation);
        } else {
            this.startStation = NOT_AVALIABLE_COMPONENT;
            this.startStationString = NOT_AVALIABLE;
        }

    }

    public void setEndStation(Component endStation) {
        if (endStation != null) {
            this.endStation = endStation;
            this.endStationString = Utils.component2Str(endStation);
        } else {
            this.endStation = NOT_AVALIABLE_COMPONENT;
            this.endStationString = NOT_AVALIABLE;
        }
    }

    public boolean isStationNotEmpty() {
        return !isStartStationEmpty() && !isEndStationEmpty();
    }

    public boolean isStartStationEmpty() {
        return getStartStationString().equals(NOT_AVALIABLE);
    }

    public boolean isEndStationEmpty() {
        return getEndStationString().equals(NOT_AVALIABLE);
    }

    public String getClearStartStationName() {
        if (startStationString.endsWith("站"))
            return startStationString.substring(0, startStationString.length() - 1);
        else
            return startStationString;
    }

    public String getClearEndStationName() {
        if (endStationString.endsWith("站"))
            return endStationString.substring(0, endStationString.length() - 1);
        else
            return endStationString;
    }

    public String getMmStartStationName() {
        return Utils.Component2MmStr(startStation.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET));
    }

    public String getMmEndStationName() {
        return Utils.Component2MmStr(endStation.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET));
    }
}
