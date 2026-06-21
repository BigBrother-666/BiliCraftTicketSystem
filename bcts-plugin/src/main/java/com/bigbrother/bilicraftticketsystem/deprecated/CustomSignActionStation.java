package com.bigbrother.bilicraftticketsystem.deprecated;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionStation;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(since = "2.0.0")
public class CustomSignActionStation extends SignActionStation {
    @Override
    public void execute(SignActionEvent info) {
        super.execute(info);
        MinecartGroup group = info.getGroup();
        if (group == null) {
            return;
        }
        // 显示非直达车信息
        if (info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER)) {
            for (MinecartMember<?> minecartMember : group) {
                RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
                if (bossbar != null && bossbar.getRouteId() != null) {
                    bossbar.updateStation();
                }
            }
        } else if (info.isAction(SignActionType.GROUP_LEAVE)) {
            for (MinecartMember<?> minecartMember : group) {
                RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
                if (bossbar != null && bossbar.getRouteId() != null) {
                    bossbar.update();
                }
            }
        }
    }
}
