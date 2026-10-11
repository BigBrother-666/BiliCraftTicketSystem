package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个<b>遍历单元</b>（{@link UnitWalk}）的结果收集器：累积该单元走出的全部节点与区间。
 * <p>
 * <b>一个单元 = 一个输出文件。</b>单元 L（从 L 的登记起点出发）走出的一切内容都写进
 * {@code L.geojson}，与各区间自身的 {@code lineId} 无关——这是「文件归属按产出单元、而非按
 * lineId」的核心：
 * <ul>
 *   <li>一条边归属哪个 lineId，由 bcswitcher 的出向声明决定；</li>
 *   <li>但<b>哪一次行走能产出它</b>，取决于从哪个节点进去。</li>
 * </ul>
 * 旧实现按 lineId 分文件，于是「L2 的道岔上声明 {@code r@L1} 的出向」产出的边要写进
 * {@code L1.geojson}，而只有遍历 L2 才能走出它——{@code walk L1} 整体覆盖就会丢掉这条边。
 * 为此旧实现不得不读回旧文件、猜哪些旧边「本次结构上不可能重现」从而保留，规则层层叠加且难以推理。
 * 按单元分文件后这个问题根本不存在：该边由单元 L2 产出，就写在 {@code L2.geojson} 里，
 * {@code walk L2} 重写它、{@code walk L1} 完全不需要知道它存在。
 * <p>
 * 因此<b>每个文件只有一个写者</b>，保存就是「整体覆盖」，不读旧文件、不合并、不保留。
 * <p>
 * 文件名只是存储分区、不是语义：{@code GeoGraphLoader} 把目录下所有 geojson 读进同一张图、
 * 按 feature 的 {@code lineId} 属性建边；{@code SnapshotPublisher} 也是先合并整个目录再推送。
 * 没有任何消费者假设「文件名 == 边的 lineId」。
 * <p>
 * 同一条边（同 edge id）可能被<b>两个单元</b>各产出一份（共用轨道 / 越界边），各带不同的
 * {@code enterFrom} 到达面。合并在读取侧统一处理（{@code GeoGraphLoader#addLink} 与
 * {@code SnapshotPublisher#mergeGeojsonDir} 按 id 去重、{@code enterFrom} 取并集）。
 */
public class TraversalCollector {
    /**
     * 本单元的节点注册表：节点 id -> 节点（按 id 去重，累积经过的 lineId）。
     */
    private final Map<String, RailNode> nodes = new LinkedHashMap<>();
    /**
     * 本单元的区间：区间 id -> 区间。
     */
    private final Map<String, RailEdge> edges = new LinkedHashMap<>();

    /**
     * 取得（或新建）一个节点。已存在则复用，保证同物理位置在本单元内去重。
     *
     * @param type        节点类型
     * @param railBlock   节点所在铁轨方块
     * @param stationName 车站名（SWITCH 传 null）
     * @return 节点
     */
    public RailNode resolveNode(RailNode.Type type, org.bukkit.block.Block railBlock, String stationName) {
        String id = com.bigbrother.bilicraftticketsystem.route.NodeId.ofBlock(railBlock);
        RailNode existing = nodes.get(id);
        if (existing != null) {
            return existing;
        }
        RailNode node = new RailNode(type, railBlock, stationName);
        nodes.put(id, node);
        return node;
    }

    /**
     * 记录一条区间（按 {@code (from,to,lineId)} 生成的 edge id 去重）。
     * <p>
     * layer 初始置 0：所有单元写出几何后，由 {@code GeoTraversalTask#recomputeAllLayers} 读回
     * 全部 geojson <b>跨全部线路</b>统一重算（高架压平交必须在全集上算）。
     *
     * @param fromNodeId      起点节点 id
     * @param toNodeId        终点节点 id
     * @param lineId          区间所属线路 id（来自 bcswitcher 出向声明）
     * @param railwaySystemId 区间所属铁路系统 id
     * @param color           显示颜色
     * @param coords          轨道坐标（已简化）
     * @param length          区间沿轨道的真实长度（{@link TrackWalker} 按 RailPath 实际移动距离计）
     * @param departDirection 本段物理出向（离开起点道岔的方向；无道岔决策传 null）
     * @param world           区间所在世界名
     * @param enterFaceFrom   本段在起点道岔的到达面 key（起点首段 / 无门控传 null）
     * @param enterFaceTo     本段在终点节点的到达面 key（无门控传 null）
     * @param ownerLineId     产出本段的遍历单元 id（即本单元，决定写进哪个文件）
     */
    public void recordEdge(String fromNodeId, String toNodeId, String lineId,
                           String railwaySystemId, String color, List<LngLatAlt> coords, double length,
                           String departDirection, String world, String enterFaceFrom, String enterFaceTo,
                           String ownerLineId) {
        String edgeId = com.bigbrother.bilicraftticketsystem.route.NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        RailEdge existing = edges.get(edgeId);
        if (existing != null) {
            // 同一 (from,to,lineId) 边被不同到达面产出（如 [+train:lr] 多进入方向续行同一段）：
            // 合并到达面而非丢弃，保证门控集合完整。
            existing.addEnterFaceFrom(enterFaceFrom);
            return;
        }
        edges.put(edgeId, new RailEdge(fromNodeId, toNodeId, lineId, railwaySystemId, coords, color, length, 0,
                departDirection, world, enterFaceFrom, enterFaceTo, ownerLineId));
    }

    /**
     * 本单元走出的全部区间（保序）。
     *
     * @return 区间列表（副本）
     */
    public List<RailEdge> edges() {
        return new ArrayList<>(edges.values());
    }

    /**
     * 本单元走出的全部节点中、被 {@link #edges()} 引用到的那些（保序去重）。
     * <p>
     * 只写被引用的节点：孤立节点（如越界边停住后没有出边的落点之外、本单元仅"到达过"但没有任何
     * 区间连接的节点）写进 geojson 没有意义。
     *
     * @return 节点列表
     */
    public List<RailNode> referencedNodes() {
        List<RailNode> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RailEdge edge : edges.values()) {
            addNode(result, seen, edge.getFromNodeId());
            addNode(result, seen, edge.getToNodeId());
        }
        return result;
    }

    private void addNode(List<RailNode> result, Set<String> seen, String nodeId) {
        if (seen.add(nodeId)) {
            RailNode node = nodes.get(nodeId);
            if (node != null) {
                result.add(node);
            }
        }
    }

    /**
     * 本单元实际到达过的全部节点 id。
     * <p>
     * 供<b>越界边落点校验</b>：单元 L 走出一条通往 L' 的越界边后，其落点节点应当出现在 L' 自己
     * 单元的到达节点集中；不出现说明该出向声明的线路写错了、或 L' 的轨道没铺到该处。
     *
     * @return 到达过的节点 id 集合（副本）
     */
    public Set<String> nodeIds() {
        return new LinkedHashSet<>(nodes.keySet());
    }

    /**
     * 节点数（用于汇总提示）。
     *
     * @return 节点总数
     */
    public int totalNodes() {
        return nodes.size();
    }

    /**
     * 区间数（用于汇总提示）。
     *
     * @return 区间总数
     */
    public int totalEdges() {
        return edges.size();
    }

    /**
     * 总长度（用于汇总提示，单位 km）。
     *
     * @return 总长度
     */
    public double totalDistance() {
        double total = 0;
        for (RailEdge edge : edges.values()) {
            total += edge.getLength();
        }
        return total / 1000;
    }
}
