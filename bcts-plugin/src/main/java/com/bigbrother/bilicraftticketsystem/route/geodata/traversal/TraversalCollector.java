package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import lombok.Getter;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遍历结果收集器：按输出文件分组累积节点与区间。
 * <p>
 * 分组规则（见 Phase 3 重做方案）：
 * <ul>
 *   <li>每条营运线路一个文件，文件键 = lineId。该线主线区间、以及沿途 default 正线子遍历的
 *       区间都归入这一组。</li>
 *   <li>联络线（contact）单独一个文件，文件键 = "contact"。</li>
 * </ul>
 * 节点（platform / bcswitcher）以全局 id 去重共享，写某个文件时带上该文件区间引用到的节点。
 */
public class TraversalCollector {
    /**
     * 全局节点注册表：节点 id -> 节点（跨文件共享，累积经过的 lineId）。
     */
    private final Map<String, RailNode> nodes = new LinkedHashMap<>();
    /**
     * 文件键 -> （区间 id -> 区间）。
     */
    private final Map<String, Map<String, RailEdge>> edgeGroups = new LinkedHashMap<>();
    /**
     * 待遍历的联络线种子（遍历主线时遇到声明 contact 分支的 bcswitcher 时收集）。
     */
    @Getter
    private final List<ContactSeed> contactSeeds = new ArrayList<>();
    /**
     * 已收集联络线种子的来源道岔节点 id（去重，避免多条主线经过同一道岔时重复遍历联络线）。
     */
    private final java.util.Set<String> contactSeedSwitchers = new java.util.HashSet<>();

    /**
     * 一条待遍历联络线的起点：来源道岔的铁轨方块 + 进入方向（带 contact tag 的矿车从此出发，
     * 经该道岔时被导向 contact 分支），以及来源道岔的节点 id（作为联络线第一段区间的起点，
     * 使停止前的联络线内容能正确成段保存）。
     */
    @Getter
    public static class ContactSeed {
        private final String sourceNodeId;
        private final org.bukkit.block.Block startRail;
        private final org.bukkit.util.Vector startDirection;

        public ContactSeed(String sourceNodeId, org.bukkit.block.Block startRail, org.bukkit.util.Vector startDirection) {
            this.sourceNodeId = sourceNodeId;
            this.startRail = startRail;
            this.startDirection = startDirection;
        }

    }

    /**
     * 记录一个联络线种子（按来源道岔节点 id 去重）。
     *
     * @param switcherNodeId 来源道岔节点 id
     * @param startRail      道岔铁轨方块
     * @param startDirection 进入方向
     */
    public void addContactSeed(String switcherNodeId, org.bukkit.block.Block startRail,
                               org.bukkit.util.Vector startDirection) {
        if (contactSeedSwitchers.add(switcherNodeId)) {
            contactSeeds.add(new ContactSeed(switcherNodeId, startRail, startDirection));
        }
    }

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
     * 记录一条区间到指定文件分组（按 from/to/lineId 去重，叠层 layer 按已有同物理区间条数递增）。
     *
     * @param fileKey    输出文件键（lineId 或 "contact"）
     * @param fromNodeId 起点节点 id
     * @param toNodeId   终点节点 id
     * @param lineId     区间所属线路 id
     * @param color      显示颜色
     * @param coords     轨道坐标（已简化）
     * @param length     区间沿轨道的真实长度（{@link TrackWalker} 按 RailPath 实际移动距离计）
     */
    public void recordEdge(String fileKey, String fromNodeId, String toNodeId, String lineId,
                           String color, List<LngLatAlt> coords, double length) {
        Map<String, RailEdge> group = edgeGroups.computeIfAbsent(fileKey, k -> new LinkedHashMap<>());
        String edgeId = com.bigbrother.bilicraftticketsystem.route.NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        if (group.containsKey(edgeId)) {
            return;
        }
        // 叠层：统计全局已有的同 from->to 物理区间数
        int layer = 0;
        for (Map<String, RailEdge> g : edgeGroups.values()) {
            for (RailEdge e : g.values()) {
                if (e.getFromNodeId().equals(fromNodeId) && e.getToNodeId().equals(toNodeId)) {
                    layer++;
                }
            }
        }
        group.put(edgeId, new RailEdge(fromNodeId, toNodeId, lineId, coords, color, length, layer));
    }

    /**
     * 是否已存在某文件分组的某区间（供调用方避免重复子遍历）。
     *
     * @param fileKey    文件键
     * @param fromNodeId 起点节点 id
     * @param toNodeId   终点节点 id
     * @param lineId     线路 id
     * @return true 表示已记录
     */
    public boolean hasEdge(String fileKey, String fromNodeId, String toNodeId, String lineId) {
        Map<String, RailEdge> group = edgeGroups.get(fileKey);
        if (group == null) {
            return false;
        }
        return group.containsKey(com.bigbrother.bilicraftticketsystem.route.NodeId.ofEdge(fromNodeId, toNodeId, lineId));
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
}
