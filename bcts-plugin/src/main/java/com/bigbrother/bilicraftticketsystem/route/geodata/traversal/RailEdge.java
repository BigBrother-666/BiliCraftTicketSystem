package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import lombok.Getter;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.List;

/**
 * 铁路遍历过程中发现的一条区间（从一个节点到相邻节点的轨道段）。
 * <p>
 * 区间是有向的（from -> to），线段 id 由两端节点 id 和线路 id 确定性生成。
 * 同一物理区间被多条线路共用时，按线路各产生一条 RailEdge（geojson 中各占一条 feature，
 * 靠 layer 叠层显示）。
 */
@Getter
public class RailEdge {
    /**
     * 线段唯一 id。
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
     * 轨道坐标序列（经度=x, 纬度=z, 高度=y，沿用旧 geojson 约定）。
     */
    private final List<LngLatAlt> coordinates;
    /**
     * 显示颜色（来自线路配置）。
     */
    private final String color;
    /**
     * 区间长度（米，约等于轨道方块数）。
     */
    private final double length;
    /**
     * 叠层层级，越大越在上层。
     */
    private final int layer;

    /**
     * @param fromNodeId  起点节点 id
     * @param toNodeId    终点节点 id
     * @param lineId      所属线路 id
     * @param coordinates 轨道坐标序列
     * @param color       显示颜色
     * @param length      区间长度
     * @param layer       叠层层级
     */
    public RailEdge(String fromNodeId,
                    String toNodeId,
                    String lineId,
                    List<LngLatAlt> coordinates,
                    String color,
                    double length,
                    int layer) {
        this.id = NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.coordinates = coordinates == null ? new ArrayList<>() : coordinates;
        this.color = color;
        this.length = length;
        this.layer = layer;
    }
}
