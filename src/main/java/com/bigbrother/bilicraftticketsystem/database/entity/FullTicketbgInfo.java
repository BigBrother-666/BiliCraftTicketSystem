package com.bigbrother.bilicraftticketsystem.database.entity;

import lombok.Getter;


@Getter
public class FullTicketbgInfo extends TicketbgInfo {
    private final boolean deleted;

    public FullTicketbgInfo(int id, String playerName, String playerUuid, String uploadTime, int usageCount, String itemName, String filePath, boolean shared, String fontColor, boolean deleted) {
        super(id, playerName, playerUuid, uploadTime, usageCount, itemName, filePath, shared, fontColor);
        this.deleted = deleted;
    }
}
