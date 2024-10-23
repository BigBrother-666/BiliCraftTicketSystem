package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.config.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerListeners implements Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getView().getType() == InventoryType.CHEST) {
            String title = event.getView().getTitle();
            if (title.equals(Menu.mainMenu.title)) {
                // 主菜单处理逻辑
                mainMenu();
            } else if (title.equals(Menu.speedMenu.title)) {
                speedMenu();
            } else if (title.equals(Menu.locationMenu.title)) {
                locationMenu();
            } else {
                return;
            }
            // 获取点击的物品
//            ItemStack clickedItem = event.getCurrentItem();
//            if (clickedItem != null && clickedItem.hasItemMeta()) {
//                ItemMeta meta = clickedItem.getItemMeta();
//
//            }
        }
    }

    private void locationMenu() {
    }

    private void speedMenu() {
    }

    private void mainMenu() {
        
    }
}
