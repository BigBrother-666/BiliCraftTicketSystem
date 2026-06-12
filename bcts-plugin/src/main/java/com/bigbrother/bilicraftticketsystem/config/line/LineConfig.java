package com.bigbrother.bilicraftticketsystem.config.line;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线路配置（routes.yml）的加载器。
 * <p>
 * 读取 CLAUDE.md 描述的线路配置格式，
 * 每个顶层键是一个线路 id，解析为 {@link LineInfo}。特殊 id（contact / default）同样以
 * LineInfo 表示，缺失字段使用默认值。
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

        Map<String, LineInfo> parsed = new LinkedHashMap<>();
        for (ConfigurationNode node : config.getNodes()) {
            LineInfo info = parseNode(node);
            parsed.put(info.getId(), info);
        }
        lines = parsed;
    }

    /**
     * 把一个配置节点解析为 {@link LineInfo}。
     *
     * @param node 线路配置节点（节点名即线路 id）
     * @return 解析出的线路信息
     */
    private static LineInfo parseNode(ConfigurationNode node) {
        String id = node.getName();
        String lineName = node.get("line-name", id);
        String lineColor = node.get("line-color", "#a9a9a9");
        List<String> rawStations = node.getList("bossbar-stations", String.class, null);
        String bossbarArrivalNotice = node.get("bossbar-arrival-notice", String.class, null);
        String bossbarColor = node.get("bossbar-color", "WHITE");
        List<String> noticeArrival = node.getList("notice-arrival", String.class, null);
        List<String> noticeDeparture = node.getList("notice-departure", String.class, null);

        // 解析车站名后缀：站名写成 "站名:RV" 表示该站为折返站（尽头式，进站后反向驶出）。
        // 拆出干净站名列表 + 折返下标集合（用下标标记，环线重复站名也能精确区分）。
        List<String> bossbarStations = new java.util.ArrayList<>();
        java.util.Set<Integer> reverseStationIndices = new java.util.HashSet<>();
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
                lineName,
                lineColor,
                bossbarStations,
                reverseStationIndices,
                bossbarArrivalNotice,
                bossbarColor,
                noticeArrival,
                noticeDeparture
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
     * 判断给定线路 id 是否存在于配置中。
     *
     * @param lineId 线路 id
     * @return true 表示存在
     */
    public static boolean contains(String lineId) {
        return lines.containsKey(lineId);
    }

    /**
     * 获取所有非特殊（营运）线路的 id，按配置文件顺序。
     * <p>
     * 排除 contact / default 等特殊线路（它们不登记遍历起点）。
     *
     * @return 营运线路 id 有序列表
     */
    public static List<String> getNormalLineIds() {
        List<String> result = new java.util.ArrayList<>();
        for (Map.Entry<String, LineInfo> entry : lines.entrySet()) {
            if (!entry.getValue().isSpecial()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
