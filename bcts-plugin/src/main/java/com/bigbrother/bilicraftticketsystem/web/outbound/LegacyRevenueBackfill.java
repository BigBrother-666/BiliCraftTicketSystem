package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.database.dao.LegacyRevenueDao;
import com.bigbrother.bilicraftticketsystem.database.dao.RevenueDao;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次性把升级前的历史营收同步到网页后端（{@code /ticketadmin weblink backfill}）。
 * <ul>
 *   <li>{@code revenue_info} 中的购票（ticket）/ 交通卡充值（card_charge）流水 → {@code revenue.event}。
 *       历史购票没有记录当时分给各铁路系统的金额，按「起点 -> 终点」用当前线路图重新估算分摊；
 *       找不到路线时整笔计入起点车站所在系统，起点也找不到则不分摊（只计入玩家统计）。</li>
 *   <li>{@code transit_pass_usage_info} 中的刷卡乘车（pass_type=card）→ {@code revenue.event}（card_ride），
 *       分摊直接取记录里的按系统价格明细（即当时的真实分摊）。</li>
 * </ul>
 * eventId 由「来源表 + 行 id」确定性生成，重复执行时后端按 eventId 去重，不会重复计算；
 * 只同步命令执行瞬间已存在的行，之后的新数据由实时链路上报。事件经 {@link EventOutbox} 发送，断线后可补发。
 */
