package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

@Data
public class MainConfig {
    public static double maxSpeed;
    public static double minSpeed;
    public static double speedStep;
    public static String expressTicketName;
    public static int expressTicketVersion;
    public static String commonTrainTag;
    public static String ticketFont;
    public static Boolean ticketFontBold;
    public static double pricePerKm;
    public static ConfigurationNode message;
    public static ConfigurationNode railwayRoutes;
    public static ConfigurationNode permDiscount;
    public static ConfigurationNode railwayColor;
    public static int maxUses;
    public static List<String> discount;
    public static String skip;

    public static void loadMainConfig(BiliCraftTicketSystem plugin) {
        FileConfiguration mainConfig = new FileConfiguration(plugin, EnumConfig.MAIN_CONFIG.getFileName());
        mainConfig.load();

        expressTicketName = mainConfig.get("express-ticket-name", "express");
        expressTicketVersion = mainConfig.get("express-ticket-version", 1);
        commonTrainTag = mainConfig.get("common-train-tag", "common");
        ticketFont = mainConfig.get("ticket-font", "");
        ticketFontBold = mainConfig.get("ticket-font-bold", true);
        pricePerKm = mainConfig.get("price-per-km", 0.3);

        ConfigurationNode speed = mainConfig.getNode("speed");
        maxSpeed = speed.get("max", 5.0);
        minSpeed = speed.get("min", 2.0);
        speedStep = speed.get("step", 0.2);

        railwayRoutes = mainConfig.getNode("railway-routes");

        permDiscount = mainConfig.getNode("perm-discount");

        railwayColor = mainConfig.getNode("railway-color");

        message = mainConfig.getNode("message");

        ConfigurationNode uses = mainConfig.getNode("uses");
        maxUses = uses.get("max", 50);
        discount = uses.getList("discount", String.class, Collections.emptyList());

        skip = mainConfig.get("skip", "");

        plugin.getLogger().log(Level.INFO, "成功加载主配置！");
    }
}
