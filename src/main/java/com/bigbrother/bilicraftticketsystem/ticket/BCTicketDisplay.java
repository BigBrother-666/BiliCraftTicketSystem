package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import org.bukkit.inventory.ItemStack;


public class BCTicketDisplay extends MapDisplay {

    @Override
    public void onAttached() {
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setGlobal(false);

        renderBackground();
        renderTicket();
    }

    @Override
    public void onMapItemChanged() {
        this.renderTicket();
    }

    public void renderBackground() {
        Ticket ticket = TicketStore.getTicketFromItem(this.getMapItem());
        MapTexture bg;
        if (ticket == null) {
            bg = Ticket.getDefaultBackgroundImage();
        } else {
            bg = ticket.loadBackgroundImage();
        }
        this.getLayer().draw(bg, 0, 0);
    }

    private void renderTicket() {
        this.getLayer(1).clear();

        ItemStack ticketItem = this.getMapItem();
        CommonTagCompound customData = this.getCommonMapItem().getCustomData();
        String displayName = customData.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, MainConfig.expressTicketName);
        if (ticketItem == null) {
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_INVALID.get());
        } else {
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_BLACK, displayName);
            if (TicketStore.isTicketExpired(ticketItem)) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 57, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_EXPIRED.get());
            } else {
                int maxUses = customData.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0);
                int numUses = (maxUses == 1) ? 0 : TicketStore.getNumberOfUses(ticketItem);
                if (maxUses < 0) {
                    maxUses = -1; // Just in case, so it works properly with Localization
                }
                String text = Localization.TICKET_MAP_USES.get(Integer.toString(maxUses), Integer.toString(numUses));
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 57, MapColorPalette.COLOR_BLACK, text);
            }

            String ownerName = customData.getValue("ticketOwnerName", "Unknown Owner");
            ownerName = StringUtil.stripChatStyle(ownerName);
            if (TicketStore.isTicketOwner(this.getOwners().get(0), ticketItem)) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 74, MapColorPalette.COLOR_BLACK, ownerName);
            } else {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 74, MapColorPalette.COLOR_RED, ownerName);
            }
        }
    }
}

