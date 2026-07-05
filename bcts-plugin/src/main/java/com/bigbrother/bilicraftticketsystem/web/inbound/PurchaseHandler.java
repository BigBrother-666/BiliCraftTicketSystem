package com.bigbrother.bilicraftticketsystem.web.inbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理后端下发的 {@code purchase.request}（网页在线购票，见 docs/PLUGIN_ADDENDUM.md §六）。
 * <p>
 * 路线在前端已选定，携带有序 {@code nodeIds} + {@code lineIdSequence}；插件不重新寻路，
 * 用 {@link GeoRouteEngine#validatePath} 校验合法性并重建路径，再复用
 * {@link BCTicket#purchaseSilently()} 完成 Vault 扣款 + 实体票交付 + 收入分摊。
 * <p>
 * 处理在主线程执行（涉及 Bukkit / Vault / 背包）；幂等：同一 requestId 只处理一次。
 */
public class PurchaseHandler {
    /**
     * 已处理 requestId → 上次结果（带时间戳），防 WS 重发导致重复扣款。
     */
    private record CachedResult(ObjectNode result, long time) {
    }

    private static final long RESULT_TTL_MS = 5 * 60 * 1000L;

    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;
    private final Map<String, CachedResult> processed = new ConcurrentHashMap<>();

    public PurchaseHandler(BiliCraftTicketSystem plugin, WebLinkClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * 处理一条 purchase.request 信封。
     *
     * @param env 信封（data 含 requestId/playerUuid/nodeIds/lineIdSequence/speedKph?/maxUses?）
     */
    public void handle(Envelope env) {
        JsonNode data = env.data;
        if (data == null) {
            return;
        }
        String requestId = text(data, "requestId");
        if (requestId == null) {
            return;
        }
        // 幂等：命中缓存直接回上次结果
        cleanupExpired();
        CachedResult cached = processed.get(requestId);
        if (cached != null) {
            client.send(Envelope.of(MsgType.PURCHASE_RESULT, requestId, cached.result));
            return;
        }

        String playerUuidStr = text(data, "playerUuid");
        List<String> nodeIds = stringList(data.get("nodeIds"));
        List<String> lineIdSequence = stringList(data.get("lineIdSequence"));
        double speedKph = data.has("speedKph") ? data.get("speedKph").asDouble() : MainConfig.maxSpeed * 20 * 3.6;
        int maxUses = data.has("maxUses") ? data.get("maxUses").asInt() : MapConfig.getPurchaseMaxUses();

        // 切回主线程执行所有游戏侧操作
        Bukkit.getScheduler().runTask(plugin, () -> {
            ObjectNode result = process(requestId, playerUuidStr, nodeIds, lineIdSequence, speedKph, maxUses);
            processed.put(requestId, new CachedResult(result, System.currentTimeMillis()));
            client.send(Envelope.of(MsgType.PURCHASE_RESULT, requestId, result));
        });
    }

    /**
     * 主线程中执行购票全过程，返回 purchase.result 负载。
     */
    private ObjectNode process(String requestId, String playerUuidStr, List<String> nodeIds,
                               List<String> lineIdSequence, double speedKph, int maxUses) {
        if (!MapConfig.isPurchaseEnabled()) {
            return fail("purchase-disabled");
        }
        // 玩家在线校验
        Player player = parsePlayer(playerUuidStr);
        if (player == null) {
            return fail("player-offline");
        }
        // 遍历互斥（与游戏内一致）
        if (GeoTraversalTask.isTraversalRunning()) {
            return fail("traversal-running");
        }
        // 校验前端选定路线合法并重建
        GeoRoutePath path = GeoRouteEngine.validatePath(nodeIds, lineIdSequence);
        if (path == null) {
            return fail("invalid-route");
        }
        // 速度 / 次数夹紧（按 config.yml 上限，与游戏内一致）
        double maxSpeedMpt = clamp(CommonUtils.kph2Mpt(speedKph), MainConfig.minSpeed, MainConfig.maxSpeed);
        int uses = Math.max(1, Math.min(maxUses, MainConfig.maxUses));

        try {
            BCTicket ticket = new BCTicket(uses, maxSpeedMpt, path, player);
            EconomyResponse r = ticket.purchaseSilently();
            if (!r.transactionSuccess()) {
                return fail("insufficient-funds");
            }
            ObjectNode result = Envelope.newData();
            result.put("success", true);
            result.put("ticketName", ticket.getTicketName());
            result.put("price", r.amount);
            result.put("balanceAfter", plugin.getEcon().getBalance(player));
            return result;
        } catch (Exception e) {
            plugin.getComponentLogger().warn(net.kyori.adventure.text.Component.text(
                    "[WebLink] 处理购票 " + requestId + " 异常：" + e.getMessage()));
            return fail("internal-error");
        }
    }

    private static ObjectNode fail(String reason) {
        ObjectNode result = Envelope.newData();
        result.put("success", false);
        result.put("reason", reason);
        return result;
    }

    private static Player parsePlayer(String uuidStr) {
        if (uuidStr == null) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        processed.entrySet().removeIf(e -> now - e.getValue().time() > RESULT_TTL_MS);
    }

    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<String> stringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
