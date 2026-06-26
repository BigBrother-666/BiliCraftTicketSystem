package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Getter;

/**
 * 读取 {@code config_map.yml}（对接铁路线路图 Web 后端的配置）。
 * 启动 / reload 时由主插件调用 {@link #loadMapConfig}。
 * <p>
 * {@link #enabled} 为 {@code false} 时插件完全不连后端，零侵入。
 */
public class MapConfig {
    private static FileConfiguration webConfig;

    /**
     * 是否启用 Web 后端对接。
     */
    @Getter
    private static boolean enabled;
    /**
     * 后端内部 WebSocket 端点（wss://.../internal/plugin）。
     */
    @Getter
    private static String backendUrl;
    /**
     * 与后端一致的预共享密钥。
     */
    @Getter
    private static String sharedToken;
    /**
     * 多服标识。
     */
    @Getter
    private static String serverId;
    /**
     * 断线重连间隔（秒）。
     */
    @Getter
    private static int reconnectSeconds;

    /**
     * 是否推送列车遥测。
     */
    @Getter
    private static boolean telemetryEnabled;
    /**
     * 列车遥测推送间隔（tick）。
     */
    @Getter
    private static int telemetryIntervalTicks;
    /**
     * 是否推送无乘客的列车。
     */
    @Getter
    private static boolean telemetryIncludeEmptyTrains;

    /**
     * 是否接受网页在线购票。
     */
    @Getter
    private static boolean purchaseEnabled;
    /**
     * 网页单次下单允许的最大次数。
     */
    @Getter
    private static int purchaseMaxUses;
    /**
     * 仅在线交付（玩家离线则购票失败）。
     */
    @Getter
    private static boolean purchaseRequireOnline;


    /**
     * 单段行走最多记录的铁轨格数。
     */
    @Getter
    private static int traversalMaxEdgesPerWalk;
    /**
     * 整次遍历最多展开的段数（跨所有起点，兜底防环）。
     */
    @Getter
    private static int traversalMaxTotalNodes;
    /**
     * 分片遍历：每个 tick（主线程）最多展开的段数。值越小对其它玩家影响越小、但遍历越慢。
     */
    @Getter
    private static int traversalSegmentsPerTick;
    /**
     * 每隔多少秒向发起者反馈一次遍历进度，{@code <=0} 表示不反馈。
     */
    @Getter
    private static int traversalProgressIntervalSeconds;
    /**
     * 一次遍历完成后的全局冷却（秒）。
     */
    @Getter
    private static int traversalCooldownSeconds;
    /**
     * 网页端logo边长（像素）
     */
    @Getter
    private static int webLogoDim;
    /**
     * 游戏内logo边长（像素）
     */
    @Getter
    private static int mcLogoDim;


    public static void loadMapConfig(BiliCraftTicketSystem plugin) {
        webConfig = new FileConfiguration(plugin, EnumConfig.WEB_CONFIG.getFileName());
        webConfig.load();

        ConfigurationNode root = webConfig.getNode("web-link");
        enabled = root.get("enabled", false);
        backendUrl = root.get("backend-url", "");
        sharedToken = root.get("shared-token", "");
        serverId = root.get("server-id", "paralon");
        reconnectSeconds = root.get("reconnect-seconds", 5);

        ConfigurationNode telemetry = root.getNode("telemetry");
        telemetryEnabled = telemetry.get("enabled", true);
        telemetryIntervalTicks = telemetry.get("interval-ticks", 5);
        telemetryIncludeEmptyTrains = telemetry.get("include-empty-trains", false);

        ConfigurationNode purchase = root.getNode("purchase");
        purchaseEnabled = purchase.get("enabled", true);
        purchaseMaxUses = purchase.get("max-uses", 1);
        purchaseRequireOnline = purchase.get("require-online", true);

        ConfigurationNode traversal = MapConfig.webConfig.getNode("traversal");
        MapConfig.traversalMaxEdgesPerWalk = traversal.get("max-edges-per-walk", 5000);
        MapConfig.traversalMaxTotalNodes = traversal.get("max-total-nodes", 100000);
        MapConfig.traversalSegmentsPerTick = traversal.get("segments-per-tick", 20);
        MapConfig.traversalProgressIntervalSeconds = traversal.get("progress-interval-seconds", 5);
        MapConfig.traversalCooldownSeconds = traversal.get("cooldown-seconds", 3600);

        ConfigurationNode logo = MapConfig.webConfig.getNode("logo");
        MapConfig.webLogoDim = logo.get("web-logo-dim", 128);
        MapConfig.mcLogoDim = logo.get("mc-logo-dim", 32);
    }
}
