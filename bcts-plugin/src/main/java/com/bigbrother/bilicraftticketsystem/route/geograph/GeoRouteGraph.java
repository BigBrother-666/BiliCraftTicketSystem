package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由 geojson 反向构建的路由图。
 * <p>
 * 持有所有节点（按 id 去重）、按车站名的索引、以及有向邻接表。寻路引擎
 * {@link GeoRouteEngine} 基于本图做最短路计算。纯数据 + 查询，不依赖 Bukkit。
 */
public class GeoRouteGraph {
    /**
     * 节点 id -> 节点（跨文件共享，同 id 合并）。
     */
    private final Map<String, GeoNode> nodes = new LinkedHashMap<>();
    /**
     * 车站名 -> 该名下所有 station 节点（一个车站可能有多个站台节点）。
     */
    private final Map<String, List<GeoNode>> stationIndex = new LinkedHashMap<>();
    /**
     * 节点 id -> 从该节点出发的有向边列表。
     */
    private final Map<String, List<GeoLink>> adjacency = new LinkedHashMap<>();

    /**
     * 加入或合并一个节点。若该 id 已存在，累积其 lineIds（保留已有节点对象）。
     *
     * @param node 节点
     * @return 图中最终持有的该 id 节点（已存在则为旧对象）
     */
    public GeoNode addNode(GeoNode node) {
        GeoNode existing = nodes.get(node.getId());
        if (existing != null) {
            existing.getLineIds().addAll(node.getLineIds());
            return existing;
        }
        nodes.put(node.getId(), node);
        if (node.isStation() && node.getName() != null) {
            stationIndex.computeIfAbsent(node.getName(), k -> new ArrayList<>()).add(node);
        }
        return node;
    }

    /**
     * 加入一条有向边（同时把两端节点的 lineId 累积上）。
     *
     * @param link 边
     */
    public void addLink(GeoLink link) {
        adjacency.computeIfAbsent(link.getFromNodeId(), k -> new ArrayList<>()).add(link);
        GeoNode from = nodes.get(link.getFromNodeId());
        if (from != null) {
            from.addLineId(link.getLineId());
        }
        GeoNode to = nodes.get(link.getToNodeId());
        if (to != null) {
            to.addLineId(link.getLineId());
        }
    }

    /**
     * 按 id 取节点。
     *
     * @param id 节点 id
     * @return 节点，不存在返回 null
     */
    public GeoNode getNode(String id) {
        return nodes.get(id);
    }

    /**
     * 取某车站名下的所有 station 节点。
     *
     * @param stationName 车站名
     * @return 节点列表（不存在返回空列表）
     */
    public List<GeoNode> stationNodes(String stationName) {
        return stationIndex.getOrDefault(stationName, Collections.emptyList());
    }

    /**
     * 取从某节点出发的出边。
     *
     * @param nodeId 节点 id
     * @return 出边列表（无则空列表）
     */
    public List<GeoLink> links(String nodeId) {
        return adjacency.getOrDefault(nodeId, Collections.emptyList());
    }

    /**
     * 取「含正线(default)分支的进站道岔」对应的停靠线 platform 车站名。
     * <p>
     * 对应「有正线车站」的结构：进站道岔是一个 bcswitcher，其 default 分支指向正线（跨站全速通过）、
     * 营运线 id 分支指向停靠线，停靠线上有 platform 车站节点。
     * <p>
     * 仅当传入节点<b>含 default 出边</b>（即此处确有正线）时，返回其出边中通往 station 节点的
     * 停靠线车站名；否则（不是道岔、无正线、或找不到停靠线车站）返回 null。
     *
     * @param node 待判断的节点（通常为进站道岔节点）
     * @return 停靠线 platform 车站名；不满足条件返回 null
     */
    public String platformNameOfMainlineSwitch(GeoNode node) {
        if (node == null) {
            return null;
        }
        List<GeoLink> outLinks = links(node.getId());
        // 须含正线(default)分支，才算「有正线车站」的进站道岔
        boolean hasDefault = false;
        for (GeoLink out : outLinks) {
            if (LineInfo.DEFAULT_ID.equals(out.getLineId())) {
                hasDefault = true;
                break;
            }
        }
        if (!hasDefault) {
            return null;
        }
        // 取出边中通往车站（停靠线 platform）的那条，返回其车站名
        for (GeoLink out : outLinks) {
            if (LineInfo.DEFAULT_ID.equals(out.getLineId())) {
                continue;
            }
            GeoNode to = nodes.get(out.getToNodeId());
            if (to != null && to.isStation() && to.getName() != null) {
                return to.getName();
            }
        }
        return null;
    }

    /**
     * 所有节点。
     *
     * @return 节点集合
     */
    public Collection<GeoNode> allNodes() {
        return nodes.values();
    }

    /**
     * 所有车站名。
     *
     * @return 车站名集合
     */
    public Collection<String> allStationNames() {
        return stationIndex.keySet();
    }

    /**
     * 节点总数。
     *
     * @return 节点数
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * 边总数。
     *
     * @return 边数
     */
    public int linkCount() {
        int sum = 0;
        for (List<GeoLink> list : adjacency.values()) {
            sum += list.size();
        }
        return sum;
    }
}
