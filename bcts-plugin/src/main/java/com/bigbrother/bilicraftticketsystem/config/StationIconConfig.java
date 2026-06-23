package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 车站按钮自定义图标配置（{@code iconitems.yml}）。
 * <p>
 * 铁路系统成员在车站选择界面把旗帜（Banner）拖到某个车站按钮上时，把该旗帜物品保存到本文件，
 * 条目 id 为 {@code 系统id_车站名}，用于覆盖默认的车站物品图标（默认 MINECART）。插件启动 / 重载
 * 时读取本文件并据此渲染车站按钮。
 * <p>
 * 文件结构（每个条目一个节点，节点名即 id）：
 * <pre>
 * 系统id_车站名:
 *   station: 车站名          # 冗余存放干净站名，便于「按站名取首个图标」查找
 *   item:                   # 旗帜物品序列化结果（{@link ItemStack#serialize()}）
 *     id: minecraft:white_banner
 *     ...
 * </pre>
 * 通过搜索按钮进入车站选择界面时系统未知（一个车站可能属于多个系统），此时取
 * {@link #getFirstForStation} 第一个读取到的该车站图标。
 */
public class StationIconConfig {
    private static FileConfiguration config;

    /**
     * 组合键（{@code 系统id_车站名}）-> 图标物品。
     */
    private static final Map<String, ItemStack> byCompositeKey = new LinkedHashMap<>();
    /**
     * 车站名 -> 图标物品（按文件读取顺序，首个出现者优先）。供搜索入口在系统未知时取首个图标。
     */
    private static final Map<String, ItemStack> firstByStation = new LinkedHashMap<>();

    private StationIconConfig() {
    }

    /**
     * 加载 iconitems.yml 并重建内存索引。插件启动与 reload 时调用。
     *
     * @param plugin 插件实例
     */
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.ICON_ITEMS.getFileName());
        config.load();

        byCompositeKey.clear();
        firstByStation.clear();
        for (ConfigurationNode node : config.getNodes()) {
            String station = node.get("station", String.class, null);
            if (station == null || !node.contains("item")) {
                continue;
            }
            ItemStack item = deserializeItem(node.getNode("item"));
            byCompositeKey.put(node.getName(), item);
            firstByStation.putIfAbsent(station, item);
        }
    }

    /**
     * 组合 id：{@code 系统id_车站名}。
     */
    public static String compositeKey(String systemId, String stationName) {
        return systemId + "_" + stationName;
    }

    /**
     * 取指定系统下某车站的自定义图标。
     *
     * @param systemId    系统 id
     * @param stationName 车站名
     * @return 图标物品；未设置返回 null
     */
    public static ItemStack get(String systemId, String stationName) {
        if (systemId == null || stationName == null) {
            return null;
        }
        return byCompositeKey.get(compositeKey(systemId, stationName));
    }

    /**
     * 取某车站的自定义图标，忽略系统（取文件中首个读取到的该车站图标）。
     * <p>
     * 用于通过搜索按钮进入车站选择界面：车站可能属于多个系统，无法确定显示哪个系统的图标。
     *
     * @param stationName 车站名
     * @return 图标物品；未设置返回 null
     */
    public static ItemStack getFirstForStation(String stationName) {
        return stationName == null ? null : firstByStation.get(stationName);
    }

    /**
     * 保存某系统下某车站的自定义图标并写回 iconitems.yml，同步更新内存索引。
     *
     * @param systemId    系统 id
     * @param stationName 车站名
     * @param icon        图标物品（调用方应已克隆并把数量设为 1）
     */
    public static void save(String systemId, String stationName, ItemStack icon) {
        if (config == null || systemId == null || stationName == null || icon == null) {
            return;
        }
        String key = compositeKey(systemId, stationName);
        ConfigurationNode node = config.getNode(key);
        node.set("station", stationName);
        node.set("item", icon.serialize());
        config.save();

        byCompositeKey.put(key, icon);
        // 重新挑选「首个图标」：清掉再按文件顺序重建该站，保证 firstByStation 与文件一致
        firstByStation.remove(stationName);
        for (ConfigurationNode n : config.getNodes()) {
            if (stationName.equals(n.get("station", String.class, null)) && n.contains("item")) {
                firstByStation.put(stationName, byCompositeKey.getOrDefault(n.getName(), icon));
                break;
            }
        }
    }

    /**
     * 反序列化节点下的物品。复用 {@link CommonUtils#deepToPlainMap} 把 BKCommonLib 节点代理转成纯
     * Map，兼容 Paper 1.21+ 的新版物品数据组件格式。
     */
    private static ItemStack deserializeItem(ConfigurationNode itemNode) {
        Map<String, Object> itemData = CommonUtils.deepToPlainMap(itemNode.getValues());
        return ItemStack.deserialize(itemData);
    }
}
