package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;

public class CustomSignActionSpawn extends SignActionSpawn {
    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }
        // Find and parse the spawn sign
        String line3 = info.getLine(3).trim();
        if (info.isAction(SignActionType.REDSTONE_ON)) {
            info.setLine(3, "");
        }
        SpawnSign sign = info.getTrainCarts().getSpawnSignManager().create(info);
        if (sign.isActive()) {
            sign.spawn(info);
            sign.resetSpawnTime();
            // 设置用于判断车票使用站台的tag
            info.getGroup().getProperties().addTags(line3);
            info.getGroup().onPropertiesChanged();
        }
        if (info.isAction(SignActionType.REDSTONE_ON)) {
            info.setLine(3, line3);
        }
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info != null && info.getMode() != SignActionMode.NONE && info.isType("bcspawn");
    }
}
