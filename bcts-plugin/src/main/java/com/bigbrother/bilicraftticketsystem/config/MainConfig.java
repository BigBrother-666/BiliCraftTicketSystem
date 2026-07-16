package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import lombok.Data;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.List;

@Data
public class MainConfig {
    public static Component prefix;
    public static double maxSpeed;
    public static double minSpeed;
    public static double speedStep;
    public static String expressTicketName;
    public static String expressTicketBgimage;
    public static double pricePerKm;
    /**
     * 创建 / 修改铁路系统时允许设置的每公里价格下限（含）。默认 0。
     */
    public static double pricePerKmMin;
    /**
     * 创建 / 修改铁路系统时允许设置的每公里价格上限（含）。默认 10。
     */
    public static double pricePerKmMax;
    /**
     * slowdown 控制牌预测 platform 的最大检测距离（block），超出仍未找到 platform 则不减速，
     * 防止玩家把 slowdown 放得过远导致的性能问题。
     */
    public static double slowdownMaxDetectDistance;
    public static ConfigurationNode font;
    public static ConfigurationNode message;
    public static ConfigurationNode permDiscount;
    public static int maxUses;
    public static List<String> discount;
    public static List<String> expressSkipSigns;
    public static ConfigurationNode cardConfig;
    /**
     * 购票搜索：显示「距离最近」的路线条数（{@code <=0} 不限制）。
     */
    public static int maxDistanceResults;
    /**
     * 购票搜索：显示「票价最低」的路线条数（{@code <=0} 不限制）。
     */
    public static int maxPriceResults;
    /**
     * 购票搜索混合排序：归一化距离的权重。
     */
    public static double searchWeightDistance;
    /**
     * 购票搜索混合排序：归一化票价的权重。
     */
    public static double searchWeightPrice;
    /**
     * 购票搜索兜底：混合排序结果里没有任何直达车票（全是联程票）时，至少保留最优的这么多条直达票
     * （若存在直达方案）。{@code <=0} 表示不兜底。
     */
    public static int minDirectResults;
    /**
     * 最多显示的联程票（换乘）方案条数（{@code <=0} 不限制，但仍受候选上限约束）。
     */
    public static int maxTransferResults;
    /**
     * 联程票最低改善比例：仅当换乘总距离 {@code < 最短直达 ×(1 - 此值)} 时才显示。两站无直达时不生效。
     */
    public static double transferMinImprovement;

    public static int loreStationNameCntRow;
    public static int loreRailwayNameCntRow;
    public static List<String> ticketLore;
    public static List<String> ticketPriceLore;
    public static String distanceInfoLore;
    /**
     * 联程票每段行程前的分隔行模板（占位符 {index} {total} {leg_start_station} {leg_end_station}）。
     */
    public static String throughTicketSeparator;
    public static List<String> cardLore;

    /**
     * 普通车 bossbar 滚动站名带样式：已过站颜色（{@code #RRGGBB} 或 legacy &-code）。
     */
    public static String bossbarPassedColor;
    /**
     * 普通车 bossbar 滚动站名带样式：未过站颜色（{@code #RRGGBB} 或 legacy &-code）；
     * 留空则回退到该线路的标志色（line-color）。
     */
    public static String bossbarNotPassedColor;
    /**
     * 普通车 bossbar 滚动站名带：已过站最大显示个数。
     */
    public static int bossbarPassedNum;
    /**
     * 普通车 bossbar 滚动站名带：未过站最大显示个数。
     */
    public static int bossbarNotPassedNum;

    /**
     * 「寻找上车站台」引导总开关。
     */
    public static boolean guideEnabled;
    /**
     * 引导到达判定：与站台的水平距离 &le; 此值（block）即视为到达。
     */
    public static double guideArriveDistance;
    /**
     * 引导超时上限（秒），{@code <=0} 表示不超时。
     */
    public static int guideTimeoutSeconds;
    /**
     * 引导刷新间隔（tick）。
     */
    public static int guideUpdateIntervalTicks;
    /**
     * 是否显示目的地粒子光柱。
     */
    public static boolean guideShowBeacon;
    /**
     * 是否显示站台头顶全息站名（TextDisplay）。
     */
    public static boolean guideShowHologram;
    /**
     * 全息站名文字大小。
     */
    public static float guideHologramTextSize;
    /**
     * 粒子光柱 / 全息文字的高度（block）。
     */
    public static double guideBeaconHeight;
    /**
     * 粒子光柱颜色（{@code #RRGGBB}），非法时回退金色。仅对 {@code DUST} 类粒子生效。
     */
    public static String guideBeaconColor;
    /**
     * 粒子光柱直径（block）。{@code <=0} 退化为一条居中竖线。
     */
    public static double guideBeaconDiameter;
    /**
     * 粒子光柱粒子类型（{@link org.bukkit.Particle} 枚举名，如 {@code DUST} / {@code FLAME} /
     * {@code END_ROD} / {@code HAPPY_VILLAGER}）。非法时回退 {@code DUST}。
     */
    public static String guideBeaconParticle;
    /**
     * 粒子光柱竖直方向相邻粒子层的间距（block），越小越密。下限 0.1。
     */
    public static double guideBeaconVerticalGap;

