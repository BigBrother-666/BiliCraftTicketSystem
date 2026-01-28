package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.menu.items.common.CoolDownItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter
@Setter
public abstract class BgItem extends CoolDownItem {
    private TicketbgInfo ticketbgInfo;

    public boolean isUseCurrTicketbg(Player player) {
        TicketbgInfo ticketbgInfo = MenuTicketbg.getTicketbgUsageMapping().get(player.getUniqueId());
        if (ticketbgInfo == null) {
            return false;
        }
        return this.ticketbgInfo.getId() == ticketbgInfo.getId();
    }
}
