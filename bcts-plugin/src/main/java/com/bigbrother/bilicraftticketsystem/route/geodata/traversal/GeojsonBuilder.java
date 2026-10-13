package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把遍历得到的节点 / 区间转换为 geojson FeatureCollection。
 * <p>
 * 产出符合 CLAUDE.md 规定的属性：
 * <ul>
 *   <li>Point：id（必须）、name（仅 station）、lineIds、railwaySystemIds（仅 station）、prev、next、type。</li>
 *   <li>LineString：id、from、to、lineId、railwaySystemId（未配置则省略）、color、length、layer、
 *       track（正线 {@code main} / 停靠线 {@code siding}，普通段省略）。</li>
 * </ul>
 * prev / next 由所有区间的 from -> to 关系推算。该类为纯转换逻辑，不依赖 Bukkit / TC，
 * 便于单元测试。
 */
public class GeojsonBuilder {

    /**
     * 把节点集合与区间集合转换为 FeatureCollection。
     *
     * @param nodes 所有节点（按 id 去重后传入）
     * @param edges 所有区间
     * @return geojson FeatureCollection
     */
    public FeatureCollection build(Collection<RailNode> nodes, Collection<RailEdge> edges) {
        FeatureCollection collection = new FeatureCollection();

        // 先根据区间推算每个节点的 prev / next
        Map<String, List<String>> prevMap = adjacency(edges, false);
        Map<String, List<String>> nextMap = adjacency(edges, true);

        for (RailNode node : nodes) {
            collection.add(buildPointFeature(node, prevMap, nextMap));
        }
        for (RailEdge edge : edges) {
            collection.add(buildLineFeature(edge));
        }
        return collection;
    }

    /**
     * 由区间推算节点的相邻关系（prev 或 next），按节点 id 去重、保持首次出现顺序。
     * <p>
     * 同一对节点之间可能有多条不同 lineId 的边（联络线与营运线并行 / 共线段），prev / next
     * 表达的是「相邻节点」，同一个节点只应出现一次。
     *
     * @param edges   全部区间
     * @param forward true 求 next（from -> to），false 求 prev（to -> from）
     * @return 节点 id -> 相邻节点 id 列表（无重复）
     */
    public Map<String, List<String>> adjacency(Collection<RailEdge> edges, boolean forward) {
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        for (RailEdge edge : edges) {
            String key = forward ? edge.getFromNodeId() : edge.getToNodeId();
            String other = forward ? edge.getToNodeId() : edge.getFromNodeId();
            sets.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(other);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        sets.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }

    /**
     * 构建一个节点的 Point feature。
     *
     * @param node    节点
     * @param prevMap 节点 id -> 前置节点 id 列表
     * @param nextMap 节点 id -> 后置节点 id 列表
     * @return Point feature
     */
    private Feature buildPointFeature(RailNode node,
                                      Map<String, List<String>> prevMap,
                                      Map<String, List<String>> nextMap) {
        Feature feature = new Feature();
        feature.setGeometry(new Point(blockToCoord(node)));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", node.getId());
        props.put("type", node.getType().getValue());
        props.put("world", node.getRailBlock().getWorld().getName());
        if (node.isStation() && node.getStationName() != null) {
            props.put("name", node.getStationName());
        }
        props.put("lineIds", new ArrayList<>(node.getLineIds()));
        if (node.isStation()) {
            // 车站节点附带铁路系统 id 数组（可能被多系统线路经过）
            props.put("railwaySystemIds", new ArrayList<>(node.getRailwaySystemIds()));
        }
        props.put("prev", prevMap.getOrDefault(node.getId(), new ArrayList<>()));
        props.put("next", nextMap.getOrDefault(node.getId(), new ArrayList<>()));
        feature.setProperties(props);
        return feature;
    }

    /**
     * 构建一条区间的 LineString feature。
     *
     * @param edge 区间
     * @return LineString feature
     */
    private Feature buildLineFeature(RailEdge edge) {
        Feature feature = new Feature();
        feature.setGeometry(new LineString(edge.getCoordinates().toArray(new LngLatAlt[0])));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", edge.getId());
        props.put("from", edge.getFromNodeId());
        props.put("to", edge.getToNodeId());
        props.put("lineId", edge.getLineId());
        props.put("world", edge.getWorld());
        // 区间所属铁路系统 id；未配置时为 null，此时省略该属性
        if (edge.getRailwaySystemId() != null) {
            props.put("railwaySystemId", edge.getRailwaySystemId());
        }
        props.put("color", edge.getColor());
        props.put("length", edge.getLength());
        props.put("layer", edge.getLayer());
        // 物理出向（离开起点道岔的方向）；无道岔决策时为 null，省略该属性
        if (edge.getDepartDirection() != null) {
            props.put("departDir", edge.getDepartDirection());
        }
        // 入向面门控：到达起点道岔的允许到达面集合 + 到达终点节点的到达面。空 / null 省略（向后兼容）。
        if (!edge.getEnterFacesFrom().isEmpty()) {
            props.put("enterFrom", new ArrayList<>(edge.getEnterFacesFrom()));
        }
        if (edge.getEnterFaceTo() != null) {
            props.put("enterTo", edge.getEnterFaceTo());
        }
        // 产出本段的遍历单元（= 本段所在的文件名）。普通线段与 lineId 相同；联络线段 / 越界边 /
        // 转线段则是带出它的那条营运线。为空省略（读回旧文件时可能缺失）。
        if (edge.getOwnerLineId() != null) {
            props.put("owner", edge.getOwnerLineId());
        }
        // 正线 / 停靠线标记；普通段省略
        if (edge.getTrack() != null) {
            props.put("track", edge.getTrack());
        }
        feature.setProperties(props);
        return feature;
    }

    /**
     * 把节点真实位置（云轨为云轨节点位置，普通铁轨为方块整数坐标）转换为 geojson 坐标（经度=x, 纬度=z, 高度=y）。
     *
     * @param node 节点
     * @return geojson 坐标
     */
    private LngLatAlt blockToCoord(RailNode node) {
        return new LngLatAlt(
                node.getPosition().getX(),
                node.getPosition().getZ(),
                node.getPosition().getY()
        );
    }
}
