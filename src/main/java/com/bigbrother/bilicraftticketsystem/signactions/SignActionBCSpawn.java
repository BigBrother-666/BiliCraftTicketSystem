package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;
import com.bigbrother.bilicraftticketsystem.menu.items.location.NearestLocItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class SignActionBCSpawn extends SignActionSpawn {
    @Override
    public void execute(SignActionEvent info) {
        String line3 = info.getLine(3).trim();

        if (line3.isEmpty()) {
            // 由于未知原因导致的控制牌tag缺失
            // 通过数据库记录寻找tag
            for (BcspawnInfo bcspawnInfo : NearestLocItem.getBcspawnInfoList()) {
                if (bcspawnInfo.getWorld().equals(info.getWorld().getName()) &&
                        bcspawnInfo.getCoordX() == info.getLocation().getBlockX() &&
                        bcspawnInfo.getCoordY() == info.getLocation().getBlockY() &&
                        bcspawnInfo.getCoordZ() == info.getLocation().getBlockZ()
                ) {
                    line3 = "%s-%s".formatted(bcspawnInfo.getTag(), bcspawnInfo.getSpawnDirection().replace("方向", ""));
                    break;
                }
            }
        }

        if (info.isAction(SignActionType.GROUP_ENTER)) {
            Location location = info.getSign().getLocation();
            plugin.getTrainDatabaseManager().addBcspawnCoord(line3, location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());
        }

        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }
        // Find and parse the spawn sign

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
                plugin.getTrainDatabaseManager().addBcspawnInfo(line3);
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
