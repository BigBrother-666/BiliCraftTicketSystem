package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class CustomSignActionSpawn extends SignActionSpawn {
    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions buildOpts = SignBuildOptions.create().setPermission(Permission.BUILD_SPAWNER).setName("train spawner").setDescription("spawn trains on the tracks above when powered by redstone").setTraincartsWIKIHelp("TrainCarts/Signs/Spawner");

        Player sender = event.getPlayer();

        // 检查放置spawn控制牌的权限
        if (!buildOpts.checkBuildPermission(sender)) {
            return false;
        } else {
            SpawnSign sign = event.getTrainCarts().getSpawnSignManager().create(event);

            // 检查每种矿车的权限
            boolean canHaveItems = false;
            for (SpawnableMember member : sign.getSpawnableGroup().getMembers()) {
                if (!member.getPermission().handleMsg(sender, Localization.SPAWN_DISALLOWED_TYPE.get(member.toString()))) {
                    return false;
                }
                if (!canHaveItems && member.hasInventoryItems()) {
                    canHaveItems = Permission.SPAWNER_INVENTORY_ITEMS.has(sender);
                    if (!canHaveItems) {
                        Localization.SPAWN_DISALLOWED_INVENTORY.message(sender);
                        return false;
                    }
                }
            }

            // spawn自动模式权限
            if (sign.hasInterval() && !Permission.SPAWNER_AUTOMATIC.handleMsg(sender, NamedTextColor.RED + "You do not have permission to use automatic signs")) {
                sign.remove();
                return false;
            } else {
                if (event.isInteractive()) {
                    buildOpts.showBuildMessage(sender);
                    if (sign.hasInterval()) {
                        sender.sendMessage(NamedTextColor.YELLOW + "This spawner will automatically spawn trains every " + Util.getTimeString(sign.getInterval()) + " while powered");
                    }
                }
                return true;
            }
        }
    }
}
