package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.config.Menu;
import com.bigbrother.bilicraftticketsystem.entity.PlayerOption;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PlayerListeners implements Listener {

    // 记录玩家的选择
    private static Map<Player, PlayerOption> playerOptionMap = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        System.out.println(event.getAction());
        if (event.getWhoClicked() instanceof Player && event.getView().getType() == InventoryType.CHEST) {
            Component title = event.getView().title();
            if (title.equals(Menu.mainMenu.title)) {
                // 主菜单处理逻辑
                event.setCancelled(true);
                mainMenu(Menu.itemLoc.get(title), event);
            } else if (title.equals(Menu.speedMenu.title)) {
                event.setCancelled(true);
                speedMenu(Menu.itemLoc.get(title), event);
            } else if (title.equals(Menu.locationMenu.title)) {
                event.setCancelled(true);
                locationMenu(Menu.itemLoc.get(title), event);
            }
            // 获取点击的物品
//            ItemStack clickedItem = event.getCurrentItem();
//            if (clickedItem != null && clickedItem.hasItemMeta()) {
//                ItemMeta meta = clickedItem.getItemMeta();
//
//            }
        }
    }

    private void locationMenu(Map<Integer, String> itemSlot, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
//        if (currentItem != )
    }

    private void speedMenu(Map<Integer, String> itemSlot, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int clickedSlot = event.getSlot();
    }

    private void mainMenu(Map<Integer, String> itemSlot, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String itemName = itemSlot.get(event.getSlot());
        if (itemName == null) {
            return;
        }
        switch (itemName) {
            case "start":
                player.openInventory(Menu.locationMenu.inventory);
                break;
            case "stop":
                break;
            case "buy":
                break;
        }
    }
}
