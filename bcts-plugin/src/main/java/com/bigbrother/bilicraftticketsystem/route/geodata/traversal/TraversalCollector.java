package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遍历结果收集器：按输出文件分组累积节点与区间。
 * <p>
 * 分组规则：每条线路一个文件，文件键 = lineId。该线遍历经过的所有区间都归入这一组。
 * 共用轨道（多条线路共线）会在各自线路的文件里各保存一份完整副本（见 {@link GraphWalk} 的
 * 按线遍历 + 去重）。
 * <p>
 * 节点（platform / bcswitcher）以全局 id 去重共享（累积经过的 lineId），写某个文件时带上该文件
 * 区间引用到的节点。
 */
public class TraversalCollector {
    /**
     * 全局节点注册表：节点 id -> 节点（跨文件共享，累积经过的 lineId）。
     */
    private final Map<String, RailNode> nodes = new LinkedHashMap<>();
    /**
     * 文件键（lineId）-> （区间 id -> 区间）。
     */
    private final Map<String, Map<String, RailEdge>> edgeGroups = new LinkedHashMap<>();

    /**
     * 取得（或新建）一个节点。已存在则复用，保证同物理位置去重。
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
     * 记录一条区间到指定文件分组（按 from/to/lineId 去重）。
     * <p>
     * layer 初始置 0，待所有区间收集完毕后由 {@link #assignLayers()} 按空间交叉关系统一重算。
     *
     * @param fileKey         输出文件键（lineId）
     * @param fromNodeId      起点节点 id
     * @param toNodeId        终点节点 id
     * @param lineId          区间所属线路 id
     * @param railwaySystemId 区间所属铁路系统 id
     * @param color           显示颜色
     * @param coords          轨道坐标（已简化）
     * @param length          区间沿轨道的真实长度（{@link TrackWalker} 按 RailPath 实际移动距离计）
     * @param departDirection 本段物理出向（离开起点道岔的方向；无道岔决策传 null）
     * @param world           区间所在世界名
     */
    public void recordEdge(String fileKey, String fromNodeId, String toNodeId, String lineId,
                           String railwaySystemId, String color, List<LngLatAlt> coords, double length,
                           String departDirection, String world) {
        Map<String, RailEdge> group = edgeGroups.computeIfAbsent(fileKey, k -> new LinkedHashMap<>());
        String edgeId = com.bigbrother.bilicraftticketsystem.route.NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        if (group.containsKey(edgeId)) {
            return;
        }
        group.put(edgeId, new RailEdge(fromNodeId, toNodeId, lineId, railwaySystemId, coords, color, length, 0,
                departDirection, world));
    }

    /**
     * 所有区间收集完毕后，按空间交叉关系统一计算各区间 layer，写回每条 {@link RailEdge}。
     * <p>
     * 跨文件全局计算：前端会叠加显示所有 geojson，故共线 / 交叉关系必须在全集上一致。
     * 详见 {@link LayerAssigner}。
     */
    public void assignLayers() {
        List<RailEdge> all = new ArrayList<>();
        for (Map<String, RailEdge> g : edgeGroups.values()) {
            all.addAll(g.values());
        }
        LayerAssigner.assign(all);
    }

    /**
     * 所有文件键。
     *
     * @return 文件键集合
     */
    public java.util.Set<String> fileKeys() {
        return edgeGroups.keySet();
    }

    /**
     * 取某文件分组的所有区间。
     *
     * @param fileKey 文件键
     * @return 区间列表
     */
    public List<RailEdge> edgesOf(String fileKey) {
        Map<String, RailEdge> group = edgeGroups.get(fileKey);
        return group == null ? new ArrayList<>() : new ArrayList<>(group.values());
    }

    /**
     * 取某文件分组区间引用到的所有节点。
     *
     * @param fileKey 文件键
     * @return 节点列表
     */
    public List<RailNode> nodesOf(String fileKey) {
        List<RailNode> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (RailEdge edge : edgesOf(fileKey)) {
            addNode(result, seen, edge.getFromNodeId());
            addNode(result, seen, edge.getToNodeId());
        }
        return result;
    }

    private void addNode(List<RailNode> result, java.util.Set<String> seen, String nodeId) {
        if (seen.add(nodeId)) {
            RailNode node = nodes.get(nodeId);
            if (node != null) {
                result.add(node);
            }
        }
    }

    /**
     * 全部节点数（用于汇总提示）。
     *
     * @return 节点总数
     */
    public int totalNodes() {
        return nodes.size();
    }

    /**
     * 全部区间数（用于汇总提示）。
     *
     * @return 区间总数
     */
    public int totalEdges() {
        int sum = 0;
        for (Map<String, RailEdge> g : edgeGroups.values()) {
            sum += g.size();
        }
        return sum;
    }

    /**
     * 总长度（用于汇总提示，单位km）。
     *
     * @return 总长度
     */
    public double totalDistance() {
        double total = 0;
        for (Map<String, RailEdge> g : edgeGroups.values()) {
            total += g.values().stream().mapToDouble(RailEdge::getLength).sum();
        }
        return total / 1000;
    }
}
