package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.geojson.LineString;
import org.geojson.Point;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 geojson 文件反向构建 {@link GeoRouteGraph}。
 * <p>
 * 读取目录下所有 {@code *.geojson}（每条线路一个 {@code <lineId>.geojson}），
 * Point feature 转 {@link GeoNode}（按 id 合并、累积 lineIds），LineString feature 转 {@link GeoLink}。
 * 节点跨文件共享同一 id。缺目录 / 空目录 / 单文件解析失败都不抛异常，仅记日志并跳过。
 * <p>
 * 解析逻辑可吃 {@link File} 或 {@link InputStream}，便于单元测试（不依赖 Bukkit）。
 */
public class GeoGraphLoader {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComponentLogger logger;

    /**
     * @param logger 日志（可为 null，为 null 时静默）
     */
    public GeoGraphLoader(ComponentLogger logger) {
        this.logger = logger;
    }

    /**
     * 读取目录下所有 *.geojson 构建路由图。
     *
     * @param dir geojson 目录
     * @return 构建出的图（目录不存在 / 无文件时为空图）
     */
    public GeoRouteGraph loadDir(File dir) {
        GeoRouteGraph graph = new GeoRouteGraph();
        if (dir == null || !dir.isDirectory()) {
            warn("geojson 目录不存在，路由图为空：" + dir);
            return graph;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".geojson"));
        if (files == null || files.length == 0) {
            warn("geojson 目录下没有 .geojson 文件，路由图为空：" + dir);
            return graph;
        }
        // 每个文件只解析一次；先加所有节点再加所有边：边加入时要把 lineId 累积到两端节点，需保证节点已就位
        List<FeatureCollection> parsed = new ArrayList<>();
        for (File file : files) {
            FeatureCollection fc = readFile(file);
            if (fc != null) {
                parsed.add(fc);
            }
        }
        for (FeatureCollection fc : parsed) {
            addNodes(graph, fc);
        }
        for (FeatureCollection fc : parsed) {
            addLinks(graph, fc);
        }
        info("路由图构建完成：" + graph.nodeCount() + " 节点，" + graph.linkCount() + " 边，来自 " + files.length + " 个文件");
        return graph;
    }

    /**
     * 从单个 FeatureCollection 构建图（仅供测试 / 单文件场景）。
     *
     * @param fc geojson FeatureCollection
     * @return 构建出的图
     */
    public GeoRouteGraph loadFeatureCollection(FeatureCollection fc) {
        GeoRouteGraph graph = new GeoRouteGraph();
        if (fc != null) {
            addNodes(graph, fc);
            addLinks(graph, fc);
        }
        return graph;
    }

    private FeatureCollection readFile(File file) {
        try {
            return mapper.readValue(file, FeatureCollection.class);
        } catch (IOException e) {
            warn("解析 geojson 文件失败（跳过）：" + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    private void addNodes(GeoRouteGraph graph, FeatureCollection fc) {
        for (Feature feature : fc.getFeatures()) {
            GeoJsonObject geometry = feature.getGeometry();
            if (!(geometry instanceof Point point)) {
                continue;
            }
            Map<String, Object> props = feature.getProperties();
            String id = str(props.get("id"));
            if (id == null) {
                continue;
            }
            String type = str(props.get("type"));
            String name = str(props.get("name"));
            // 坐标约定：经度=x、纬度=z、高度=y
            double x = point.getCoordinates().getLongitude();
            double z = point.getCoordinates().getLatitude();
            double y = point.getCoordinates().getAltitude();
            GeoNode node = new GeoNode(id, type, name, x, y, z);
            Object lineIds = props.get("lineIds");
            if (lineIds instanceof List) {
                for (Object lid : (List<?>) lineIds) {
                    node.addLineId(str(lid));
                }
            }
            graph.addNode(node);
        }
    }

    private void addLinks(GeoRouteGraph graph, FeatureCollection fc) {
        for (Feature feature : fc.getFeatures()) {
            if (!(feature.getGeometry() instanceof LineString)) {
                continue;
            }
            Map<String, Object> props = feature.getProperties();
            String id = str(props.get("id"));
            String from = str(props.get("from"));
            String to = str(props.get("to"));
            String lineId = str(props.get("lineId"));
            if (id == null || from == null || to == null) {
                continue;
            }
            double distance = num(props.get("length"));
            String color = str(props.get("color"));
            String departDir = str(props.get("departDir"));
            graph.addLink(new GeoLink(id, from, to, lineId, distance, color, departDir));
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double num(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                // 落到默认 0
            }
        }
        return 0;
    }

    private void info(String msg) {
        if (logger != null) {
            logger.info(Component.text(msg, NamedTextColor.DARK_AQUA));
        }
    }

    private void warn(String msg) {
        if (logger != null) {
            logger.warn(Component.text(msg, NamedTextColor.DARK_AQUA));
        }
    }
}
