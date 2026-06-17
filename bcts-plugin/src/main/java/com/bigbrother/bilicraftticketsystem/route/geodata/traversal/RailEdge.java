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
     * 所属铁路系统 id（来自该区间所属线路的 {@code railway-system}）。线路未配置系统时为 null。
     */
    private final String railwaySystemId;
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
     * 本段的物理出向（{@code e/s/w/n} 或 {@code f/b/l/r}）——即离开起点节点（道岔）所走的方向。
     * platform 续行段 / 起点首段无道岔决策时为 null。运行时道岔据此对带导航的列车直接选向，消除
     * 同一出向多线路或正线/到发线共用 lineId 的歧义。
     */
    private final String departDirection;

    /**
     * @param fromNodeId      起点节点 id
     * @param toNodeId        终点节点 id
     * @param lineId          所属线路 id
     * @param railwaySystemId 所属铁路系统 id（未配置为 null）
     * @param coordinates     轨道坐标序列
     * @param color           显示颜色
     * @param length          区间长度
     * @param layer           叠层层级
     * @param departDirection 物理出向（无道岔决策传 null）
     */
    public RailEdge(String fromNodeId,
                    String toNodeId,
                    String lineId,
                    String railwaySystemId,
                    List<LngLatAlt> coordinates,
                    String color,
                    double length,
                    int layer,
                    String departDirection) {
        this.id = NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.railwaySystemId = railwaySystemId;
        this.coordinates = coordinates == null ? new ArrayList<>() : coordinates;
        this.color = color;
        this.length = length;
        this.layer = layer;
        this.departDirection = departDirection;
    }
}