    public static void loadMainConfig(BiliCraftTicketSystem plugin) {
        FileConfiguration mainConfig = new FileConfiguration(plugin, EnumConfig.MAIN_CONFIG.getFileName());
        mainConfig.load();

        expressTicketName = mainConfig.get("express-ticket-name", "express");
        expressTicketBgimage = mainConfig.get("express-ticket-bgimage", "");
        pricePerKm = mainConfig.get("price-per-km", 0.3);

        ConfigurationNode priceRange = mainConfig.getNode("price-per-km-range");
        pricePerKmMin = priceRange.get("min", 0.0);
        pricePerKmMax = priceRange.get("max", 10.0);

        slowdownMaxDetectDistance = mainConfig.get("slowdown-max-detect-distance", 500.0);

        font = mainConfig.getNode("font");

        ConfigurationNode speed = mainConfig.getNode("speed");
        maxSpeed = speed.get("max", 5.0);
        minSpeed = speed.get("min", 2.0);
        speedStep = speed.get("step", 0.2);

        permDiscount = mainConfig.getNode("perm-discount");

        // 提示信息独立到 messages.yml
        FileConfiguration messagesConfig = new FileConfiguration(plugin, EnumConfig.MESSAGES_CONFIG.getFileName());
        messagesConfig.load();
        message = messagesConfig;
        prefix = CommonUtils.mmStr2Component(message.get("ticket-system-prefix", "<gold>[帕拉伦国有铁路车票系统]"));

        ConfigurationNode uses = mainConfig.getNode("uses");
        maxUses = uses.get("max", 50);
        discount = uses.getList("discount", String.class, Collections.emptyList());

        expressSkipSigns = mainConfig.getList("express-skip-signs", String.class, Collections.emptyList());

        // 搜索结果配置（兼容旧顶层 max-search-results 作为默认值）
        int legacyMaxResults = mainConfig.get("max-search-results", 5);
        ConfigurationNode search = mainConfig.getNode("search");
        maxDistanceResults = search.get("max-distance-results", legacyMaxResults);
        maxPriceResults = search.get("max-price-results", legacyMaxResults);
        searchWeightDistance = search.get("weight-distance", 0.5);
        searchWeightPrice = search.get("weight-price", 0.5);
        minDirectResults = search.get("min-direct-results", 1);
        maxTransferResults = search.get("max-transfer-results", 3);
        transferMinImprovement = search.get("transfer-min-improvement", 0.2);

        cardConfig = mainConfig.getNode("card");
        ConfigurationNode lore = mainConfig.getNode("lore");
        loreStationNameCntRow = lore.get("station-name-cnt-perrow", 7);
        loreRailwayNameCntRow = lore.get("railway-name-cnt-perrow", 4);
        ticketLore = lore.getList("ticket", String.class, Collections.emptyList());
        ticketPriceLore = lore.getList("ticket-price", String.class, Collections.emptyList());
        distanceInfoLore = lore.get("distance-info-lore", "<gold>{railway_system} <dark_aqua>{system_distance} <dark_purple>{system_price}");
        throughTicketSeparator = lore.get("through-ticket-separator",
                "<!italic><dark_gray>========= <gold>行程 {index}/{total} <gray>{leg_start_station}→{leg_end_station} <dark_gray>=========");
        cardLore = lore.getList("card", String.class, Collections.emptyList());

        ConfigurationNode bossbar = mainConfig.getNode("bossbar");
        bossbarPassedColor = bossbar.get("passed-color", "&7");
        bossbarNotPassedColor = bossbar.get("not-passed-color", String.class, null);
        bossbarPassedNum = bossbar.get("passed-num", 2);
        bossbarNotPassedNum = bossbar.get("not-passed-num", 3);

        ConfigurationNode guide = mainConfig.getNode("guide");
        guideEnabled = guide.get("enabled", true);
        guideArriveDistance = guide.get("arrive-distance", 3.0);
        guideTimeoutSeconds = guide.get("timeout-seconds", 60);
        guideUpdateIntervalTicks = guide.get("update-interval-ticks", 5);
        guideShowBeacon = guide.get("show-beacon", true);
        guideShowHologram = guide.get("show-hologram", true);
        guideHologramTextSize = guide.get("hologram-text-size", 1.2f);
        guideBeaconHeight = guide.get("beacon-height", 4.0);
        guideBeaconColor = guide.get("beacon-color", "#FFAA00");
        guideBeaconDiameter = guide.get("beacon-diameter", 0.6);
        guideBeaconParticle = guide.get("beacon-particle", "DUST");
        guideBeaconVerticalGap = guide.get("beacon-vertical-gap", 0.5);
    }
}
