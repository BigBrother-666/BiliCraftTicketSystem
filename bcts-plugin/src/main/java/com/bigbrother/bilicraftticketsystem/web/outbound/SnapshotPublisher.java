package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.geojson.Feature;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 geojson 路由图 / 铁路系统 / 线路快照推送给后端（见 docs/PLUGIN_ADDENDUM.md §四）。
 * <p>
 * geojson 采用<b>合并推送</b>：读取 geodataDir 下所有 {@code *.geojson}，按节点 id 去重合并成单个
 * FeatureCollection。系统快照只推公开字段（{@link RailwaySystemInfo#toPublicDto()}），剔除敏感数据。
 */
public class SnapshotPublisher {
    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;

    public SnapshotPublisher(BiliCraftTicketSystem plugin, WebLinkClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * 全量同步：geo + systems + lines。
     */
    public void publishAll() {
        publishGeo();
        publishSystems();
        publishLines();
    }

    /**
     * 读取 geodataDir 下所有 geojson，合并后推 {@code snapshot.geo}。
     */
    public void publishGeo() {
        FeatureCollection merged = mergeGeojsonDir(plugin.getGeodataDir());
        if (merged == null) {
            return;
        }
        ObjectMapper mapper = Envelope.MAPPER;
        ObjectNode data = mapper.createObjectNode();
        data.set("featureCollection", mapper.valueToTree(merged));
        client.send(Envelope.of(MsgType.SNAPSHOT_GEO, data));
    }

    /**
     * 推 {@code snapshot.systems}（仅公开字段）。
     */
    public void publishSystems() {
        ArrayNode arr = Envelope.MAPPER.createArrayNode();
        for (RailwaySystemInfo info : RailwaySystemConfig.allSystems()) {
            ObjectNode dto = Envelope.MAPPER.valueToTree(info.toPublicDto());
            // 读取该系统的 web logo 图片（若存在）转 base64 data URL，作为 logoUrl 直接可用
            String logo = readLogoBase64(info.getId());
            if (logo != null) {
                dto.put("logoUrl", logo);
            }
            arr.add(dto);
        }
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        data.set("systems", arr);
        client.send(Envelope.of(MsgType.SNAPSHOT_SYSTEMS, data));
    }

    /**
     * 读取铁路系统的 web logo 文件并转 base64 data URL（无文件 / 读失败返回 null）。
     *
     * @param systemId 系统 id
     * @return {@code data:image/png;base64,...}；无 logo 返回 null
     */
    private static String readLogoBase64(String systemId) {
        File file = ImageUtils.getSystemImageFileWeb(systemId);
        if (!file.isFile()) {
            return null;
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 推 {@code snapshot.lines}（id/name/color/systemId/stations/ring）。
     */
    public void publishLines() {
        ArrayNode arr = Envelope.MAPPER.createArrayNode();
        for (LineInfo info : LineConfig.allLines()) {
            ObjectNode line = Envelope.MAPPER.createObjectNode();
            line.put("id", info.getId());
            line.put("name", info.getLineName());
            line.put("color", info.getLineColor());
            line.put("systemId", info.getRailwaySystemId());
            ArrayNode stations = line.putArray("stations");
            for (String s : info.getBossbarStations()) {
                stations.add(s);
            }
            line.put("ring", info.isRing());
            // 折返站（:RV）干净站名，前端寻路据此跳过，与插件 GeoRouteEngine 同步
            ArrayNode reverse = line.putArray("reverseStations");
            for (String s : info.getReverseStationNames()) {
                reverse.add(s);
            }
            arr.add(line);
        }
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        data.set("lines", arr);
        client.send(Envelope.of(MsgType.SNAPSHOT_LINES, data));
    }

    /**
     * 读取目录下所有 {@code *.geojson} 并按 feature id 去重合并成单个 FeatureCollection。
     * <p>
     * Point（含 {@code id}）按 id 去重，合并 {@code lineIds} / {@code railwaySystemIds} 并集。
     * <p>
     * LineString <b>同样按 id 去重</b>、合并 {@code enterFrom} 并集：同一条边可能被<b>两个遍历单元</b>
     * 各产出一份（共用轨道 / 越界边，见 {@code TraversalCollector}），各自只记下自己那一侧能到达的面。
     * 不去重会让后端图里出现重复出边，只取其中一份又会让门控面残缺。
     * <p>
     * 注意：物理上重合但 lineId 不同的边 id 本就不同（{@code e.<lineId>.<from>__<to>}），
     * 仍各占一条 feature 靠 layer 叠层显示——这里只合并真正的「同 id 重复」。
     * <p>
     * 纯逻辑（除文件读取），便于复用 / 测试。
     *
     * @param dir geojson 目录
     * @return 合并后的 FeatureCollection；目录不存在 / 无文件返回 null
     */
    public static FeatureCollection mergeGeojsonDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".geojson"));
        if (files == null || files.length == 0) {
            return null;
        }
        ObjectMapper mapper = Envelope.MAPPER;
        // 节点按 id 去重（保留首个，合并 lineIds / railwaySystemIds）
        Map<String, Feature> points = new LinkedHashMap<>();
        // 区间同样按 id 去重（保留首个，合并 enterFrom）；无 id 的区间无从判重，原样保留
        Map<String, Feature> lines = new LinkedHashMap<>();
        List<Feature> unkeyedLines = new ArrayList<>();
        for (File file : files) {
            FeatureCollection fc;
            try {
                fc = mapper.readValue(file, FeatureCollection.class);
            } catch (Exception e) {
                continue;
            }
            if (fc.getFeatures() == null) {
                continue;
            }
            for (Feature f : fc.getFeatures()) {
                String id = stringProp(f, "id");
                if (f.getGeometry() instanceof org.geojson.Point) {
                    if (id == null) {
                        continue;
                    }
                    Feature existing = points.get(id);
                    if (existing == null) {
                        points.put(id, f);
                    } else {
                        mergeStringArray(existing, f, "lineIds");
                        mergeStringArray(existing, f, "railwaySystemIds");
                    }
                } else if (id == null) {
                    unkeyedLines.add(f);
                } else {
                    Feature existing = lines.get(id);
                    if (existing == null) {
                        lines.put(id, f);
                    } else {
                        // 两个单元各产出一份：到达面取并集，门控才完整
                        mergeStringArray(existing, f, "enterFrom");
                    }
                }
            }
        }
        FeatureCollection merged = new FeatureCollection();
        points.values().forEach(merged::add);
        lines.values().forEach(merged::add);
        unkeyedLines.forEach(merged::add);
        return merged;
    }

    private static String stringProp(Feature f, String key) {
        Object v = f.getProperty(key);
        return v == null ? null : v.toString();
    }

    /**
     * 把 {@code src} 的字符串数组属性并入 {@code target}（去重，保持顺序）。
     */
    @SuppressWarnings("unchecked")
    private static void mergeStringArray(Feature target, Feature src, String key) {
        Object srcVal = src.getProperty(key);
        if (!(srcVal instanceof List)) {
            return;
        }
        Object tgtVal = target.getProperty(key);
        List<Object> list = tgtVal instanceof List ? (List<Object>) tgtVal : new ArrayList<>();
        for (Object o : (List<Object>) srcVal) {
            if (!list.contains(o)) {
                list.add(o);
            }
        }
        target.setProperty(key, list);
    }
}
