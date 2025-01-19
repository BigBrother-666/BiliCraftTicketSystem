package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionStation;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbar;

public class CustomSignActionStation extends SignActionStation {
    @Override
    public void execute(SignActionEvent info) {
        super.execute(info);
        RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(info.getMember(), null);
        if (bossbar != null) {
            bossbar.updateStation();
        }
    }
}
