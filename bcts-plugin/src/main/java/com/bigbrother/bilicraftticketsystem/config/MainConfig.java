package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class MainConfig {
    public static double maxSpeed;
    public static double minSpeed;
    public static double speedStep;
    public static String expressTicketName;
    public static String expressTicketBgimage;
    public static double pricePerKm;
    public static ConfigurationNode font;
    public static ConfigurationNode message;
    public static ConfigurationNode permDiscount;
    public static int maxUses;
    public static List<String> discount;
    public static List<String> expressSkipSigns;
    public static ConfigurationNode cardConfig;
    /**
     * 购票界面最多显示的车票数：即寻路返回的「距离最短的前 N 条路线」对应的车票数上限。
     * {@code <=0} 表示不限制（显示全部候选路线）。
     */
    public static int maxSearchResults;

    public static int loreStationNameCntRow;
    public static int loreRailwayNameCntRow;
    public static List<String> ticketLore;
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

    public static void loadMainConfig(BiliCraftTicketSystem plugin) {
        FileConfiguration mainConfig = new FileConfiguration(plugin, EnumConfig.MAIN_CONFIG.getFileName());
        mainConfig.load();

        expressTicketName = mainConfig.get("express-ticket-name", "express");
        expressTicketBgimage = mainConfig.get("express-ticket-bgimage", "");
        pricePerKm = mainConfig.get("price-per-km", 0.3);

        font = mainConfig.getNode("font");

        ConfigurationNode speed = mainConfig.getNode("speed");
        maxSpeed = speed.get("max", 5.0);
        minSpeed = speed.get("min", 2.0);
        speedStep = speed.get("step", 0.2);

        permDiscount = mainConfig.getNode("perm-discount");

        message = mainConfig.getNode("message");

        ConfigurationNode uses = mainConfig.getNode("uses");
        maxUses = uses.get("max", 50);
        discount = uses.getList("discount", String.class, Collections.emptyList());

        expressSkipSigns = mainConfig.getList("express-skip-signs", String.class, Collections.emptyList());

        maxSearchResults = mainConfig.get("max-search-results", 5);

        cardConfig = mainConfig.getNode("card");
        ConfigurationNode lore = mainConfig.getNode("lore");
        loreStationNameCntRow = lore.get("station-name-cnt-perrow", 7);
        loreRailwayNameCntRow = lore.get("railway-name-cnt-perrow", 4);
        ticketLore = lore.getList("ticket", String.class, Collections.emptyList());
        cardLore = lore.getList("card", String.class, Collections.emptyList());

        ConfigurationNode bossbar = mainConfig.getNode("bossbar");
        bossbarPassedColor = bossbar.get("passed-color", "&7");
        bossbarNotPassedColor = bossbar.get("not-passed-color", String.class, null);
        bossbarPassedNum = bossbar.get("passed-num", 2);
        bossbarNotPassedNum = bossbar.get("not-passed-num", 3);
    }
}
