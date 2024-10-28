package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@SuppressWarnings("unchecked")
public class Menu {
    @AllArgsConstructor
    public static class CustomMenu {
        public Inventory inventory;
        public Component title;
        public List<Integer> ticketSlots;
    }

    public static CustomMenu mainMenu;
    public static CustomMenu locationMenu;
    private static FileConfiguration itemsConfig;
    public static Map<Component, Map<Integer, String>> itemLocReverse;
    public static Map<Component, Map<String, Integer>> itemLoc;

    public static void loadMenu(BiliCraftTicketSystem plugin) {
        itemsConfig = new FileConfiguration(plugin, "menuitems.yml");
        itemsConfig.load();
        itemLocReverse = new HashMap<>();
        itemLoc = new HashMap<>();
        mainMenu = createMenu("menu_main.yml", plugin);
        locationMenu = createMenu("menu_location.yml", plugin);
        plugin.getLogger().log(Level.INFO, "成功加载菜单！");
    }

    private static CustomMenu createMenu(String filePath, BiliCraftTicketSystem plugin) {
        FileConfiguration menuConf = new FileConfiguration(plugin, filePath);
        menuConf.load();
        ConfigurationNode items = menuConf.getNode("items");
        Component title = MiniMessage.miniMessage().deserialize(menuConf.get("title","")).decoration(TextDecoration.ITALIC, false);
        Inventory inventory = Bukkit.createInventory(null, menuConf.get("size", 54), title);
        Map<Integer, String> tempReverse = new HashMap<>();
        Map<String, Integer> temp = new HashMap<>();
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
            for (String s : item.get("lore", List.of(""))) {
                lore.add(MiniMessage.miniMessage().deserialize(s).decoration(TextDecoration.ITALIC, false));
            }
            itemMeta.lore(lore);
            itemMeta.displayName(MiniMessage.miniMessage().deserialize(item.get("name","")).decoration(TextDecoration.ITALIC, false));
            customItem.setItemMeta(itemMeta);

            // 添加物品
            inventory.setItem(item.get("slot",0), customItem);
            tempReverse.put(item.get("slot",0), item.getName());
            temp.put(item.getName(), item.get("slot",0));
        }
        itemLocReverse.put(title, tempReverse);
        itemLoc.put(title, temp);
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
        return null;
    }
}
