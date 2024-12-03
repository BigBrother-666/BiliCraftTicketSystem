package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public class Menu {
    public Menu() {
        loadMenu();
    }

    @AllArgsConstructor
    public static class CustomMenu {
        public Inventory inventory;
        public Component title;
        public List<Integer> ticketSlots;
    }

    public CustomMenu mainMenu;
    public CustomMenu locationMenu;
    private static FileConfiguration mainMenuConfig;
    private static FileConfiguration locationMenuConfig;
    private static FileConfiguration itemsConfig;
    public static Map<Component, Map<Integer, String>> itemLocReverse;
    public static Map<Component, Map<String, Integer>> itemLoc;

    private static final Map<UUID, Menu> playerMenu = new HashMap<>();

    public static void loadMenuConfig(BiliCraftTicketSystem plugin) {
        itemsConfig = new FileConfiguration(plugin, EnumMenu.ITEMS.getFileName());
        itemsConfig.load();
        mainMenuConfig = new FileConfiguration(plugin, EnumMenu.MAIN.getFileName());
        mainMenuConfig.load();
        locationMenuConfig = new FileConfiguration(plugin, EnumMenu.LOCATION.getFileName());
        locationMenuConfig.load();
        itemLocReverse = new HashMap<>();
        itemLoc = new HashMap<>();
        playerMenu.clear();
        plugin.getLogger().log(Level.INFO, "成功加载菜单配置文件！");
    }

    public void loadMenu() {
        mainMenu = createMenu(mainMenuConfig);
        locationMenu = createMenu(locationMenuConfig);
    }

    public static Menu getMenu(Player player) {
        if (!playerMenu.containsKey(player.getUniqueId())) {
            playerMenu.put(player.getUniqueId(), new Menu());
        }
        return playerMenu.get(player.getUniqueId());
    }

    private static CustomMenu createMenu(FileConfiguration menuConf) {
        ConfigurationNode items = menuConf.getNode("items");
        Component title = MiniMessage.miniMessage().deserialize(menuConf.get("title","")).decoration(TextDecoration.ITALIC, false);
        Inventory inventory = new MenuInventoryHolder(menuConf.get("size", 54), title).getInventory();
        Map<Integer, String> tempReverse = new HashMap<>();
        Map<String, Integer> temp = new HashMap<>();

        int slotCount = 0; // 无slot处理
        for (Map.Entry<String, Object> entry : items.getValues().entrySet()) {
            ConfigurationNode item = items.getNode(entry.getKey());

            // 设置物品信息
            String material = item.get("material","");
            ItemStack customItem;
            if (material.startsWith("item-")) {
                customItem = loadItemFromFile(material.replaceFirst("item-","").trim());
            } else {
                customItem = new ItemStack(Material.valueOf(item.get("material","").trim()));
            }
            ItemMeta itemMeta = customItem.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String s : item.getList("lore", String.class, Collections.emptyList())) {
                lore.add(MiniMessage.miniMessage().deserialize(s).decoration(TextDecoration.ITALIC, false));
            }
            itemMeta.lore(lore);
            itemMeta.displayName(MiniMessage.miniMessage().deserialize(item.get("name","")).decoration(TextDecoration.ITALIC, false));
            customItem.setItemMeta(itemMeta);

            // 添加物品
            inventory.setItem(item.get("slot",slotCount), customItem);
            tempReverse.put(item.get("slot",slotCount), item.getName());
            temp.put(item.getName(), item.get("slot",slotCount));
            slotCount++;
        }
        itemLocReverse.putIfAbsent(title, tempReverse);
        itemLoc.putIfAbsent(title, temp);
        return new CustomMenu(inventory, title, menuConf.getList("ticket-slots", Integer.class));
    }

    // 保存物品到配置文件
    public static void saveItemToFile(String path, ItemStack item) {
        // 序列化
        Map<String, Object> itemData = item.serialize();
        itemsConfig.set(path, itemData);
        itemsConfig.save();
    }

    // 从配置文件中读取物品
    public static ItemStack loadItemFromFile(String path) {
        path = path.trim();
        if (itemsConfig.contains(path)) {
            // 反序列化
            Map<String, Object> itemData = itemsConfig.getNode(path).getValues();
            return ItemStack.deserialize(itemData);
        }
        return new ItemStack(Material.AIR);
    }
}
