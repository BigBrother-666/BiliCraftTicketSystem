package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

@SuppressWarnings("unchecked")
public class Menu {
    public static class CustomMenu {
        public Inventory inventory;
        public String title;
    }

    public static CustomMenu mainMenu;
    public static CustomMenu speedMenu;
    public static CustomMenu locationMenu;
    public static FileConfiguration itemsConfig;
    public static Map<String, Map<String, Integer>> itemLoc;

    public static void loadMenu(BiliCraftTicketSystem plugin) {
        itemsConfig = new FileConfiguration(plugin, "menuitems.yml");
        createMenu("menu_main.yml", plugin, mainMenu);
        createMenu("menu_speed.yml", plugin, speedMenu);
        createMenu("menu_location.yml", plugin, locationMenu);
    }

    private static void createMenu(String filePath, BiliCraftTicketSystem plugin, CustomMenu menu) {
        FileConfiguration menuConf = new FileConfiguration(plugin, filePath);
        menuConf.load();
        ConfigurationNode items = menuConf.getNode("items");
        Inventory inventory = Bukkit.createInventory(null, menuConf.get("size", 54), Component.text(menuConf.get("title","")));
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
            if (customItem == null) {
                return;
            }
            ItemMeta itemMeta = customItem.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String s : (List<String>)item.get("lore", List.class)) {
                lore.add(Component.text(s));
            }
            itemMeta.lore(lore);
            itemMeta.displayName(Component.text(item.get("name","")));
            customItem.setItemMeta(itemMeta);

            // 添加物品
            inventory.setItem(item.get("slot",0), customItem);
            itemLoc.put(menuConf.get("title",""), Map.of(item.getName(), item.get("slot",0)));
        }
        menu.inventory = inventory;
        menu.title = menuConf.get("title","");
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
        if (itemsConfig.contains(path)) {
            // 反序列化
            Map<String, Object> itemData = itemsConfig.get(path, Map.class);
            return ItemStack.deserialize(itemData);
        }
        return new ItemStack(Material.AIR);
    }
}
