package com.bigbrother.bilicraftticketsystem.entity;

import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;

@Data
public class PlayerOption {
    public PlayerOption() {
        this.startStation = Component.text("未选择起始站", NamedTextColor.RED);
        this.endStation = Component.text("未选择终到站", NamedTextColor.RED);
        this.speed = 4.0;
        this.tickets = new HashMap<>();
        this.uses = 1;
    }

    private Component startStation;
    private Component endStation;
    private double speed;
    private int uses;
    private Map<Integer, BCTicket> tickets;  // slot-ticket
    private boolean startStationFlag = false;
    private boolean searchedFlag = false;

    public String getStartStationString() {
        return PlainTextComponentSerializer.plainText().serialize(startStation);
    }

    public String getEndStationString() {
        return PlainTextComponentSerializer.plainText().serialize(endStation);
    }

    public boolean canSearch() {
        return !getStartStationString().contains("未选择") && !getEndStationString().contains("未选择");
    }
}
