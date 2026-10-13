package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.List;

/**
 * 把已落盘的 geojson 读回 {@link RailEdge}，供<b>跨全部线路重算 layer</b> 使用
 * （见 {@code GeoTraversalTask#recomputeAllLayers}）：新走出的高架 / 平交与既有线路的叠压关系
 * 必须在全集上判定，故要读回所有文件的区间几何。
 * <p>
 * 与 {@link com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader} 的区别：那个面向
 * 寻路图、只取寻路要用的字段；本类<b>无损</b>还原区间（坐标 / 颜色 / 长度 / layer / 出向 / 门控面 /
 * 世界 / 归属单元）。
 * <p>
 * 不解析节点，因而不依赖 Bukkit / 世界加载——按遍历单元分文件后，保存是整体覆盖（每个文件只有一个
 * 写者），不再需要读回旧文件做任何合并，节点还原也就没有了用途。
 */
public class GeojsonReader {

    /**
     * 只从已解析的 {@link FeatureCollection} 中提取全部区间（{@link RailEdge}），<b>不解析节点</b>，
     * 因而不依赖 Bukkit / 世界加载——供跨文件全局 layer 重算读取所有 geojson 的区间几何。
     *
     * @param fc 已解析的 FeatureCollection（可为 null）
     * @return 全部区间（保序）；无区间返回空列表
     */
    public List<RailEdge> readEdges(FeatureCollection fc) {
        List<RailEdge> edges = new ArrayList<>();
        if (fc == null || fc.getFeatures() == null) {
            return edges;
        }
        for (Feature f : fc.getFeatures()) {
            if (f.getGeometry() instanceof LineString ls) {
                RailEdge edge = toEdge(f, ls);
                if (edge != null) {
                    edges.add(edge);
                }
            }
        }
        return edges;
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
        String owner = str(f.getProperty("owner"));
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
                departDir, world, firstEnterFrom, enterTo, owner);
        restEnterFrom.forEach(edge::addEnterFaceFrom);
        edge.setTrack(str(f.getProperty("track")));
        return edge;
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
}