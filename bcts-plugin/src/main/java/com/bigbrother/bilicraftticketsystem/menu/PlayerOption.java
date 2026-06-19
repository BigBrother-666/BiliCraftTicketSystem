package com.bigbrother.bilicraftticketsystem.menu;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import lombok.Data;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

@Data
public class PlayerOption {
    @Getter
    protected Component startStationComponent;
    @Getter
    protected Component endStationComponent;
    protected String startStationSystem;
    protected String endStationSystem;
    @Getter
    protected String startStationString;
    @Getter
    protected String endStationString;

    protected double speed;
    private int uses;
    private boolean searchedFlag = false;


    public PlayerOption(String mmStartStation, String mmEndStation) {
        this(CommonUtils.mmStr2Component(mmStartStation), CommonUtils.mmStr2Component(mmEndStation));
    }

    public PlayerOption(Component startStationComponent, Component endStationComponent) {
        setStartStationComponent(startStationComponent);
        setEndStationComponent(endStationComponent);
    }

    public PlayerOption() {
        this.startStationComponent = CommonUtils.NOT_AVAILABLE_COMPONENT;
        this.endStationComponent = CommonUtils.NOT_AVAILABLE_COMPONENT;
        this.startStationSystem = CommonUtils.NOT_AVAILABLE;
        this.endStationSystem = CommonUtils.NOT_AVAILABLE;
        this.startStationString = CommonUtils.NOT_AVAILABLE;
        this.endStationString = CommonUtils.NOT_AVAILABLE;
        this.speed = 4.0;
        this.uses = 1;
    }

    public void setStartStationComponent(Component startStationComponent) {
        if (startStationComponent != null) {
            this.startStationComponent = startStationComponent;
            this.startStationString = CommonUtils.component2Str(startStationComponent);
        } else {
            this.startStationComponent = CommonUtils.NOT_AVAILABLE_COMPONENT;
            this.startStationString = CommonUtils.NOT_AVAILABLE;
        }

    }

    public void setEndStationComponent(Component endStationComponent) {
        if (endStationComponent != null) {
            this.endStationComponent = endStationComponent;
            this.endStationString = CommonUtils.component2Str(endStationComponent);
        } else {
            this.endStationComponent = CommonUtils.NOT_AVAILABLE_COMPONENT;
            this.endStationString = CommonUtils.NOT_AVAILABLE;
        }
    }

    public boolean isStationNotEmpty() {
        return !isStartStationEmpty() && !isEndStationEmpty();
    }

    public boolean isStartStationEmpty() {
        return getStartStationString().equals(CommonUtils.NOT_AVAILABLE);
    }

    public boolean isEndStationEmpty() {
        return getEndStationString().equals(CommonUtils.NOT_AVAILABLE);
    }

    public String getMmStartStationName() {
        return CommonUtils.component2MmStr(startStationComponent.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET));
    }

    public String getMmEndStationName() {
        return CommonUtils.component2MmStr(endStationComponent.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET));
    }
}
