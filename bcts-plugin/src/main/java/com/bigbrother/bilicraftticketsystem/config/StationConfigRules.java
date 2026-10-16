package com.bigbrother.bilicraftticketsystem.config;

import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 车站别名 / 车站组的共享校验规则，游戏内指令（{@code /ticketconfig editStationAlias / editStationGroup}）与
 * 网页端配置请求共用，保证两端规则一致。
 */
public final class StationConfigRules {
    private StationConfigRules() {
    }

    /**
     * 规范化英文站名：去首尾空白，合并连续空白为单个空格。
     *
     * @param englishName 原始英文名（可空）
     * @return 规范化后的英文名；空白返回空串
     */
    public static String normalizeEnglishName(String englishName) {
        return englishName == null ? "" : englishName.trim().replaceAll("\\s+", " ");
    }

    /**
     * 车站去重（保持顺序），去除首尾空白与空项。
     *
     * @param stations 原始车站
     * @return 去重后的车站集合
     */
    public static Set<String> distinctStations(Collection<String> stations) {
        Set<String> unique = new LinkedHashSet<>();
        if (stations != null) {
            for (String s : stations) {
                if (s != null && !s.isBlank()) {
                    unique.add(s.trim());
                }
            }
        }
        return unique;
    }

    /**
     * 车站组冲突检查：一个车站最多属于一个车站组（覆盖本组时，本组原有车站不算冲突）。
     *
     * @param groupName 要写入的组名
     * @param stations  组内车站
     * @return 冲突车站 -> 已所在组名（无冲突为空 Map）
     */
    public static Map<String, String> groupConflicts(String groupName, Collection<String> stations) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        for (String station : stations) {
            String owner = StationGroupConfig.groupOf(station);
            if (owner != null && !owner.equals(groupName)) {
                conflicts.put(station, owner);
            }
        }
        return conflicts;
    }

    /**
     * 当前线路图中不存在的车站（仍允许保存：线路图可能尚未构建）。
     *
     * @param stations 待检查车站
     * @return 缺失车站列表
     */
    public static List<String> missingStations(Collection<String> stations) {
        Collection<String> known = GeoRouteEngine.getGraph().allStationNames();
        List<String> missing = new ArrayList<>();
        for (String station : stations) {
            if (!known.contains(station)) {
                missing.add(station);
            }
        }
        return missing;
    }

    /**
     * 缺失车站的警告文本（游戏内指令与网页端相同）；无缺失返回 null。
     *
     * @param stations 待检查车站
     * @return 警告文本或 null
     */
    public static String missingStationsWarning(Collection<String> stations) {
        List<String> missing = missingStations(stations);
        if (missing.isEmpty()) {
            return null;
        }
        return "警告：当前线路图中不存在车站 %s，仍将保存。".formatted(String.join("、", missing));
    }
}
