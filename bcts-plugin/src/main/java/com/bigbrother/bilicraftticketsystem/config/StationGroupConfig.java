package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 车站组配置（{@code station_group.yml}）。
 * <p>
 * 把若干<b>不同名</b>但距离较近的车站标记为一个车站组，组内车站两两之间可换乘（仅影响联程票查询）。
 * 一个车站最多属于一个车站组。文件结构：
 * <pre>
 * 组名:
 *   - 车站A
 *   - 车站B
 * </pre>
 * 内存数据可通过 {@link #replaceAll} 直接设置，便于在无 Bukkit 环境的单元测试中使用。
 */
public class StationGroupConfig {
    private static FileConfiguration config;

    /**
     * 组名 -> 组内车站（保持文件顺序）。volatile 整体替换，寻路线程读取时无需加锁。
     */
    private static volatile Map<String, List<String>> groups = Collections.emptyMap();
    /**
     * 车站名 -> 所属组名（{@link #groups} 的反向索引）。
     */
    private static volatile Map<String, String> stationToGroup = Collections.emptyMap();

    private StationGroupConfig() {
    }

    /**
     * 加载 station_group.yml 并重建内存索引。插件启动与 reload 时调用。
     *
     * @param plugin 插件实例
     */
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.STATION_GROUP.getFileName());
        config.load();

        Map<String, List<String>> parsed = new LinkedHashMap<>();
        for (String key : config.getKeys()) {
            List<String> stations = config.getList(key, String.class, null);
            if (stations != null && !stations.isEmpty()) {
                parsed.put(key, new ArrayList<>(stations));
            }
        }
        replaceAll(parsed);
    }

    /**
     * 整体替换内存中的车站组并重建反向索引（不写文件）。
     * <p>
     * 若同一车站出现在多个组中（手动改文件导致），以先出现的组为准。
     *
     * @param newGroups 组名 -> 组内车站
     */
    public static void replaceAll(Map<String, List<String>> newGroups) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        Map<String, String> index = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : newGroups.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
            for (String station : e.getValue()) {
                index.putIfAbsent(station, e.getKey());
            }
        }
        groups = copy;
        stationToGroup = index;
    }

    /**
     * 取与某车站可互相换乘的全部车站（含其自身）。
     *
     * @param stationName 车站名
     * @return 所在组的全部车站；不属于任何组时返回仅含自身的列表
     */
    public static List<String> groupMembers(String stationName) {
        String group = stationToGroup.get(stationName);
        if (group == null) {
            return List.of(stationName);
        }
        return groups.get(group);
    }

    /**
     * 取某车站所属的组名。
     *
     * @param stationName 车站名
     * @return 组名；不属于任何组返回 null
     */
    public static String groupOf(String stationName) {
        return stationToGroup.get(stationName);
    }

    /**
     * 新增 / 覆盖一个车站组并写回文件。
     *
     * @param groupName 组名
     * @param stations  组内车站（调用方保证已去重、至少两个、不属于其它组）
     */
    public static void upsert(String groupName, List<String> stations) {
        Map<String, List<String>> next = new LinkedHashMap<>(groups);
        next.put(groupName, new ArrayList<>(stations));
        replaceAll(next);
        if (config != null) {
            config.set(groupName, new ArrayList<>(stations));
            config.save();
        }
    }

    /**
     * 删除一个车站组并写回文件。
     *
     * @param groupName 组名
     * @return 原先存在返回 true
     */
    public static boolean delete(String groupName) {
        if (!groups.containsKey(groupName)) {
            return false;
        }
        Map<String, List<String>> next = new LinkedHashMap<>(groups);
        next.remove(groupName);
        replaceAll(next);
        if (config != null) {
            config.remove(groupName);
            config.save();
        }
        return true;
    }

    /**
     * 全部车站组（只读视图）。
     *
     * @return 组名 -> 组内车站
     */
    public static Map<String, List<String>> all() {
        return Collections.unmodifiableMap(groups);
    }
}
