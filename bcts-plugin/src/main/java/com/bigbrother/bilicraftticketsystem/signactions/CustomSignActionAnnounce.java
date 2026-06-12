package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionAnnounce;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

public class CustomSignActionAnnounce extends SignActionAnnounce {
    @Override
    public void execute(SignActionEvent info) {
        String message = getMessage(info);
        for (String s : message.split("\\\\n")) {
            if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
                if (!info.hasRailedMember() || !info.isPowered()) return;
                sendMessage(info, info.getGroup(), s);
            } else if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
                if (!info.hasRailedMember() || !info.isPowered()) return;
                sendMessage(info, info.getMember(), s);
            } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
                for (MinecartGroup group : info.getRCTrainGroups()) {
                    sendMessage(info, group, s);
                }
            }
        }
    }
}
