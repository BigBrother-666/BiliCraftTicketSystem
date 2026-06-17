package com.bigbrother.bilicraftticketsystem.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.awt.*;
import java.io.File;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.config.ItemsConfig.itemsConfig;

public class CommonUtils {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String NOT_AVAILABLE = "N/A";
    public static final Component NOT_AVAILABLE_COMPONENT = Component.text(NOT_AVAILABLE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET);
    public static final String NOT_AVAILABLE_MM = component2MmStr(NOT_AVAILABLE_COMPONENT);

    public static MiniMessage miniMessage() {
        return MM;
    }

    /**
     * 解析含 legacy &amp; 代码的配置文本。等价于 {@link #mmStr2Component(String)}，
     * 二者都同时支持 MiniMessage 与 &amp; 代码（见 {@link PlaceholderParser#toComponent(String)}）。
     */
    public static Component legacyStr2Component(String s) {
        return PlaceholderParser.toComponent(s);
    }

    /**
     * 解析配置文本为 Component，同时支持 MiniMessage 标签与 legacy &amp; 颜色代码。
     */
    public static Component mmStr2Component(String s) {
        return PlaceholderParser.toComponent(s);
    }

    public static String component2MmStr(Component c) {
        return MM.serialize(c);
    }

    public static String component2Str(Component component) {
        String str = PlainTextComponentSerializer.plainText().serialize(component);
        if (str.startsWith("[") && str.endsWith("]")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
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
        return new ItemStack(Material.RAIL);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean deleteTicketbg(String fileName) {
        File file = new File(ImageUtils.getImageFolder(), fileName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static Color hexToColor(String hex) throws IllegalArgumentException {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) {
            throw new IllegalArgumentException("Invalid HEX color: " + hex);
        }

        int red = Integer.parseInt(hex.substring(0, 2), 16);
        int green = Integer.parseInt(hex.substring(2, 4), 16);
        int blue = Integer.parseInt(hex.substring(4, 6), 16);

        return new Color(red, green, blue);
    }

    /**
     * 速度转换 m/tick -> km/h
     */
    public static double mpt2Kph(double mpT) {
        return mpT * 20 * 3.6;
    }
}
