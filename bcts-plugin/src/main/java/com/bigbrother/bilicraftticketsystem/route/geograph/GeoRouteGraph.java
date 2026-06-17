package com.bigbrother.bilicraftticketsystem.route.geograph;

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
     * 取「有正线的进站道岔」对应的停靠线 platform 车站名。
     * <p>
     * 结构判定（不再依赖 {@code default} 特殊 lineId）：进站道岔是一个 bcswitcher，其一条出边通往
     * <b>道岔</b>节点（正线，跨站全速通过）、另一条出边通往<b>车站</b>节点（停靠线 platform）。
     * <p>
     * 仅当传入节点<b>同时</b>有通往道岔与通往车站的出边时，返回那条通往车站的停靠线车站名；
     * 否则（不是道岔、无正线绕行、或找不到停靠线车站）返回 null。
     *
     * @param node 待判断的节点（通常为进站道岔节点）
     * @return 停靠线 platform 车站名；不满足条件返回 null
     */
    public String platformNameOfMainlineSwitch(GeoNode node) {
        if (node == null) {
            return null;
        }
        List<GeoLink> outLinks = links(node.getId());
        boolean toSwitch = false;
        String platformName = null;
        for (GeoLink out : outLinks) {
            GeoNode to = nodes.get(out.getToNodeId());
            if (to == null) {
                continue;
            }
            if (to.isStation()) {
                if (to.getName() != null) {
                    platformName = to.getName();
                }
            } else {
                toSwitch = true;
            }
        }
        // 须同时有「通往道岔的正线出边」和「通往车站的停靠线出边」，才算有正线的进站道岔
        return toSwitch ? platformName : null;
    }

    /**
     * 取「有正线的进站道岔」的<b>到发线物理出向</b>（e/s/w/n 或 f/b/l/r），供无导航列车（普通车 /
     * 手动车）在此一律走到发线进站停靠。
     *
     * @param node 待判断的节点（通常为进站道岔节点）
     * @return 到发线物理出向；不满足条件 / 无出向记录返回 null
     */
    public String sidingDirectionOfMainlineSwitch(GeoNode node) {
        if (node == null) {
            return null;
        }
        String sidingDir = null;
        for (GeoLink out : links(node.getId())) {
            GeoNode to = nodes.get(out.getToNodeId());
            if (to == null) {
                continue;
            }
            if (to.isStation()) {
                if (out.getDepartDirection() != null) {
                    return out.getDepartDirection();
                }
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
