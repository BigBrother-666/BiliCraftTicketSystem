package com.bigbrother.bilicraftticketsystem.route.geograph;

import lombok.Getter;

/**
 * 由 geojson 反向构建的路由图中的一条有向边（一段轨道区间）。
 * <p>
 * 对应 geojson 的一个 LineString feature。从 {@link #fromNodeId} 指向 {@link #toNodeId}，
 * 属于线路 {@link #lineId}，权重为 {@link #distance}（取 geojson 的 {@code length}）。
 * <p>
 * 纯数据对象，不依赖 Bukkit。
 */
@Getter
public class GeoLink {
    /**
     * 边唯一 id（geojson LineString 的 {@code id} 属性，格式 {@code e.lineId.from__to}）。
     */
    private final String id;
    /**
     * 起点节点 id。
     */
    private final String fromNodeId;
    /**
     * 终点节点 id。
     */
    private final String toNodeId;
    /**
     * 所属线路 id。
     */
    private final String lineId;
    /**
     * 边权（轨道区间长度，米/格）。
     */
    private final double distance;
    /**
     * 线路标志色（十六进制），仅供展示。
     */
    private final String color;

    /**
     * @param id         边 id
     * @param fromNodeId 起点节点 id
     * @param toNodeId   终点节点 id
     * @param lineId     所属线路 id
     * @param distance   边权（长度）
     * @param color      线路色
     */
    public GeoLink(String id, String fromNodeId, String toNodeId, String lineId, double distance, String color) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.distance = distance;
        this.color = color;
    }
}
