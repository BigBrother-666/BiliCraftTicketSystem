package com.bigbrother.bctsguardplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class GuardListeners implements Listener {
    public static final NamespacedKey KEY_TRANSIT_PASS = new NamespacedKey("bcts-guard", "bc-transit-pass");

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查玩家是否与制图台交互
        InventoryView view = event.getView();
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) {
            return;
        }
        ItemMeta itemMeta = currentItem.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
        Boolean b = pdc.get(KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN);
        if (view.getTopInventory().getType() == InventoryType.CARTOGRAPHY && Boolean.TRUE.equals(b)) {
            event.setCancelled(true);
        }
    }
}
