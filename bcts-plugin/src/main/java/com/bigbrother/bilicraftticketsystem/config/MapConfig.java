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
    private static int maxReconnectSeconds;

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
     * 分片遍历：每隔多少 tick 在主线程展开一批边。
     */
    @Getter
    private static int traversalIntervalTicks;
    /**
     * 分片遍历：每批最多展开的边数。
     */
    @Getter
    private static int traversalEdgesPerInterval;
    /**
     * 每隔多少秒向发起者反馈一次遍历进度，{@code <=0} 表示不反馈。
     */
    @Getter
    private static int traversalProgressIntervalSeconds;
    /**
     * 一次 {@code walkAll}（全图遍历）完成后的冷却（秒）。与 {@code walk} 冷却独立计时。
     */
    @Getter
    private static int traversalCooldownSeconds;
    /**
     * 一次 {@code walk}（单线增量遍历）完成后的冷却（秒）。与 {@code walkAll} 冷却独立计时。
     */
    @Getter
    private static int walkCooldownSeconds;
    /**
     * TCC 云轨（TCCoasters 虚拟曲线轨）几何采样步长，单位方块。
     * <p>
     * <b>仅作用于 TCCoasters 的曲线轨</b>：遍历经过这类轨道时，按此步长用列车真实浮点位置密采，
     * 使弧线在 geojson 里画成弧而非直线。<b>普通铁轨（原版轨 / 其它 RailType）的遍历逻辑与采样一概不变</b>
     * （仍逐轨道方块取整数坐标），本配置对其零影响。
     * <p>
     * {@code <=0} 时完全关闭云轨密采、退回旧行为（云轨也逐段取整点，弧线会呈直线）。值越小弧线越平滑、
     * 但 LineString 顶点越多；0.5 是兼顾观感与体积的推荐值。仅影响几何精度，不改变遍历拓扑 / 车站 / 寻路。
     * <p>
     * 实际步长被夹在 {@code 1.0} 格以内（大于 1 的配置按 1 处理）：保证密采一步不跨过整格铁轨、
     * 不漏掉其上的节点牌 / addtag，使采样率参数对遍历行为始终安全。
     */
    @Getter
    private static double traversalCoasterSampleStep;
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
        maxReconnectSeconds = root.get("reconnect-seconds", 5);

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
        MapConfig.traversalIntervalTicks = Math.max(1, traversal.get("interval-ticks", 1));
        MapConfig.traversalEdgesPerInterval = Math.max(1, traversal.get("edges-per-interval", 20));
        MapConfig.traversalProgressIntervalSeconds = traversal.get("progress-interval-seconds", 5);
        MapConfig.traversalCooldownSeconds = traversal.get("cooldown-seconds", 3600);
        MapConfig.walkCooldownSeconds = traversal.get("walk-cooldown-seconds", 300);
        MapConfig.traversalCoasterSampleStep = traversal.get("coaster-sample-step", 0.5);

        ConfigurationNode logo = MapConfig.webConfig.getNode("logo");
        MapConfig.webLogoDim = logo.get("web-logo-dim", 128);
        MapConfig.mcLogoDim = logo.get("mc-logo-dim", 32);
    }
}
