package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 由 geojson 反向构建的路由图中的一个节点（车站或道岔）。
 * <p>
 * 对应 geojson 的一个 Point feature。节点 id 由物理坐标确定性生成（见
 * {@link NodeId}），同一物理位置在不同线路文件里
 * 共享同一 id，加载时按 id 合并、累积 {@link #lineIds}。
 * <p>
 * 纯数据对象，不依赖 Bukkit，便于单元测试。
 */
@Getter
public class GeoNode {
    /**
     * 节点类型字符串，与 geojson Point 的 {@code type} 属性一致："station" 或 "switch"。
     */
    public static final String TYPE_STATION = "station";
    public static final String TYPE_SWITCH = "switch";

    /**
     * 节点唯一 id（geojson Point 的 {@code id} 属性，格式 {@code n.world.x.y.z}）。
     */
    private final String id;
    /**
     * 节点类型（{@link #TYPE_STATION} / {@link #TYPE_SWITCH}）。
     */
    private final String type;
    /**
     * 车站名（仅 station 节点有，switch 为 null）。
     */
    private final String name;
    /**
     * 坐标：经度=x、纬度=z、高度=y（与 geojson LngLatAlt 约定一致）。
     */
    private final double x;
    private final double y;
    private final double z;
    /**
     * 经过该节点的线路 id 集合（加载时跨文件累积）。
     */
    private final Set<String> lineIds = new LinkedHashSet<>();

    /**
     * @param id   节点 id
     * @param type 节点类型字符串
     * @param name 车站名（switch 传 null）
     * @param x    经度(=方块 x)
     * @param y    高度(=方块 y)
     * @param z    纬度(=方块 z)
     */
    public GeoNode(String id, String type, String name, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 是否为车站节点。
     *
     * @return true 表示车站
     */
    public boolean isStation() {
        return TYPE_STATION.equals(type);
    }

    /**
     * 从节点 id 解析世界名（id 格式 {@code n.world.x.y.z}）。
     *
     * @return 世界名，解析失败返回 null
     */
    public String getWorld() {
        return NodeId.worldOf(id);
    }

    /**
     * 累积一条经过该节点的线路 id。
     *
     * @param lineId 线路 id
     */
    public void addLineId(String lineId) {
        if (lineId != null && !lineId.isEmpty()) {
            lineIds.add(lineId);
        }
    }

    /**
     * 判断两个node对应铁轨坐标是否相同
     */
    public boolean coordEquals(GeoNode other) {
        return Double.compare(this.x, other.x) == 0 && Double.compare(this.y, other.y) == 0 && Double.compare(this.z, other.z) == 0;
    }
}
