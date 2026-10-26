package com.bigbrother.bilicraftticketsystem.web.inbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.commands.ConfigEditCommand;
import com.bigbrother.bilicraftticketsystem.config.StationAliasConfig;
import com.bigbrother.bilicraftticketsystem.config.StationConfigRules;
import com.bigbrother.bilicraftticketsystem.config.StationGroupConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.bigbrother.bilicraftticketsystem.web.outbound.SnapshotPublisher;
import com.bigbrother.bilicraftticketsystem.wizard.ConfigValidators;
import com.bigbrother.bilicraftticketsystem.wizard.WizardStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理后端下发的 {@code config.request}（网页个人中心编辑铁路系统 / 线路 / 车站别名 / 车站组），回 {@code config.result}。
 * <p>
 * 权限判定已在后端完成，插件只做数据校验（与游戏内向导 / 指令共用 {@link ConfigValidators} /
 * {@link StationConfigRules}），写回 yml 后局部重载并推送最新快照。
 * 处理在主线程执行（logo 解码缩放在异步线程）；幂等：同一 requestId 只处理一次，重复请求回上次结果。
 */
public class ConfigRequestHandler {
    private record CachedResult(ObjectNode result, long time) {
    }

    private static final long RESULT_TTL_MS = 5 * 60 * 1000L;

    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;
    private final SnapshotPublisher snapshots;
    private final Map<String, CachedResult> processed = new ConcurrentHashMap<>();
    /**
     * 正在处理中的 requestId（防止处理完成前的重发被执行两次）。
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ConfigRequestHandler(BiliCraftTicketSystem plugin, WebLinkClient client, SnapshotPublisher snapshots) {
        this.plugin = plugin;
        this.client = client;
        this.snapshots = snapshots;
    }

    /**
     * 处理一条 config.request 信封。
     *
     * @param env 信封（data 含 requestId / op / actorUuid / actorName / payload）
     */
    public void handle(Envelope env) {
        JsonNode data = env.data;
        if (data == null) {
            return;
        }
        String requestId = text(data, "requestId");
        if (requestId == null || requestId.isEmpty()) {
            requestId = env.id;
        }
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        cleanupExpired();
        CachedResult cached = processed.get(requestId);
        if (cached != null) {
            reply(requestId, cached.result());
            return;
        }
        if (!inFlight.add(requestId)) {
            return;
        }
        String op = text(data, "op");
        String actor = text(data, "actorName");
        JsonNode payload = data.has("payload") && data.get("payload").isObject() ? data.get("payload") : Envelope.newData();
        String finalRequestId = requestId;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ObjectNode result;
            try {
                result = process(finalRequestId, op, payload);
            } catch (Exception e) {
                plugin.getComponentLogger().warn(Component.text(
                        "[WebLink] 处理配置请求 %s(%s) 异常：%s".formatted(op, finalRequestId, e), NamedTextColor.YELLOW));
                result = fail("internal-error", "插件处理时发生错误：" + e.getMessage(), null);
            }
            if (result != null) {
                // result == null 表示异步继续处理（logo），由其自行 complete
                complete(finalRequestId, result);
                if (result.path("success").asBoolean()) {
                    plugin.getComponentLogger().info(Component.text(
                            "[WebLink] 网页端 %s 执行了配置操作 %s".formatted(actor, op), NamedTextColor.DARK_AQUA));
                }
            }
        });
    }

    private void complete(String requestId, ObjectNode result) {
        result.put("requestId", requestId);
        processed.put(requestId, new CachedResult(result, System.currentTimeMillis()));
        inFlight.remove(requestId);
        reply(requestId, result);
    }

    private void reply(String requestId, ObjectNode result) {
        client.send(Envelope.of(MsgType.CONFIG_RESULT, requestId, result));
    }

    /**
     * 主线程执行一次配置操作。返回 null 表示已转异步处理，完成后自行回执。
     */
    private ObjectNode process(String requestId, String op, JsonNode p) {
        if (GeoTraversalTask.isTraversalRunning()) {
            return fail("traversal-running", "铁路系统正在进行铁轨遍历，暂停线路/铁路系统配置，请稍后再试", null);
        }
        if (op == null) {
            return fail("unknown-op", "未知的配置操作", null);
        }
        return switch (op) {
            case "system.update" -> systemUpdate(p);
            case "system.delete" -> systemDelete(p);
            case "system.logo" -> systemLogo(requestId, p);
            case "line.update" -> lineUpdate(p);
            case "line.delete" -> lineDelete(p);
            case "station_alias.upsert" -> aliasUpsert(p);
            case "station_alias.delete" -> aliasDelete(p);
            case "station_group.upsert" -> groupUpsert(p);
            case "station_group.delete" -> groupDelete(p);
            default -> fail("unknown-op", "未知的配置操作：" + op, null);
        };
    }

    // ===== 铁路系统 =====

    private ObjectNode systemUpdate(JsonNode p) {
        String systemId = text(p, "systemId");
        RailwaySystemInfo info = RailwaySystemConfig.get(systemId);
        if (info == null) {
            return fail("not-found", "铁路系统不存在", null);
        }
        String name = text(p, "name");
        WizardStep.Result r = ConfigValidators.systemName(name);
        if (!r.isOk()) {
            return invalid("name", r.getError());
        }
        Double pricePerKm = null;
        JsonNode priceNode = p.get("pricePerKm");
        if (priceNode != null && !priceNode.isNull()) {
            if (!priceNode.isNumber()) {
                return invalid("pricePerKm", "每公里价格必须是数字");
            }
            r = ConfigValidators.pricePerKm(priceNode.asDouble());
            if (!r.isOk()) {
                return invalid("pricePerKm", r.getError());
            }
            pricePerKm = (Double) r.getValue();
        }
        // members 缺省 = 不修改；否则整体替换，创建者始终保留
        Set<UUID> members = new LinkedHashSet<>(info.getMembersView());
        JsonNode membersNode = p.get("members");
        if (membersNode != null && !membersNode.isNull()) {
            if (!membersNode.isArray()) {
                return invalid("members", "成员列表格式无效");
            }
            Set<UUID> next = new LinkedHashSet<>();
            if (info.getCreator() != null) {
                next.add(info.getCreator());
            }
            for (JsonNode n : membersNode) {
                try {
                    next.add(UUID.fromString(n.asText().trim()));
                } catch (IllegalArgumentException e) {
                    return invalid("members", "成员 UUID 格式无效：" + n.asText());
                }
            }
            members = next;
        }
        // creator 传 null：upsert 保留原创建者；income / withdrawn 不触碰
        RailwaySystemConfig.upsert(systemId, name, members, pricePerKm, null);
        RailwaySystemConfig.load(plugin);
        snapshots.publishSystems();
        return ok(null);
    }

    private ObjectNode systemDelete(JsonNode p) {
        String systemId = text(p, "systemId");
        if (!RailwaySystemConfig.contains(systemId)) {
            return fail("not-found", "铁路系统不存在", null);
        }
        List<String> lineIds = LineConfig.getLineIdsOfSystem(systemId);
        ConfigEditCommand.deleteSystemFiles(systemId, lineIds);
        RailwaySystemConfig.load(plugin);
        LineConfig.load(plugin);
        // 清理线路图数据并重载路由图，完成后推全量快照（失败只记日志：配置已删除，回执仍为成功）
        ConfigEditCommand.removeGeodata(plugin, lineIds, List.of(systemId), (res, err) -> logGeodataError(err));
        // 线路图清理是异步的，先推一次系统 / 线路快照，让网页端立即看到删除
        snapshots.publishSystems();
        snapshots.publishLines();
        return ok(null);
    }

    private ObjectNode systemLogo(String requestId, JsonNode p) {
        String systemId = text(p, "systemId");
        if (!RailwaySystemConfig.contains(systemId)) {
            return fail("not-found", "铁路系统不存在", null);
        }
        String kind = text(p, "kind");
        if (!"web".equals(kind) && !"mc".equals(kind)) {
            return invalid("kind", "未知的 Logo 类型");
        }
        boolean isWeb = "web".equals(kind);
        boolean clear = p.path("clear").asBoolean(false);
        String base64 = text(p, "imageBase64");
        if (!clear && (base64 == null || base64.isEmpty())) {
            return invalid("file", "请选择图片");
        }
        // 解码 / 缩放 / 写文件放异步线程，完成后回主线程推送并回执
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ObjectNode result;
            try {
                if (clear) {
                    File file = isWeb ? ImageUtils.getSystemImageFileWeb(systemId) : ImageUtils.getSystemImageFileMc(systemId);
                    java.nio.file.Files.deleteIfExists(file.toPath());
                    result = ok(null);
                } else {
                    byte[] bytes;
                    try {
                        bytes = Base64.getDecoder().decode(base64);
                    } catch (IllegalArgumentException e) {
                        bytes = null;
                    }
                    String error = bytes == null ? "图片数据无效" : ImageUtils.saveSystemLogo(systemId, bytes, isWeb);
                    result = error == null ? ok(null) : invalid("file", error);
                }
                if (result.path("success").asBoolean() && !isWeb) {
                    ArrayNode warnings = result.putArray("warnings");
                    warnings.add("游戏内 logo 需重新生成 Oraxen 资源包后生效");
                }
            } catch (Exception e) {
                result = fail("internal-error", "保存图片时发生错误：" + e.getMessage(), null);
            }
            ObjectNode finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalResult.path("success").asBoolean()) {
                    snapshots.publishSystems();
                }
                complete(requestId, finalResult);
            });
        });
        return null;
    }

    // ===== 线路 =====

    private ObjectNode lineUpdate(JsonNode p) {
        String lineId = text(p, "id");
        if (lineId == null || !LineConfig.contains(lineId)) {
            return fail("not-found", "线路不存在", null);
        }
        WizardStep.Result r = ConfigValidators.existingSystem(text(p, "systemId"));
        if (!r.isOk()) {
            return invalid("systemId", r.getError());
        }
        String systemId = (String) r.getValue();
        String name = text(p, "name");
        r = ConfigValidators.lineName(name);
        if (!r.isOk()) {
            return invalid("name", r.getError());
        }
        r = ConfigValidators.hexColor(text(p, "color"));
        if (!r.isOk()) {
            return invalid("color", r.getError());
        }
        String color = (String) r.getValue();
        r = ConfigValidators.stations(stringList(p.get("bossbarStations")));
        if (!r.isOk()) {
            return invalid("bossbarStations", r.getError());
        }
        @SuppressWarnings("unchecked")
        List<String> stations = (List<String>) r.getValue();
        String arrivalNotice = emptyToNull(text(p, "bossbarArrivalNotice"));
        String bossbarColor = emptyToNull(text(p, "bossbarColor"));
        if (bossbarColor != null) {
            r = ConfigValidators.bossbarColor(bossbarColor);
            if (!r.isOk()) {
                return invalid("bossbarColor", r.getError());
            }
            bossbarColor = (String) r.getValue();
        }
        r = ConfigValidators.noticeList(text(p, "noticeArrival"));
        if (!r.isOk()) {
            return invalid("noticeArrival", r.getError());
        }
        @SuppressWarnings("unchecked")
        List<String> noticeArrival = (List<String>) r.getValue();
        r = ConfigValidators.noticeList(text(p, "noticeDeparture"));
        if (!r.isOk()) {
            return invalid("noticeDeparture", r.getError());
        }
        @SuppressWarnings("unchecked")
        List<String> noticeDeparture = (List<String>) r.getValue();

        boolean displayOnly = p.path("displayOnly").asBoolean(false);

        LineConfig.upsert(lineId, systemId, name, color, stations, arrivalNotice, bossbarColor, noticeArrival, noticeDeparture, displayOnly);
        LineConfig.load(plugin);
        snapshots.publishLines();
        ObjectNode res = ok(null);
        res.putArray("warnings").add("线路走向 / 车站若有变动，请在游戏内用 /railgeo walkAll 重新遍历生成路由图");
        return res;
    }

    private ObjectNode lineDelete(JsonNode p) {
        String lineId = text(p, "lineId");
        if (lineId == null || !LineConfig.contains(lineId)) {
            return fail("not-found", "线路不存在", null);
        }
        ConfigEditCommand.deleteLineFiles(lineId);
        RailwaySystemConfig.load(plugin);
        LineConfig.load(plugin);
        ConfigEditCommand.removeGeodata(plugin, List.of(lineId), List.of(), (res, err) -> logGeodataError(err));
        snapshots.publishLines();
        return ok(null);
    }

    private void logGeodataError(String err) {
        if (err != null) {
            plugin.getComponentLogger().warn(Component.text(
                    "[WebLink] 清理线路图数据失败：%s。请用 /railgeo walkAll 重新构建线路图".formatted(err), NamedTextColor.YELLOW));
        }
    }

    // ===== 车站别名 / 车站组 =====

    private ObjectNode aliasUpsert(JsonNode p) {
        String station = trimOrNull(text(p, "station"));
        if (station == null) {
            return invalid("station", "车站名不能为空");
        }
        String en = StationConfigRules.normalizeEnglishName(text(p, "englishName"));
        if (en.isEmpty()) {
            return invalid("englishName", "英文车站名不能为空");
        }
        String warning = StationConfigRules.missingStationsWarning(List.of(station));
        StationAliasConfig.upsert(station, en);
        snapshots.publishStations();
        return ok(warning);
    }

    private ObjectNode aliasDelete(JsonNode p) {
        String station = trimOrNull(text(p, "station"));
        if (station == null || !StationAliasConfig.delete(station)) {
            return fail("not-found", "该车站没有设置英文名", null);
        }
        snapshots.publishStations();
        return ok(null);
    }

    private ObjectNode groupUpsert(JsonNode p) {
        String group = trimOrNull(text(p, "group"));
        if (group == null || group.contains(" ") || group.contains("\t")) {
            return invalid("group", "组名不能为空且不能包含空格");
        }
        Set<String> unique = StationConfigRules.distinctStations(stringList(p.get("stations")));
        if (unique.size() < 2) {
            return invalid("stations", "车站组至少需要两个不同的车站");
        }
        Map<String, String> conflicts = StationConfigRules.groupConflicts(group, unique);
        if (!conflicts.isEmpty()) {
            ObjectNode res = fail("conflict", "部分车站已属于其它车站组，一个车站只能属于一个组", "stations");
            ObjectNode c = res.putObject("conflicts");
            conflicts.forEach(c::put);
            return res;
        }
        String warning = StationConfigRules.missingStationsWarning(unique);
        StationGroupConfig.upsert(group, new ArrayList<>(unique));
        snapshots.publishStations();
        return ok(warning);
    }

    private ObjectNode groupDelete(JsonNode p) {
        String group = trimOrNull(text(p, "group"));
        if (group == null || !StationGroupConfig.delete(group)) {
            return fail("not-found", "车站组不存在", null);
        }
        snapshots.publishStations();
        return ok(null);
    }

    // ===== 工具 =====

    private static ObjectNode ok(String warning) {
        ObjectNode result = Envelope.newData();
        result.put("success", true);
        if (warning != null) {
            result.putArray("warnings").add(warning);
        }
        return result;
    }

    private static ObjectNode invalid(String field, String message) {
        return fail("invalid", message, field);
    }

    private static ObjectNode fail(String reason, String message, String field) {
        ObjectNode result = Envelope.newData();
        result.put("success", false);
        result.put("reason", reason);
        if (message != null) {
            result.put("message", message);
        }
        if (field != null) {
            result.put("field", field);
        }
        return result;
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        processed.entrySet().removeIf(e -> now - e.getValue().time() > RESULT_TTL_MS);
    }

    private static String text(JsonNode node, String key) {
        JsonNode v = node == null ? null : node.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static List<String> stringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
