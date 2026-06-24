package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Getter;

public class GeoConfig {
    private static FileConfiguration railwayGeoConfig;

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

    public static void loadRailwayGeoConfig(BiliCraftTicketSystem plugin) {
        railwayGeoConfig = new FileConfiguration(plugin, EnumConfig.GEO_CONFIG.getFileName());
        railwayGeoConfig.load();

        ConfigurationNode traversal = railwayGeoConfig.getNode("traversal");
        traversalMaxEdgesPerWalk = traversal.get("max-edges-per-walk", 5000);
        traversalMaxTotalNodes = traversal.get("max-total-nodes", 100000);
        traversalSegmentsPerTick = traversal.get("segments-per-tick", 20);
        traversalProgressIntervalSeconds = traversal.get("progress-interval-seconds", 5);
        traversalCooldownSeconds = traversal.get("cooldown-seconds", 3600);

        ConfigurationNode logo = railwayGeoConfig.getNode("logo");
        webLogoDim = logo.get("web-logo-dim", 128);
        mcLogoDim = logo.get("mc-logo-dim", 32);
    }

    public static void saveRailwayGeoConfig() {
        railwayGeoConfig.save();
    }
}
