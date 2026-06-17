package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把遍历得到的节点 / 区间转换为 geojson FeatureCollection。
 * <p>
 * 产出符合 CLAUDE.md 规定的属性：
 * <ul>
 *   <li>Point：id（必须）、name（仅 station）、lineIds、railwaySystemIds（仅 station）、prev、next、type。</li>
 *   <li>LineString：id、from、to、lineId、railwaySystemId（未配置则省略）、color、length、layer。</li>
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
        Map<String, List<String>> prevMap = new LinkedHashMap<>();
        Map<String, List<String>> nextMap = new LinkedHashMap<>();
        for (RailEdge edge : edges) {
            nextMap.computeIfAbsent(edge.getFromNodeId(), k -> new ArrayList<>()).add(edge.getToNodeId());
            prevMap.computeIfAbsent(edge.getToNodeId(), k -> new ArrayList<>()).add(edge.getFromNodeId());
        }

        for (RailNode node : nodes) {
            collection.add(buildPointFeature(node, prevMap, nextMap));
        }
        for (RailEdge edge : edges) {
            collection.add(buildLineFeature(edge));
        }
        return collection;
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
        feature.setProperties(props);
        return feature;
    }

    /**
     * 把节点所在方块转换为 geojson 坐标（经度=x, 纬度=z, 高度=y）。
     *
     * @param node 节点
     * @return geojson 坐标
     */
    private LngLatAlt blockToCoord(RailNode node) {
        return new LngLatAlt(
                node.getRailBlock().getX(),
                node.getRailBlock().getZ(),
                node.getRailBlock().getY()
        );
    }
}
