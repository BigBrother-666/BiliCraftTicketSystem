package com.bigbrother.bilicraftticketsystem.database.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardInfo {
    private Integer id;
    private String cardUuid;
    private String startStation;
    private String mmStartStation;
    private String startStationSystem;
    private String endStation;
    private String mmEndStation;
    private String endStationSystem;
    private Double maxSpeed;
    private Double balance;

    public CardInfo(String cardUuid, String startStation, String mmStartStation, String startStationSystem,
                    String endStation, String mmEndStation, String endStationSystem, Double maxSpeed, Double balance) {
        this.cardUuid = cardUuid;
        this.startStation = startStation;
        this.mmStartStation = mmStartStation;
        this.startStationSystem = startStationSystem;
        this.endStation = endStation;
        this.mmEndStation = mmEndStation;
        this.endStationSystem = endStationSystem;
        this.maxSpeed = maxSpeed;
        this.balance = balance;
    }
}
