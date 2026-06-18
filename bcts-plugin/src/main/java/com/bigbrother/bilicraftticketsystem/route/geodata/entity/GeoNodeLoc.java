package com.bigbrother.bilicraftticketsystem.route.geodata.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * 一条线路的遍历起点：起始铁轨坐标 + 方向，按线路 id 登记。
 * <p>
 * 遍历方案按线路驱动——每条线路登记一个起点，遍历时矿车带该 lineId 的 tag 从此处出发，
 * 沿轨道靠 bcswitcher 按 tag 选向，走完该线路。
 */
@Getter
@RequiredArgsConstructor
public class GeoNodeLoc {
    /**
     * 线路 id（railway_routes.yml 中的键，如 "pr-cw"）。
     */
    private final String lineId;
    /**
     * 起始铁轨坐标。
     */
    private final Location startLocation;
    /**
     * 起始行走方向。
     */
    private final Vector startDirection;
}
