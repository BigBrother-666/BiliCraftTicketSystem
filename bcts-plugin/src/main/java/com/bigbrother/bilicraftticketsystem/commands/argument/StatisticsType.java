package com.bigbrother.bilicraftticketsystem.commands.argument;

import java.util.ArrayList;
import java.util.List;

public enum StatisticsType {
    BCSPAWN("bcspawn"),
    TICKET_REVENUE("ticket_revenue"),
    TRANSIT_PASS_USAGE("transit_pass_usage");

    private final String name;

    StatisticsType(String name) {
        this.name = name;
    }

    public static StatisticsType fromName(String name) {
        for (StatisticsType lt : values()) {
            if (lt.name.equalsIgnoreCase(name)) {
                return lt;
            }
        }
        return null;
    }

    public static List<String> getNameList() {
        List<String> nameList = new ArrayList<>();
        for (StatisticsType lt : values()) {
            nameList.add(lt.name);
        }
        return nameList;
    }
}
