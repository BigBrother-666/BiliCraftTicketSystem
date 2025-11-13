package com.bigbrother.bilicraftticketsystem.addon;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.addon.geodata.walkingpoint.PRGeoWalkingPoint;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddonConfig {
    private static FileConfiguration addonConfig;
    public static boolean realTimeDataEnabled;
    public static List<String> ignoreStationTag;
    public static ConfigurationNode startNode;

    public static void loadAddonConfig(BiliCraftTicketSystem plugin) {
        addonConfig = new FileConfiguration(plugin, EnumConfig.ADDON_CONFIG.getFileName());
        addonConfig.load();
        realTimeDataEnabled = addonConfig.get("geo-data.real-time-data", true);
        ignoreStationTag = addonConfig.getList("geo-data.ignore-station-tag", String.class);
        startNode = addonConfig.getNode("geo-data.start-node");
    }

    public static void saveAddonConfig() {
        addonConfig.save();
    }

    /**
     * 添加遍历起点
     *
     * @param platformTag 站台tag
     * @param location 位置
     * @param direction 方向
     */
    public static void addStartNode(String platformTag, Location location, Vector direction) {
        ConfigurationNode platformNode = new ConfigurationNode();

        ConfigurationNode directionNode = new ConfigurationNode();
        directionNode.set("x", direction.getX());
        directionNode.set("y", direction.getY());
        directionNode.set("z", direction.getZ());
        platformNode.set("direction", directionNode);

        platformNode.set("location", location);

        startNode.set(platformTag, platformNode);

        saveAddonConfig();
    }

    /**
     * 获取某mermaid节点的遍历起点数据
     *
     * @param node mermaid节点
     * @return 带位置方向信息的节点
     */
    public static @Nullable PRGeoWalkingPoint.WalkingPointNode getStartNodeData(MermaidGraph.Node node) {
        ConfigurationNode platformNode = startNode.getNode(node.getPlatformTag());
        if (platformNode == null || platformNode.isEmpty()) {
            return null;
        }

        Location location = platformNode.get("location", Location.class);
        if (location == null) {
            return null;
        }

        ConfigurationNode dirNode = platformNode.getNode("direction");
        if (dirNode == null || dirNode.isEmpty()) {
            return null;
        }
        Vector direction = new Vector(
                dirNode.get("x", Double.class),
                dirNode.get("y", Double.class),
                dirNode.get("z", Double.class)
        );

        return new PRGeoWalkingPoint.WalkingPointNode(node, direction, location);
    }
}
