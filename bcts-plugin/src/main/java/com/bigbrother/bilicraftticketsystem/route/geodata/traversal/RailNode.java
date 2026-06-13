package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import lombok.Getter;
import org.bukkit.block.Block;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 铁路遍历过程中发现的一个节点（车站或道岔）。
 * <p>
 * 节点 id 由其所在铁轨方块坐标确定性生成（见 {@link NodeId}），因此同一物理位置多次遍历
 * 得到相同 id，便于 geojson 幂等更新。
 */
@Getter
public class RailNode {
    /**
     * 节点类型。
     */
    @Getter
    public enum Type {
        /**
         * 车站节点（platform 控制牌），在前端地图上显示。
         */
        STATION("station"),
        /**
         * 道岔节点（bcswitcher 控制牌），前端不显示。
         */
        SWITCH("switch");

        private final String value;

        Type(String value) {
            this.value = value;
        }
    }

    /**
     * 节点唯一 id。
     */
    private final String id;
    /**
     * 节点类型。
     */
    private final Type type;
    /**
     * 节点所在铁轨方块。
     */
    private final Block railBlock;
    /**
     * 车站名（仅 STATION 类型有，SWITCH 为 null）。
     */
    private final String stationName;
    /**
     * 经过该节点的线路 id 集合（遍历过程中累积）。
     */
    private final Set<String> lineIds = new LinkedHashSet<>();
    /**
     * 经过该节点的线路所属铁路系统 id 集合（遍历过程中累积，去重）。
     * <p>
     * 一个车站可能被分属不同铁路系统的多条线路经过，故为集合。联络线不贡献系统 id。
     */
    private final Set<String> railwaySystemIds = new LinkedHashSet<>();

    /**
     * @param type        节点类型
     * @param railBlock   节点所在铁轨方块
     * @param stationName 车站名（SWITCH 传 null）
     */
    public RailNode(Type type, Block railBlock, String stationName) {
        this.id = NodeId.ofBlock(railBlock);
        this.type = type;
        this.railBlock = railBlock;
        this.stationName = stationName;
    }

    /**
     * 记录一条经过该节点的线路。
     *
     * @param lineId 线路 id
     */
    public void addLineId(String lineId) {
        if (lineId != null && !lineId.isEmpty()) {
            lineIds.add(lineId);
        }
    }

    /**
     * 记录一个经过该节点的铁路系统 id。
     *
     * @param railwaySystemId 铁路系统 id（null / 空忽略）
     */
    public void addRailwaySystemId(String railwaySystemId) {
        if (railwaySystemId != null && !railwaySystemId.isEmpty()) {
            railwaySystemIds.add(railwaySystemId);
        }
    }

    /**
     * 是否为车站节点。
     *
     * @return true 表示车站
     */
    public boolean isStation() {
        return type == Type.STATION;
    }
}
