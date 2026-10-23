package com.bigbrother.bilicraftticketsystem.route.geograph;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 边 id -> 边。供构图时按 id 判重（同一条边可能被多个遍历单元各产出一份，须合并而非重复加入）。
     */
    private final Map<String, GeoLink> linksById = new LinkedHashMap<>();

    /**
     * 站名级「直达可达」缩合距离矩阵的缓存：起点站名 -> 终点站名 -> 最短可达距离（km，下界估计）。
     * 首次 {@link #stationDirectDistances()} 时惰性构建，之后复用；图在 reload 时整体替换，故随图失效。
     * 仅供 {@link GeoRouteEngine#findTransferJourneys} 快速筛选候选换乘站，不用于最终票价/路线。
     */
    private volatile Map<String, Map<String, Double>> stationDistCache;
    /**
     * 进站道岔索引缓存（见 {@link #mainlineSwitchStations()}），加边时失效。
     */
    private volatile Map<String, String> mainlineSwitchCache;
    /**
     * 寻路整数化索引缓存（见 {@link #searchIndex()}），加节点 / 加边时失效。
     */
    private volatile GeoSearchIndex searchIndexCache;

    /**
     * 加入或合并一个节点。若该 id 已存在，累积其 lineIds（保留已有节点对象）。
     *
     * @param node 节点
     * @return 图中最终持有的该 id 节点（已存在则为旧对象）
     */
    @SuppressWarnings("UnusedReturnValue")
    public GeoNode addNode(GeoNode node) {
        searchIndexCache = null;
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
        mainlineSwitchCache = null;
        searchIndexCache = null;
        linksById.put(link.getId(), link);
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
     * 按 id 取边。供构图时判重：同一条边可能被多个遍历单元各产出一份（共用轨道 / 越界边），
     * 须按 id 合并到达面而不是重复加入邻接表。
     *
     * @param id 边 id
     * @return 边，不存在返回 null
     */
    public GeoLink getLink(String id) {
        return linksById.get(id);
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
     * 获取某个线路对应某车站的车站节点
     * 保证一个线路只经过某个特定车站一次，因此某个线路对应的某车站节点唯一
     *
     * @param stationName 车站名
     * @param lineId      线路名
     * @return 车站节点，没找到返回null
     */
    @Nullable
    public GeoNode getNode(String stationName, String lineId) {
        for (GeoNode stationNode : stationNodes(stationName)) {
            if (stationNode.getLineIds().contains(lineId)) {
                return stationNode;
            }
        }
        return null;
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
     * 按 geojson 的 {@code track} 标记判定（不做结构推断）：进站道岔上本线有一条正线出边
     * （{@code track=main}）和一条停靠线出边（{@code track=siding}），沿本线停靠线一路走到的第一个车站
     * 即为所求。停靠线中途可以经过别的道岔。
     *
     * @param node   待判断的节点（通常为进站道岔节点）
     * @param lineId 当前线路id
     * @return 停靠线 platform 车站名；不是本线进站道岔返回 null
     */
    public String platformNameOfMainlineSwitch(GeoNode node, String lineId) {
        if (node == null || node.isStation() || lineId == null) {
            return null;
        }
        return mainlineSwitchStations().get(mainlineSwitchKey(node.getId(), lineId));
    }

    /**
     * 进站道岔 → 停靠线车站名 的索引（key 见 {@link #mainlineSwitchKey}），惰性构建，加边时失效。
     */
    private Map<String, String> mainlineSwitchStations() {
        Map<String, String> cache = mainlineSwitchCache;
        if (cache != null) {
            return cache;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<GeoLink>> entry : adjacency.entrySet()) {
            for (GeoLink main : entry.getValue()) {
                if (!main.isMainline()) {
                    continue;
                }
                String station = sidingStationFrom(entry.getKey(), main.getLineId());
                if (station != null) {
                    result.put(mainlineSwitchKey(entry.getKey(), main.getLineId()), station);
                }
            }
        }
        mainlineSwitchCache = result;
        return result;
    }

    /**
     * 从进站道岔出发沿本线停靠线（{@code track=siding}）出边前进，返回遇到的第一个车站名。
     *
     * @param switchNodeId 进站道岔节点 id
     * @param lineId       线路 id
     * @return 车站名；找不到返回 null
     */
    private String sidingStationFrom(String switchNodeId, String lineId) {
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(switchNodeId);
        seen.add(switchNodeId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (GeoLink link : links(cur)) {
                if (!link.isSiding() || !lineId.equals(link.getLineId())) {
                    continue;
                }
                GeoNode to = nodes.get(link.getToNodeId());
                if (to == null) {
                    continue;
                }
                if (to.isStation()) {
                    if (to.getName() != null) {
                        return to.getName();
                    }
                } else if (seen.add(to.getId())) {
                    queue.add(to.getId());
                }
            }
        }
        return null;
    }

    private static String mainlineSwitchKey(String nodeId, String lineId) {
        return nodeId + "|" + lineId;
    }

    /**
     * 寻路热路径用的整数化索引（{@link GeoSearchIndex}）。图构建完成后首次寻路时惰性构建一次并缓存，
     * 之后多个查询线程共享只读使用；图在 reload 时整体替换，缓存随之失效。
     *
     * @return 寻路索引
     */
    GeoSearchIndex searchIndex() {
        GeoSearchIndex cache = searchIndexCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (searchIndexCache == null) {
                searchIndexCache = new GeoSearchIndex(this);
            }
            return searchIndexCache;
        }
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
     * 站名级「直达可达」缩合距离矩阵：起点站名 → 终点站名 → 一趟直达车的最短距离（km）。
     * <p>
     * 把每个物理节点（含大量道岔/多站台，真实图 ~639 个）缩合为站名节点（真实图 ~78 个），只保留
     * 「站名 A 能否一趟直达站名 B、最短多少」这一信息，供 {@link GeoRouteEngine#findTransferJourneys}
     * 快速枚举全部换乘站而无需对每个候选跑全图寻路。
     * <p>
     * 口径为<b>下界估计</b>：对每个站名的各站台节点跑一次普通 Dijkstra（边权 = {@link GeoLink#getDistance()}，
     * 忽略 enterFace 门控与折返/停靠线中途站约束），取到各目标站名的最短距离。它只用于<b>筛选</b>候选换乘站，
     * 最终每段行程仍由 {@link GeoRouteEngine#findByStation} 权威实体化并施加全部约束，故下界的乐观性不影响
     * 结果正确性（至多让个别不可行候选进入实体化阶段被自然淘汰）。
     * <p>
     * 惰性构建 + 缓存；图在 reload 时整体替换（新建 {@code GeoRouteGraph}），缓存随之失效。
     *
     * @return 站名 → (站名 → 最短直达距离 km) 的只读矩阵
     */
    public Map<String, Map<String, Double>> stationDirectDistances() {
        Map<String, Map<String, Double>> cache = stationDistCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (stationDistCache != null) {
                return stationDistCache;
            }
            Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();
            for (String startName : stationIndex.keySet()) {
                Map<String, Double> row = new LinkedHashMap<>();
                for (GeoNode platform : stationIndex.get(startName)) {
                    accumulateShortestToStations(platform.getId(), row);
                }
                row.remove(startName); // 起点到自身不算直达候选
                matrix.put(startName, row);
            }
            stationDistCache = matrix;
            return matrix;
        }
    }

    /**
     * 从单一起点节点做普通 Dijkstra（边权 = 段长，米），把到达各<b>站名</b>的最短距离（km）并入 {@code out}
     * （多站台/多路径取更短者）。缩合距离矩阵构建的内层步骤。
     *
     * @param startNodeId 起点节点 id
     * @param out         站名 → 最短距离（km）累加表（原地更新，取更小值）
     */
    private void accumulateShortestToStations(String startNodeId, Map<String, Double> out) {
        Map<String, Double> dist = new java.util.HashMap<>();
        dist.put(startNodeId, 0.0);
        java.util.PriorityQueue<Map.Entry<String, Double>> queue = new java.util.PriorityQueue<>(
                Map.Entry.comparingByValue());
        queue.add(Map.entry(startNodeId, 0.0));
        while (!queue.isEmpty()) {
            Map.Entry<String, Double> cur = queue.poll();
            String nodeId = cur.getKey();
            double d = cur.getValue();
            Double known = dist.get(nodeId);
            if (known != null && d > known) {
                continue; // 过期堆条目
            }
            GeoNode node = nodes.get(nodeId);
            if (node != null && node.isStation() && node.getName() != null && d > 0) {
                double km = d / 1000;
                out.merge(node.getName(), km, Math::min);
            }
            for (GeoLink link : links(nodeId)) {
                double nd = d + link.getDistance();
                Double old = dist.get(link.getToNodeId());
                if (old == null || nd < old) {
                    dist.put(link.getToNodeId(), nd);
                    queue.add(Map.entry(link.getToNodeId(), nd));
                }
            }
        }
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
