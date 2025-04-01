package com.bigbrother.bilicraftticketsystem.database.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TicketbgInfo {
    private int id;
    private String playerName;
    private String playerUuid;
    private String uploadTime;
    private int usageCount;
    private String itemName;
    private String filePath;
    private boolean shared;
    private String fontColor;
}
