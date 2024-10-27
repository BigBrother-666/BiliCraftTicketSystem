package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import com.bigbrother.bilicraftticketsystem.entity.PlayerOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class PlayerListeners implements Listener {

    // 记录玩家的选择
    private static final Map<Player, PlayerOption> playerOptionMap = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && event.getView().getType() == InventoryType.CHEST) {
            Component title = event.getView().title();
            if (title.equals(Menu.mainMenu.title)) {
                // 主菜单处理逻辑
                event.setCancelled(true);
                mainMenu(Menu.itemLocReverse.get(title), event);
            } else if (title.equals(Menu.locationMenu.title)) {
                event.setCancelled(true);
                locationMenu(Menu.itemLocReverse.get(title), event);
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
        if (currentItem == null) {
            return;
        }
        if (playerOptionMap.get(player).isStartStationFlag()) {
            playerOptionMap.get(player).setStartStation(Component.text("已选择：" + itemSlot.get(event.getSlot()), NamedTextColor.GREEN));
        } else {
            playerOptionMap.get(player).setEndStation(Component.text("已选择：" + itemSlot.get(event.getSlot()), NamedTextColor.GREEN));
        }
        event.getView().close();
        player.openInventory(Menu.mainMenu.inventory);
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
        PlayerOption option = playerOptionMap.get(player);
        switch (itemName) {
            case "start":
                option.setStartStationFlag(true);
                player.openInventory(Menu.locationMenu.inventory);
                break;
            case "stop":
                option.setStartStationFlag(false);
                player.openInventory(Menu.locationMenu.inventory);
                break;
            case "speed":
                break;
            case "search":
                List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(option.getStartStationString(), option.getEndStationString());
                plugin.getLogger().log(Level.INFO, pathInfoList.toString());
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && event.getView().getType() == InventoryType.CHEST) {
            Component title = event.getView().title();
            Map<String, Integer> itemSlot = Menu.itemLoc.get(title);
            if (title.equals(Menu.mainMenu.title)) {
                // 先创建option
                if (!playerOptionMap.containsKey(player)) {
                    playerOptionMap.put(player, new PlayerOption());
                }

                // 获取item
                ItemStack startStation = event.getView().getItem(itemSlot.get("start"));
                ItemStack endStation = event.getView().getItem(itemSlot.get("stop"));
                ItemStack search = event.getView().getItem(itemSlot.get("search"));
                if (startStation == null || endStation == null || search == null) {
                    return;
                }

                // 显示选择的参数
                PlayerOption option = playerOptionMap.get(player);

                ItemMeta itemMeta = startStation.getItemMeta();
                itemMeta.lore(List.of(option.getStartStation()));
                startStation.setItemMeta(itemMeta);

                itemMeta = endStation.getItemMeta();
                itemMeta.lore(List.of(option.getEndStation()));
                endStation.setItemMeta(itemMeta);

                itemMeta = search.getItemMeta();
                if (option.canSearch()) {
                    itemMeta.lore(List.of(Component.text("点击搜索", NamedTextColor.GREEN)));
                } else {
                    itemMeta.lore(List.of(Component.text("不可用，未选择起点站/终到站", NamedTextColor.RED)));
                }
                search.setItemMeta(itemMeta);
            }
        }
    }
}
