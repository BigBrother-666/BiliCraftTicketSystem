package com.bigbrother.bilicraftticketsystem.utils;

import com.bergerkiller.bukkit.common.config.yaml.YamlNodeAbstract;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            // 反序列化。
            // 注意：BKCommonLib 的 getValues() 返回的是 YamlNodeMapProxy，其嵌套子树（如新版物品
            // 数据组件格式的 components 块）仍是 BKCommonLib 节点代理，既不是 java.util.Map 也不是
            // Bukkit ConfigurationSection。而 Paper 1.21+ 在反序列化「新版格式」（含 schema_version）
            // 时会断言 components instanceof Map，否则抛 "components must be a Map"。
            // 因此这里先把整棵树递归转成纯 LinkedHashMap，新旧两种格式都能正确反序列化。
            Map<String, Object> itemData = deepToPlainMap(itemsConfig.getNode(path).getValues());
            return ItemStack.deserialize(itemData);
        }
        return new ItemStack(Material.RAIL);
    }

    /**
     * 把 BKCommonLib 配置节点（{@code getValues()} 返回的代理 Map / 嵌套节点）递归转换为纯
     * {@link LinkedHashMap} / {@link ArrayList}，保持键顺序。
     * <p>
     * 用于喂给 {@link ItemStack#deserialize(Map)}：Paper 的新版物品反序列化对 {@code components}
     * 等嵌套结构要求是标准 {@link Map}，而 BKCommonLib 的节点代理不满足该判断。
     * <p>
     * 设为公开以便单测直接验证转换结果（见 ItemDeserializeTest）。
     *
     * @param map 节点 Map（顶层）
     * @return 仅含纯 JDK 集合 / 标量的等价 Map
     */
    public static Map<String, Object> deepToPlainMap(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>(Math.max(8, map.size() * 2));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), deepToPlainValue(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object deepToPlainValue(Object value) {
        // BKCommonLib 的嵌套子节点是 YamlNodeAbstract（未实现 java.util.Map），需先取其 getValues()
        if (value instanceof YamlNodeAbstract<?> node) {
            return deepToPlainMap(node.getValues());
        }
        if (value instanceof Map<?, ?> nested) {
            return deepToPlainMap((Map<String, Object>) nested);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepToPlainValue(element));
            }
            return copy;
        }
        return value;
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

    /**
     * 速度转换 km/h -> m/tick
     */
    public static double kph2Mpt(double kph) {
        return kph / (20 * 3.6);
    }
}
