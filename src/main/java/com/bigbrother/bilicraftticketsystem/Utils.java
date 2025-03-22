package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.ItemsConfig.itemsConfig;

public class Utils {
    private static final Cache<String, TextColor> colorCache = CacheBuilder.newBuilder().maximumSize(50).build();

    public static Component str2Component(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    public static String component2Str(Component component) {
        String str = PlainTextComponentSerializer.plainText().serialize(component);
        if (str.startsWith("[") && str.endsWith("]")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    public static TextColor getRailwayColor(String railway) {
        TextColor color = colorCache.getIfPresent(railway);
        if (color != null) {
            return color;
        }

        for (String key : MainConfig.railwayColor.getKeys()) {
            if (railway.startsWith(key)) {
                color = TextColor.fromHexString(MainConfig.railwayColor.get(key, ""));
                if (color != null) {
                    colorCache.put(railway, color);
                    return color;
                }
                break;
            }
        }
        return NamedTextColor.GOLD;
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


    public static File getTicketbg(String fileName) {
        File folder = TrainCarts.plugin.getDataFile("images");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith(fileName)) {
                        return file;
                    }
                }
            }
        }
        return null;
    }
}