public class LegacyRevenueBackfill {
    private static final int PAGE_SIZE = 500;
    private static final long PAGE_DELAY_MS = 300L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BiliCraftTicketSystem plugin;
    private final EventOutbox outbox;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LegacyRevenueBackfill(BiliCraftTicketSystem plugin, EventOutbox outbox) {
        this.plugin = plugin;
        this.outbox = outbox;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 开始回填（异步）。已在运行时返回 false。
     *
     * @param sender 进度 / 结果提示对象
     */
    public boolean start(CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        LegacyRevenueDao dao = new LegacyRevenueDao(plugin, plugin.getTrainDatabaseManager().getDs());
        Thread t = new Thread(() -> {
            try {
                run(dao, sender);
            } catch (Exception e) {
                tell(sender, "历史数据同步失败：" + e, NamedTextColor.RED);
            } finally {
                running.set(false);
            }
        }, "bcts-weblink-backfill");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void run(LegacyRevenueDao dao, CommandSender sender) throws InterruptedException {
        long[] max = dao.maxIds();
        tell(sender, "开始同步历史营收：流水 %d 行以内、乘车记录 %d 行以内".formatted(max[0], max[1]), NamedTextColor.AQUA);

        int tickets = 0, charges = 0, estimated = 0, unallocated = 0;
        long after = 0;
        while (true) {
            List<LegacyRevenueDao.RevenueRow> page = dao.findRevenuePage(after, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (LegacyRevenueDao.RevenueRow row : page) {
                after = row.id();
                if (row.id() > max[0]) {
                    break;
                }
                Map<String, Double> alloc = Map.of();
                String type;
                if (RevenueDao.TYPE_TICKET.equals(row.type())) {
                    type = RevenueEventPublisher.TYPE_TICKET;
                    alloc = estimateTicketAllocation(row.detail(), row.amount(), row.playerUuid());
                    if (alloc.isEmpty()) {
                        unallocated++;
                    } else {
                        estimated++;
                    }
                    tickets++;
                } else if (RevenueDao.TYPE_CARD_CHARGE.equals(row.type())) {
                    type = RevenueEventPublisher.TYPE_CARD_CHARGE;
                    charges++;
                } else {
                    continue;
                }
                publish("revenue_info:" + row.id(), type, row.playerUuid(), row.playerName(),
                        row.amount(), row.detail(), alloc, parseTime(row.time()));
            }
            if (after >= max[0] || page.size() < PAGE_SIZE) {
                break;
            }
            Thread.sleep(PAGE_DELAY_MS);
        }

        int rides = 0;
        after = 0;
        while (true) {
            List<LegacyRevenueDao.CardRideRow> page = dao.findCardRidePage(after, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (LegacyRevenueDao.CardRideRow row : page) {
                after = row.id();
                if (row.id() > max[1]) {
                    break;
                }
                Map<String, Double> alloc = parsePriceJson(row.priceJson());
                double amount = alloc.values().stream().mapToDouble(Double::doubleValue).sum();
                publish("transit_pass_usage_info:" + row.id(), RevenueEventPublisher.TYPE_CARD_RIDE,
                        row.playerUuid(), row.playerName(), Math.round(amount * 100.0) / 100.0,
                        row.startStation() + " -> " + row.endStation(), alloc, parseTime(row.boardingTime()));
                rides++;
            }
            if (after >= max[1] || page.size() < PAGE_SIZE) {
                break;
            }
            Thread.sleep(PAGE_DELAY_MS);
        }

        tell(sender, ("历史营收已提交同步：购票 %d 笔（按当前线路图估算分摊 %d 笔，无法分摊 %d 笔）、充值 %d 笔、刷卡乘车 %d 次。"
                + "事件经 outbox 发送，后端未连接时会在重连后补发；重复执行不会重复计算。")
                .formatted(tickets, estimated, unallocated, charges, rides), NamedTextColor.GREEN);
    }

    private void publish(String sourceKey, String type, String uuid, String name, double amount, String detail,
                         Map<String, Double> alloc, long time) {
        ObjectNode data = Envelope.newData();
        // 确定性 eventId：同一行多次回填得到同一 id，后端幂等去重
        data.put("eventId", "backfill-" + UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)));
        data.put("type", type);
        ObjectNode player = data.putObject("player");
        player.put("uuid", uuid == null ? "" : uuid.toLowerCase());
        player.put("name", name == null ? "" : name);
        data.put("amount", amount);
        if (detail != null) {
            data.put("detail", detail);
        }
        ObjectNode a = data.putObject("allocations");
        alloc.forEach(a::put);
        data.put("time", time);
        outbox.enqueue(MsgType.REVENUE_EVENT, data);
    }

    /**
     * 按「起点 -> 终点」用当前线路图估算历史购票的按系统分摊：取最短路，按各段所属系统的票价（成员免费段记 0）
     * 占比分摊实付金额；全部为 0 或找不到路线时整笔计入起点车站所在系统。
     */
    Map<String, Double> estimateTicketAllocation(String detail, double amount, String payerUuid) {
        if (detail == null || !detail.contains(" -> ")) {
            return Map.of();
        }
        String[] parts = detail.split(" -> ", 2);
        String start = parts[0].trim();
        String end = parts[1].trim();
        GeoRoutePath path = null;
        try {
            List<GeoRoutePath> paths = GeoRouteEngine.findByStation(start, end, 1);
            path = paths.isEmpty() ? null : paths.getFirst();
        } catch (Exception ignored) {
            // 线路图未加载等：走起点兜底
        }
        Map<String, Double> raw = new LinkedHashMap<>();
        String startSystem = null;
        if (path != null) {
            List<String> lineIds = path.getLineIdSequence();
            List<Double> dist = path.getDistanceSequence();
            UUID payer = parseUuid(payerUuid);
            Map<String, Boolean> member = new HashMap<>();
            for (int i = 0; i < dist.size(); i++) {
                String systemId = LineConfig.getSystemId(i < lineIds.size() ? lineIds.get(i) : null);
                if (systemId == null) {
                    continue;
                }
                if (startSystem == null) {
                    startSystem = systemId;
                }
                RailwaySystemInfo sys = RailwaySystemConfig.get(systemId);
                double rate = sys != null && sys.getPricePerKm() != null ? sys.getPricePerKm() : MainConfig.pricePerKm;
                boolean waived = sys != null && payer != null && member.computeIfAbsent(systemId, k -> sys.isMember(payer));
                raw.merge(systemId, waived ? 0.0 : dist.get(i) * rate, Double::sum);
            }
        }
        if (startSystem == null) {
            startSystem = systemOfStation(start);
        }
        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> out = new LinkedHashMap<>();
        if (total <= 0) {
            if (startSystem != null) {
                out.put(startSystem, amount);
            }
            return out;
        }
        double assigned = 0;
        String largest = null;
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double v = Math.round(amount * e.getValue() / total * 100.0) / 100.0;
            out.put(e.getKey(), v);
            assigned += v;
            if (largest == null || v > out.get(largest)) {
                largest = e.getKey();
            }
        }
        // 四舍五入残差补到金额最大的系统，保证求和等于实付
        out.merge(largest, Math.round((amount - assigned) * 100.0) / 100.0, Double::sum);
        return out;
    }

    private static String systemOfStation(String station) {
        return GeoRouteEngine.allStationNodes().stream()
                .filter(n -> station.equals(n.getName()))
                .flatMap(n -> n.getLineIds().stream())
                .map(LineConfig::getSystemId)
                .filter(s -> s != null && !s.isEmpty())
                .findFirst().orElse(null);
    }

    /** 解析 transit_pass_usage_info.price：{系统id: {price, distance}}。 */
    static Map<String, Double> parsePriceJson(String json) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = Envelope.MAPPER.readTree(json);
            root.fields().forEachRemaining(e -> {
                JsonNode p = e.getValue().get("price");
                if (p != null && p.isNumber()) {
                    out.put(e.getKey(), p.asDouble());
                }
            });
        } catch (Exception ignored) {
            // 损坏的明细：不分摊
        }
        return out;
    }

    static long parseTime(String s) {
        try {
            return LocalDateTime.parse(s, TIME_FMT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void tell(CommandSender sender, String msg, NamedTextColor color) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text("[WebLink] " + msg, color)));
        plugin.getComponentLogger().info(Component.text("[WebLink] " + msg, color));
    }
}
