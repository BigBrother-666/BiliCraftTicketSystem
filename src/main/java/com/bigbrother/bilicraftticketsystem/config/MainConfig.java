package com.bigbrother.bilicraftticketsystem.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Data;

import java.util.logging.Level;

@Data
public class MainConfig {
    public static double maxSpeed;
    public static double minSpeed;
    public static double speedStep;
    public static String expressTicketName;
    public static double pricePerKm;

    public static void loadMainConfig(BiliCraftTicketSystem plugin) {
        FileConfiguration itemsConfig = new FileConfiguration(plugin, "config.yml");
        itemsConfig.load();
        expressTicketName = itemsConfig.get("express-ticket-name", "express");
        pricePerKm = itemsConfig.get("price-per-km", 0.3);
        ConfigurationNode speed = itemsConfig.getNode("speed");
        maxSpeed = speed.get("max", 5.0);
        minSpeed = speed.get("min", 2.0);
        speedStep = speed.get("step", 0.2);
        plugin.getLogger().log(Level.INFO, "成功加载主配置！");
    }
}
