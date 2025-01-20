package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;

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
            MinecartGroup group = info.getGroup();
            if (group != null) {
                // 设置用于判断车票使用站台的tag
                group.getProperties().addTags(line3);
                group.onPropertiesChanged();
                // 发车信息记录到数据库
                trainDatabaseManager.addBcspawnInfo(line3);
            }
        }
        if (info.isAction(SignActionType.REDSTONE_ON)) {
            info.setLine(3, line3);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent signChangeActionEvent) {
        // 检查权限和格式
        if (!signChangeActionEvent.getPlayer().hasPermission("bcts.buildsign.bcspawn")) {
            return false;
        }
        signChangeActionEvent.getPlayer().sendMessage(Component.text("建立控制牌成功，该控制牌可以在生成矿车时自动添加一个tag", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info != null && info.getMode() != SignActionMode.NONE && info.isType("bcspawn");
    }
}
