package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 车站英文名映射配置（{@code station_alias.yml}）。
 * <p>
 * 记录「中文车站名 → 英文车站名」，英文名仅用于展示（网页线路图 / 车票系统选站界面），不参与寻路等逻辑。
 * 文件结构（每个顶层键是中文站名，值是英文站名，可含空格）：
 * <pre>
 * 中文站名: English Name
 * </pre>
 */
public class StationAliasConfig {
    private static FileConfiguration config;

    /**
     * 中文站名 -> 英文站名（保持文件顺序）。
     */
    private static final Map<String, String> aliases = new LinkedHashMap<>();

    private StationAliasConfig() {
    }

    /**
     * 加载 station_alias.yml 并重建内存映射。插件启动与 reload 时调用。
     *
     * @param plugin 插件实例
     */
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.STATION_ALIAS.getFileName());
        config.load();

        aliases.clear();
        for (String key : config.getKeys()) {
            String en = config.get(key, String.class, null);
            if (en != null && !en.isBlank()) {
                aliases.put(key, en.trim());
            }
        }
    }

    /**
     * 取某车站的英文名。
     *
     * @param stationName 中文站名
     * @return 英文站名；未设置返回 null
     */
    public static String get(String stationName) {
        return stationName == null ? null : aliases.get(stationName);
    }

    /**
     * 新增 / 修改某车站的英文名并写回文件。
     *
     * @param stationName 中文站名
     * @param englishName 英文站名
     */
    public static void upsert(String stationName, String englishName) {
        aliases.put(stationName, englishName);
        if (config != null) {
            config.set(stationName, englishName);
            config.save();
        }
    }

    /**
     * 删除某车站的英文名并写回文件。
     *
     * @param stationName 中文站名
     * @return 原先存在映射返回 true
     */
    public static boolean delete(String stationName) {
        boolean existed = aliases.remove(stationName) != null;
        if (existed && config != null) {
            config.remove(stationName);
            config.save();
        }
        return existed;
    }

    /**
     * 全部映射（只读视图）。
     *
     * @return 中文站名 -> 英文站名
     */
    public static Map<String, String> all() {
        return Collections.unmodifiableMap(aliases);
    }

    /**
     * 车站显示名：有英文名时为「中文 (英文)」，否则为中文站名本身。
     *
     * @param stationName 中文站名
     * @return 显示名
     */
    public static String displayName(String stationName) {
        String en = get(stationName);
        return en == null ? stationName : "%s (%s)".formatted(stationName, en);
    }
}
