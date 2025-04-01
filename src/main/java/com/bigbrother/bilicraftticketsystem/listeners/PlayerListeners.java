package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

import java.util.UUID;

public class PlayerListeners implements Listener {
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        MenuMain.getMainMenuMapping().remove(uuid);
        MenuLocation.getLocationMenuMapping().remove(uuid);
        MenuFilter.getFilterMenuMapping().remove(uuid);
        MenuTicketbg.getTicketbgMenuMapping().remove(uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查玩家是否与制图台交互
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() == InventoryType.CARTOGRAPHY && TicketStore.isTicketItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }
}
