package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.geojson.*;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class GeojsonManager {
    @Getter
    private FeatureCollection collection;
    private final File geodataDir;

    /**
     * 创建一个空的geojson
     *
     * @param geodataDir geojson文件夹
     */
    public GeojsonManager(File geodataDir) {
        this.collection = new FeatureCollection();
        this.geodataDir = geodataDir;
    }

    /**
     * 加载一个geojson文件
     *
     * @param geodataDir geojson文件夹
     * @param fileName   文件名
     */
    public GeojsonManager(File geodataDir, String fileName) throws IOException {
        this.geodataDir = geodataDir;
        File file = new File(fileName);
        if (file.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            this.collection = mapper.readValue(file, FeatureCollection.class);
        }
    }

    /**
     * 增加一条线（LineString）
     */
    public void addLine(List<LngLatAlt> coodrinates, Map<String, Object> props) {
        LineString line = new LineString(coodrinates.toArray(new LngLatAlt[0]));
        Feature feature = new Feature();
        feature.setGeometry(line);
        if (props != null) {
            feature.setProperties(props);
        }
        collection.add(feature);
    }

    /**
     * 向FeatureCollection增加一个点
     *
     * @param coord 点坐标
     * @param props 点的自定义properties
     */
    public void addPoint(LngLatAlt coord, Map<String, Object> props) {
        Point point = new Point(coord);
        Feature feature = new Feature();
        feature.setGeometry(point);
        if (props != null) {
            feature.setProperties(props);
        }
        collection.add(feature);
    }

    /**
     * 保存为 GeoJSON 文件
     */
    public void saveGeojsonFile(String fileName) throws IOException {
        if (!geodataDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            geodataDir.mkdir();
        }
        File file = new File(geodataDir, fileName);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, collection);
        collection = new FeatureCollection();
    }

    /**
     * 获取存在的geojson对应的Node
     */
    public Set<MermaidGraph.Node> buildNodes() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Set<MermaidGraph.Node> nodes = new HashSet<>();

        File[] files = geodataDir.listFiles((file, name) -> name.toLowerCase().endsWith(".geojson"));
        if (files == null) {
            return nodes;
        }

        // ========== 获取所有存在的geojson节点 ==========
        for (File file : files) {
            FeatureCollection fc = mapper.readValue(file, FeatureCollection.class);
            if (fc.getFeatures().isEmpty()) {
                continue;
            }
            for (Feature feature : fc.getFeatures()) {
                if (feature.getGeometry() instanceof Point) {
                    Map<String, Object> props = feature.getProperties();
                    String platformTag = (String) props.getOrDefault("platform_tag", null);
                    if (platformTag == null) {
                        continue;
                    }
                    MermaidGraph.Node node = TrainRoutes.graph.getNodeFromPtag(platformTag);
                    if (node == null) {
                        continue;
                    }
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }
}
