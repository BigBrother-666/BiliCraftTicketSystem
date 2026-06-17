package com.bigbrother.bilicraftticketsystem.config.line;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import lombok.Getter;

import java.util.*;

/**
 * 线路配置（routes.yml）的加载器。
 * <p>
 * 读取 CLAUDE.md 描述的线路配置格式，
 * 每个顶层键是一个线路 id，解析为 {@link LineInfo}，缺失字段使用默认值。
 */
public class LineConfig {
    /**
     * 线路 id -> 线路信息，使用 LinkedHashMap 保持配置文件中的顺序。
     */
    @Getter
    private static Map<String, LineInfo> lines = new LinkedHashMap<>();

    private static FileConfiguration config;

    /**
     * 加载 routes.yml，解析所有线路配置。插件启动与 reload 时调用。
     *
     * @param plugin 插件实例
     */
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.ROUTES_CONFIG.getFileName());
        config.load();

        // 先收集所有线路 id，供解析时判断 bossbar-stations 最后一项是否为「转线目标线路 id」。
        Set<String> allLineIds = new HashSet<>();
        for (ConfigurationNode node : config.getNodes()) {
            allLineIds.add(node.getName());
        }

        Map<String, LineInfo> parsed = new LinkedHashMap<>();
        for (ConfigurationNode node : config.getNodes()) {
            LineInfo info = parseNode(node, allLineIds);
            parsed.put(info.getId(), info);
        }
        lines = parsed;
    }

    /**
     * 把一个配置节点解析为 {@link LineInfo}。
     *
     * @param node       线路配置节点（节点名即线路 id）
     * @param allLineIds 所有已知线路 id 集合（用于判断车站列表末项是否为转线目标线路 id）
     * @return 解析出的线路信息
     */
    private static LineInfo parseNode(ConfigurationNode node, Set<String> allLineIds) {
        String id = node.getName();
        String railwaySystemId = node.get("railway-system", String.class, null);
        String lineName = node.get("line-name", id);
        String lineColor = node.get("line-color", "#a9a9a9");
        List<String> rawStations = node.getList("bossbar-stations", String.class, null);
        String bossbarArrivalNotice = node.get("bossbar-arrival-notice", String.class, null);
        String bossbarColor = node.get("bossbar-color", "WHITE");
        List<String> noticeArrival = node.getList("notice-arrival", String.class, null);
        List<String> noticeDeparture = node.getList("notice-departure", String.class, null);

        // 转线目标：bossbar-stations 最后一项若是 "<线路id>" 或 "<线路id>:<进入站名>"（线路 id 为某条
        // 已存在线路、非本线），表示普通车到达本线终点站后转入该线路。该项不当站名处理（从车站列表剥离）。
        // 仅末项可填线路 id。冒号后为转线后的进入站名（即下一站，可跳过目标线路靠前的车站）。
        String nextLineId = null;
        String nextLineEntryStation = null;
        if (rawStations != null && !rawStations.isEmpty()) {
            String last = rawStations.getLast();
            if (last != null) {
                String trimmed = last.trim();
                int sep = trimmed.indexOf(':');
                String candidateId = sep >= 0 ? trimmed.substring(0, sep).trim() : trimmed;
                if (allLineIds.contains(candidateId) && !candidateId.equals(id)) {
                    nextLineId = candidateId;
                    nextLineEntryStation = sep >= 0 ? trimmed.substring(sep + 1).trim() : "";
                    rawStations = new ArrayList<>(rawStations.subList(0, rawStations.size() - 1));
                }
            }
        }

        // 解析车站名后缀：站名写成 "站名:RV" 表示该站为折返站（尽头式，进站后反向驶出）。
        // 拆出干净站名列表 + 折返下标集合（用下标标记，环线重复站名也能精确区分）。
        List<String> bossbarStations = new ArrayList<>();
        Set<Integer> reverseStationIndices = new HashSet<>();
        if (rawStations != null) {
            for (int i = 0; i < rawStations.size(); i++) {
                String raw = rawStations.get(i);
                String name = raw;
                int sep = raw.lastIndexOf(':');
                if (sep >= 0 && "RV".equalsIgnoreCase(raw.substring(sep + 1).trim())) {
                    name = raw.substring(0, sep).trim();
                    reverseStationIndices.add(i);
                }
                bossbarStations.add(name);
            }
        }

        return new LineInfo(
                id,
                railwaySystemId,
                lineName,
                lineColor,
                bossbarStations,
                reverseStationIndices,
                bossbarArrivalNotice,
                bossbarColor,
                noticeArrival,
                noticeDeparture,
                nextLineId,
                nextLineEntryStation
        );
    }

    /**
     * 根据线路 id 获取线路信息。
     *
     * @param lineId 线路 id
     * @return 线路信息，不存在时返回 null
     */
    public static LineInfo get(String lineId) {
        return lines.get(lineId);
    }

    /**
     * 获取线路标志色，找不到线路时返回灰色默认值。
     *
     * @param lineId 线路 id
     * @return 十六进制颜色字符串
     */
    public static String getColor(String lineId) {
        LineInfo info = lines.get(lineId);
        return info == null ? "#a9a9a9" : info.getLineColor();
    }

    /**
     * 获取线路所属铁路系统 id。
     *
     * @param lineId 线路 id
     * @return 铁路系统 id；线路不存在返回 null
     */
    public static String getSystemId(String lineId) {
        LineInfo info = lines.get(lineId);
        if (info == null) {
            return null;
        }
        return info.getRailwaySystemId();
    }

    /**
     * 判断给定线路 id 是否存在于配置中。
     *
     * @param lineId 线路 id
     * @return true 表示存在
     */
    public static boolean contains(String lineId) {
        return lines.containsKey(lineId);
    }

    /**
     * 新建或更新一条线路，并写回 routes.yml（保留注释）。
     * <p>
     * 仅写文件，不刷新内存缓存——调用方应在写回后触发配置重载
     * （{@link BiliCraftTicketSystem#loadConfig}）使其生效。选填字段传 null / 空集合表示
     * 不写该项（移除已存在的键），必填字段调用方应保证非空。
     *
     * @param lineId               线路 id
     * @param railwaySystemId      所属铁路系统 id
     * @param lineName             线路名称
     * @param lineColor            线路标志色（#RRGGBB）
     * @param bossbarStations      车站列表（原样，含 :RV 后缀）
     * @param bossbarArrivalNotice 到站 bossbar 提示（可空）
     * @param bossbarColor         bossbar 颜色名（可空）
     * @param noticeArrival        进站提示列表（可空）
     * @param noticeDeparture      出站提示列表（可空）
     */
    public static void upsert(String lineId,
                              String railwaySystemId,
                              String lineName,
                              String lineColor,
                              List<String> bossbarStations,
                              String bossbarArrivalNotice,
                              String bossbarColor,
                              List<String> noticeArrival,
                              List<String> noticeDeparture) {
        ConfigurationNode node = config.getNode(lineId);
        node.set("railway-system", railwaySystemId);
        node.set("line-name", lineName);
        node.set("line-color", lineColor);
        node.set("bossbar-stations", bossbarStations);
        setOrRemove(node, "bossbar-arrival-notice", bossbarArrivalNotice);
        setOrRemove(node, "bossbar-color", bossbarColor);
        setOrRemoveList(node, "notice-arrival", noticeArrival);
        setOrRemoveList(node, "notice-departure", noticeDeparture);
        config.save();
    }

    /**
     * 删除一条线路，并写回 routes.yml（保留其余注释）。
     * <p>
     * 仅写文件，不刷新内存缓存——调用方应在写回后触发配置重载
     * （{@link BiliCraftTicketSystem#loadConfig} 或 {@link #load}）使其生效。
     *
     * @param lineId 线路 id
     * @return true 表示该线路存在并已删除；false 表示线路不存在（未改动文件）
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean delete(String lineId) {
        if (lineId == null || !config.contains(lineId)) {
            return false;
        }
        config.remove(lineId);
        config.save();
        return true;
    }

    /**
     * 获取归属于某铁路系统的所有线路 id（按配置文件顺序），基于当前内存缓存。
     *
     * @param systemId 铁路系统 id
     * @return 该系统下的线路 id 列表（systemId 为 null / 无匹配时返回空列表）
     */
    public static List<String> getLineIdsOfSystem(String systemId) {
        List<String> result = new ArrayList<>();
        if (systemId == null) {
            return result;
        }
        for (LineInfo info : lines.values()) {
            if (systemId.equals(info.getRailwaySystemId())) {
                result.add(info.getId());
            }
        }
        return result;
    }

    private static void setOrRemove(ConfigurationNode node, String key, String value) {        if (value == null || value.isBlank()) {
            node.remove(key);
        } else {
            node.set(key, value);
        }
    }

    private static void setOrRemoveList(ConfigurationNode node, String key, List<String> value) {
        if (value == null || value.isEmpty()) {
            node.remove(key);
        } else {
            node.set(key, value);
        }
    }
}
