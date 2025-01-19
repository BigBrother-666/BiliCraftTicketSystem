package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionStation;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbar;

public class CustomSignActionStation extends SignActionStation {
    @Override
    public void execute(SignActionEvent info) {
        super.execute(info);
        for (MinecartMember<?> minecartMember : info.getGroup()) {
            RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
            if (bossbar != null) {
                bossbar.updateStation();
            }
        }
    }
}
