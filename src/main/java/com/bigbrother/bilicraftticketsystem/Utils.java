package com.bigbrother.bilicraftticketsystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.config.ItemsConfig.itemsConfig;

public class Utils {
    public static Component str2Component(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
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
