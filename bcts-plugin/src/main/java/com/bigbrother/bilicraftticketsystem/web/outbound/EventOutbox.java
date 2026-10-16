package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseManager;
import com.bigbrother.bilicraftticketsystem.database.dao.WebOutboxDao;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 需可靠送达的事件（{@code revenue.event} / {@code ride.event} / {@code ride.payment}）的离线补发 outbox。
 * <ol>
 *   <li>{@link #enqueue}：为负载生成 {@code eventId}，先写入 {@code web_outbox} 表，已连接则随即发送；</li>
 *   <li>后端落库后回 {@code event.ack}，{@link #onAck} 删除对应行；</li>
 *   <li>重连握手（welcome）后 {@link #replay} 按 created_at 顺序分批补发积压行，
 *       {@code ride.event} / {@code ride.payment} 补发时附 {@code "replayed": true}。</li>
 * </ol>
 * 保留策略：丢弃 30 天前的行，且最多保留最新 100000 行。所有数据库 IO 都在单线程 {@link #executor} 中执行，
 * 不阻塞主线程，并保证同一事件「先落库后发送」的顺序。
 */
public class EventOutbox {
    private static final int REPLAY_BATCH_SIZE = 200;
    private static final long REPLAY_BATCH_DELAY_MS = 500L;
    private static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_ROWS = 100_000;
    private static final long PRUNE_INTERVAL_MS = 60L * 60 * 1000;

    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bcts-weblink-outbox");
        t.setDaemon(true);
        return t;
    });
    /**
     * 补发代次：每次 welcome 自增，旧代次的补发批次自动停止（防止断线重连后两轮补发并行）。
     */
    private final AtomicInteger replayGen = new AtomicInteger();
    private long lastPrune;

    public EventOutbox(BiliCraftTicketSystem plugin, WebLinkClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * 新建一个带 {@code eventId} 的事件负载。
     *
     * @return 已含随机 eventId 的空负载
     */
    public static ObjectNode newEventData() {
        ObjectNode data = Envelope.newData();
        data.put("eventId", UUID.randomUUID().toString());
        return data;
    }

    /**
     * 事件入队：先写 outbox，再在已连接时发送。线程安全，可在任意线程调用。
     *
     * @param type 消息类型
     * @param data 负载（须含 eventId，见 {@link #newEventData()}）
     */
    public void enqueue(String type, ObjectNode data) {
        String eventId = data.path("eventId").asText("");
        if (eventId.isEmpty()) {
            eventId = UUID.randomUUID().toString();
            data.put("eventId", eventId);
        }
        long now = System.currentTimeMillis();
        String finalEventId = eventId;
        submit(() -> {
            WebOutboxDao dao = dao();
            if (dao != null) {
                dao.insert(finalEventId, type, data.toString(), now);
                pruneIfDue(dao, now);
            }
            client.send(Envelope.of(type, data));
        });
    }

    /**
     * 处理后端 {@code event.ack}：删除已落库的事件。
     *
     * @param env 信封（data.eventIds 为字符串数组）
     */
    public void onAck(Envelope env) {
        JsonNode ids = env.data == null ? null : env.data.get("eventIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()) {
            return;
        }
        List<String> list = new ArrayList<>();
        ids.forEach(n -> list.add(n.asText()));
        submit(() -> {
            WebOutboxDao dao = dao();
            if (dao != null) {
                dao.deleteAll(list);
            }
        });
    }

    /**
     * 重连握手后补发 outbox 中积压的事件（只补发本轮开始前已入库的行，之后的新事件已实时发送）。
     */
    public void replay() {
        int gen = replayGen.incrementAndGet();
        long cutoff = System.currentTimeMillis();
        submit(() -> {
            WebOutboxDao dao = dao();
            if (dao == null) {
                return;
            }
            int pruned = dao.prune(cutoff - RETENTION_MS, MAX_ROWS);
            lastPrune = cutoff;
            if (pruned > 0) {
                log("已丢弃 %d 条过期 / 超量的待补发事件".formatted(pruned), NamedTextColor.YELLOW);
            }
            replayBatch(gen, cutoff, Long.MIN_VALUE, "", 0);
        });
    }

    /**
     * 补发一批，并在批间停顿后调度下一批。运行在 {@link #executor} 中。
     */
    private void replayBatch(int gen, long cutoff, long afterCreatedAt, String afterEventId, int sentSoFar) {
        if (gen != replayGen.get() || !client.isConnected()) {
            return;
        }
        WebOutboxDao dao = dao();
        if (dao == null) {
            return;
        }
        List<WebOutboxDao.OutboxRow> rows = dao.findPage(afterCreatedAt, afterEventId, REPLAY_BATCH_SIZE);
        int sent = sentSoFar;
        WebOutboxDao.OutboxRow last = null;
        boolean reachedCutoff = false;
        for (WebOutboxDao.OutboxRow row : rows) {
            if (row.createdAt() > cutoff) {
                reachedCutoff = true;
                break;
            }
            last = row;
            try {
                ObjectNode data = (ObjectNode) Envelope.MAPPER.readTree(row.payload());
                if (MsgType.RIDE_EVENT.equals(row.type()) || MsgType.RIDE_PAYMENT.equals(row.type())) {
                    data.put("replayed", true);
                }
                client.send(Envelope.of(row.type(), data));
                sent++;
            } catch (Exception e) {
                // 负载损坏：丢弃，避免每次重连都卡在这一行
                dao.deleteAll(List.of(row.eventId()));
            }
        }
        if (last == null || reachedCutoff || rows.size() < REPLAY_BATCH_SIZE) {
            if (sent > 0) {
                log("已补发 %d 条离线期间的事件".formatted(sent), NamedTextColor.GREEN);
            }
            return;
        }
        long nextCreatedAt = last.createdAt();
        String nextEventId = last.eventId();
        int finalSent = sent;
        try {
            executor.schedule(() -> replayBatch(gen, cutoff, nextCreatedAt, nextEventId, finalSent),
                    REPLAY_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // 已关闭
        }
    }

    private void pruneIfDue(WebOutboxDao dao, long now) {
        if (now - lastPrune < PRUNE_INTERVAL_MS) {
            return;
        }
        lastPrune = now;
        dao.prune(now - RETENTION_MS, MAX_ROWS);
    }

    /**
     * 取当前数据库的 DAO（reload 会重建 TrainDatabaseManager，故每次现取）。
     */
    private WebOutboxDao dao() {
        TrainDatabaseManager db = plugin.getTrainDatabaseManager();
        return db == null ? null : db.getWebOutboxDao();
    }

    private void submit(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log("outbox 处理失败：" + e, NamedTextColor.RED);
                }
            });
        } catch (Exception ignored) {
            // 已关闭（插件卸载中）
        }
    }

    /**
     * 关闭：等待已入队的写库任务完成（最多 3 秒），避免卸载时丢失刚产生的事件。
     */
    public void shutdown() {
        replayGen.incrementAndGet();
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String msg, NamedTextColor color) {
        plugin.getComponentLogger().info(Component.text("[WebLink] " + msg, color));
    }
}
