package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已落盘的 {@code <lineId>.geojson} 读回 {@link RailNode} / {@link RailEdge}，供单线增量遍历
 * （{@code /railgeo walk}）复用既有产物：合并 {@code contact.geojson} 时保留其它线的联络线段，
 * 计算 layer 时把其它文件的区间当作固定障碍。
 * <p>
 * 与 {@link com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader} 的区别：那个面向
 * 寻路图、只取寻路要用的字段；本类要<b>无损</b>还原区间（坐标 / 颜色 / 长度 / layer / 出向 / 门控面 /
 * 世界）以便原样重写 geojson。节点的 {@link Block} 由节点 id 编码的世界名 + 坐标还原（须主线程，
 * 世界须已加载）。
 */
public class GeojsonReader {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 读回一个 geojson 文件的所有节点与区间。文件不存在 / 解析失败返回空结果。
     *
     * @param file geojson 文件
     * @return 解析结果（节点 + 区间）
     */
    public Result read(File file) {
        Result result = new Result();
        if (file == null || !file.isFile()) {
            return result;
        }
        FeatureCollection fc;
        try {
            fc = mapper.readValue(file, FeatureCollection.class);
        } catch (Exception e) {
            return result;
        }
        if (fc.getFeatures() == null) {
            return result;
        }
        for (Feature f : fc.getFeatures()) {
            if (f.getGeometry() instanceof Point) {
                RailNode node = toNode(f);
                if (node != null) {
                    result.nodes.put(node.getId(), node);
                }
            } else if (f.getGeometry() instanceof LineString ls) {
                RailEdge edge = toEdge(f, ls);
                if (edge != null) {
                    result.edges.add(edge);
                }
            }
        }
        return result;
    }

    /**
     * Point feature -> {@link RailNode}。节点 {@link Block} 由 id 编码的世界名 + 坐标还原
     * （须主线程且世界已加载，否则该节点跳过）。
     *
     * @param f Point feature
     * @return 节点；id 缺失 / 世界未加载返回 null
     */
    private RailNode toNode(Feature f) {
        String id = str(f.getProperty("id"));
        if (id == null) {
            return null;
        }
        com.bigbrother.bilicraftticketsystem.route.NodeId.Coords coords =
                com.bigbrother.bilicraftticketsystem.route.NodeId.parseCoords(id);
        if (coords == null) {
            return null;
        }
        World world = Bukkit.getWorld(coords.world());
        if (world == null) {
            return null;
        }
        Block block = world.getBlockAt(coords.x(), coords.y(), coords.z());
        String typeStr = str(f.getProperty("type"));
        RailNode.Type type = "station".equalsIgnoreCase(typeStr) ? RailNode.Type.STATION : RailNode.Type.SWITCH;
        String name = str(f.getProperty("name"));
        RailNode node = new RailNode(type, block, name);
        addAll(node::addLineId, f.getProperty("lineIds"));
        addAll(node::addRailwaySystemId, f.getProperty("railwaySystemIds"));
        return node;
    }

    /**
     * LineString feature -> {@link RailEdge}，无损还原全部区间属性。
     *
     * @param f  LineString feature
     * @param ls 几何
     * @return 区间；from/to 缺失返回 null
     */
    private RailEdge toEdge(Feature f, LineString ls) {
        String from = str(f.getProperty("from"));
        String to = str(f.getProperty("to"));
        if (from == null || to == null) {
            return null;
        }
        String lineId = str(f.getProperty("lineId"));
        String railwaySystemId = str(f.getProperty("railwaySystemId"));
        String color = str(f.getProperty("color"));
        double length = num(f.getProperty("length"));
        int layer = (int) num(f.getProperty("layer"));
        String departDir = str(f.getProperty("departDir"));
        String world = str(f.getProperty("world"));
        String enterTo = str(f.getProperty("enterTo"));
        List<LngLatAlt> coords = ls.getCoordinates() == null ? new ArrayList<>() : new ArrayList<>(ls.getCoordinates());
        // 门控入向面可能是多值：用首个初始化，其余合并进去
        Object enterFromProp = f.getProperty("enterFrom");
        String firstEnterFrom = null;
        List<String> restEnterFrom = new ArrayList<>();
        if (enterFromProp instanceof List<?> list) {
            for (Object o : list) {
                String s = str(o);
                if (s == null || s.isEmpty()) {
                    continue;
                }
                if (firstEnterFrom == null) {
                    firstEnterFrom = s;
                } else {
                    restEnterFrom.add(s);
                }
            }
        }
        RailEdge edge = new RailEdge(from, to, lineId, railwaySystemId, coords, color, length, layer,
                departDir, world, firstEnterFrom, enterTo);
        restEnterFrom.forEach(edge::addEnterFaceFrom);
        return edge;
    }

    private static void addAll(java.util.function.Consumer<String> sink, Object prop) {
        if (prop instanceof List<?> list) {
            for (Object o : list) {
                sink.accept(str(o));
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                // 落到 0
            }
        }
        return 0;
    }

    /**
     * 单文件解析结果：按 id 去重的节点表 + 区间列表。
     */
    public static class Result {
        /**
         * 节点 id -> 节点。
         */
        public final Map<String, RailNode> nodes = new LinkedHashMap<>();
        /**
         * 全部区间（保序）。
         */
        public final List<RailEdge> edges = new ArrayList<>();
    }
}