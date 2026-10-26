package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.StationAliasConfig;
import com.bigbrother.bilicraftticketsystem.config.StationGroupConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import com.bigbrother.bilicraftticketsystem.wizard.ConfigValidators;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Bukkit;
import org.geojson.Feature;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * 全量同步：geo + systems + lines + stations。
     */
    public void publishAll() {
        publishGeo();
        publishSystems();
        publishLines();
        publishStations();
    }

    /**
     * 推 {@code snapshot.stations}：车站别名映射与车站组。
     * <p>
     * 数据格式：{@code {"aliases": {中文站名: 英文站名}, "groups": {组名: [车站...]}}}
     */
    public void publishStations() {
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        ObjectNode aliases = data.putObject("aliases");
        for (Map.Entry<String, String> e : StationAliasConfig.all().entrySet()) {
            aliases.put(e.getKey(), e.getValue());
        }
        ObjectNode groups = data.putObject("groups");
        for (Map.Entry<String, List<String>> e : StationGroupConfig.all().entrySet()) {
            ArrayNode stations = groups.putArray(e.getKey());
            for (String s : e.getValue()) {
                stations.add(s);
            }
        }
        client.send(Envelope.of(MsgType.SNAPSHOT_STATIONS, data));
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
     * 收入变动触发的私有系统快照推送节流间隔：窗口内多次变动合并为一次推送。
     */
    private static final long INCOME_PUSH_INTERVAL_MS = 60_000L;
    private volatile long lastIncomePush;
    private final AtomicBoolean incomePushScheduled = new AtomicBoolean();

    /**
     * 收入 / 已取出变动后请求推送私有系统快照（节流：{@link #INCOME_PUSH_INTERVAL_MS} 内合并为一次）。
     * 线程安全，可在任意线程调用；实际推送在主线程执行。
     */
    public void requestIncomePush() {
        if (!client.isConnected() || !incomePushScheduled.compareAndSet(false, true)) {
            return;
        }
        long wait = Math.max(0L, lastIncomePush + INCOME_PUSH_INTERVAL_MS - System.currentTimeMillis());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            incomePushScheduled.set(false);
            lastIncomePush = System.currentTimeMillis();
            publishSystemsPrivate();
        }, Math.max(1L, wait / 50L));
    }

    /**
     * 推 {@code snapshot.systems}（仅公开字段）与 {@code snapshot.systems.private}。
     */
    public void publishSystems() {
        publishSystemsPublic();
        publishSystemsPrivate();
    }

    /**
     * 推 {@code snapshot.systems}（仅公开字段）。
     */
    public void publishSystemsPublic() {
        ArrayNode arr = Envelope.MAPPER.createArrayNode();
        for (RailwaySystemInfo info : RailwaySystemConfig.allSystems()) {
            ObjectNode dto = Envelope.MAPPER.valueToTree(info.toPublicDto());
            // 读取该系统的 web logo 图片（若存在）转 base64 data URL，作为 logoUrl 直接可用
            String logo = readLogoBase64(ImageUtils.getSystemImageFileWeb(info.getId()));
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
     * 推 {@code snapshot.systems.private}：含创建者 / 成员 / 收入等内部字段，后端只用于个人中心，不对公开接口暴露。
     * <p>
     * {@code pricePerKm} 为文件中显式配置的价格（未配置为 null，表示沿用全局默认），与 system.update 写回互逆。
     */
    public void publishSystemsPrivate() {
        ArrayNode arr = Envelope.MAPPER.createArrayNode();
        for (RailwaySystemInfo info : RailwaySystemConfig.allSystems()) {
            ObjectNode dto = arr.addObject();
            dto.put("id", info.getId());
            dto.put("name", info.getName());
            if (info.getConfiguredPricePerKm() == null) {
                dto.putNull("pricePerKm");
            } else {
                dto.put("pricePerKm", info.getConfiguredPricePerKm());
            }
            String logo = readLogoBase64(ImageUtils.getSystemImageFileWeb(info.getId()));
            if (logo == null) {
                dto.putNull("logoUrl");
            } else {
                dto.put("logoUrl", logo);
            }
            String mcLogo = readLogoBase64(ImageUtils.getSystemImageFileMc(info.getId()));
            if (mcLogo == null) {
                dto.putNull("mcLogoUrl");
            } else {
                dto.put("mcLogoUrl", mcLogo);
            }
            dto.put("creator", info.getCreator() == null ? "" : uuidString(info.getCreator()));
            ArrayNode members = dto.putArray("members");
            for (UUID uuid : info.getMembersView()) {
                members.add(uuidString(uuid));
            }
            dto.put("income", info.getIncome());
            dto.put("withdrawn", info.getWithdrawn());
        }
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        data.set("systems", arr);
        client.send(Envelope.of(MsgType.SNAPSHOT_SYSTEMS_PRIVATE, data));
    }

    private static String uuidString(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 读取铁路系统的 logo 文件并转 base64 data URL（无文件 / 读失败返回 null）。
     *
     * @param file logo 文件（web 或 mc）
     * @return {@code data:image/png;base64,...}；无 logo 返回 null
     */
    private static String readLogoBase64(File file) {
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
     * 推 {@code snapshot.lines}（公开）与 {@code snapshot.lines.private}（完整配置）。
     */
    public void publishLines() {
        publishLinesPublic();
        publishLinesPrivate();
    }

    /**
     * 推 {@code snapshot.lines.private}：线路完整配置，字段与 railway_routes.yml 一一对应、可无损写回。
     * <ul>
     *   <li>{@code bossbarStations}：yml {@code bossbar-stations} 原样条目（折返站带 {@code :RV}，末项可为转线目标
     *       {@code <线路id>} / {@code <线路id>:<进入站名>}）。</li>
     *   <li>{@code noticeArrival} / {@code noticeDeparture}：与向导输入一致的 {@code ||} 连接字符串（无则空串）。</li>
     *   <li>选填字符串字段缺省为空串。</li>
     *   <li>{@code displayOnly}：yml {@code display-only}（仅展示线路），缺省 false。</li>
     * </ul>
     */
    public void publishLinesPrivate() {
        ArrayNode arr = Envelope.MAPPER.createArrayNode();
        for (LineInfo info : LineConfig.allLines()) {
            ObjectNode line = arr.addObject();
            line.put("id", info.getId());
            line.put("systemId", nullToEmpty(info.getRailwaySystemId()));
            line.put("name", info.getLineName());
            line.put("color", info.getLineColor());
            ArrayNode stations = line.putArray("bossbarStations");
            for (String s : info.getRawBossbarStations()) {
                stations.add(s);
            }
            line.put("bossbarArrivalNotice", nullToEmpty(info.getBossbarArrivalNotice()));
            line.put("bossbarColor", nullToEmpty(info.getBossbarColor()));
            line.put("noticeArrival", ConfigValidators.joinNotices(info.getNoticeArrival()));
            line.put("noticeDeparture", ConfigValidators.joinNotices(info.getNoticeDeparture()));
            line.put("displayOnly", info.isDisplayOnly());
        }
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        data.set("lines", arr);
        client.send(Envelope.of(MsgType.SNAPSHOT_LINES_PRIVATE, data));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 推 {@code snapshot.lines}（id/name/color/systemId/stations/ring/reverseStations/displayOnly）。
     */
    public void publishLinesPublic() {
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
            // 仅展示线路：前后端寻路据此跳过该线路，与插件 GeoRouteEngine 同步
            line.put("displayOnly", info.isDisplayOnly());
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
                        // 正线 / 停靠线标记：首份缺失时取另一份的
                        if (existing.getProperty("track") == null && f.getProperty("track") != null) {
                            existing.setProperty("track", f.getProperty("track"));
                        }
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
