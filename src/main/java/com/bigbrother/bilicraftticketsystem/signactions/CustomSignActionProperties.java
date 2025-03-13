package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionProperties;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbar;

public class CustomSignActionProperties extends SignActionProperties {
    @Override
    public void execute(SignActionEvent info) {
        super.execute(info);
        if ((info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) && info.isTrainSign() && info.hasGroup()) {
            MinecartGroup group = info.getGroup();
            if (info.isPowered() &&
                    !group.isUnloaded() &&
                    info.getLine(2).equals("remtag") &&
                    group.getProperties().getTickets().contains(MainConfig.expressTicketName)) {
                for (MinecartMember<?> minecartMember : group) {
                    RouteBossbar bossbar = SignActionShowroute.bossbarMapping.get(minecartMember);
                    if (bossbar != null) {
                        bossbar.updateExpress(group.getProperties().getTags().size() + 1);
                    }
                }
            }
        }
    }
}
