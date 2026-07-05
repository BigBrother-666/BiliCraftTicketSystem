package com.bigbrother.bilicraftticketsystem.web;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.web.inbound.PurchaseHandler;
import com.bigbrother.bilicraftticketsystem.web.inbound.SyncHandler;
import com.bigbrother.bilicraftticketsystem.web.outbound.BindPublisher;
import com.bigbrother.bilicraftticketsystem.web.outbound.RideEventPublisher;
import com.bigbrother.bilicraftticketsystem.web.outbound.SnapshotPublisher;
import com.bigbrother.bilicraftticketsystem.web.outbound.TrainTelemetryTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Web 后端对接的门面：聚合 WebSocket 客户端、出站发布器与入站处理器，统一由主插件管理生命周期。
 * <p>
 * 仅当 {@code config_map.yml} 的 {@code web-link.enabled=true} 时由主插件创建并 {@link #start()}。
 */
@Getter
public class WebLink {
    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;
    private final SnapshotPublisher snapshotPublisher;
    private final BindPublisher bindPublisher;
    private final RideEventPublisher rideEventPublisher;
    private final PurchaseHandler purchaseHandler;
    private final SyncHandler syncHandler;

    private BukkitTask telemetryTask;

    public WebLink(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
        // 客户端：注入 hello 负载、握手完成回调、入站分派
        this.client = new WebLinkClient(plugin, this::buildHelloData, this::onWelcome, this::dispatch);
        this.snapshotPublisher = new SnapshotPublisher(plugin, client);
        this.bindPublisher = new BindPublisher(plugin, client);
        this.rideEventPublisher = new RideEventPublisher(client);
        this.purchaseHandler = new PurchaseHandler(plugin, client);
        this.syncHandler = new SyncHandler(snapshotPublisher);
    }

    /**
     * 启动：异步连接后端，并按需注册列车遥测任务。
     */
    public void start() {
        client.connect();
        registerTelemetry();
    }

    /**
     * 推送一条「列车已移除」消息，让前端立即清除该列车（矿车销毁事件触发，见 PROBLEMS 问题 3）。
     *
     * @param trainId 列车唯一标识（与遥测的 trainId 一致）
     */
    public void publishRemovedTrain(String trainId) {
        if (trainId == null || trainId.isEmpty() || !client.isConnected()) {
            return;
        }
        ObjectNode data = Envelope.newData();
        data.put("trainId", trainId);
        client.send(Envelope.of(MsgType.REALTIME_REMOVED, data));
    }

    /**
     * 优雅关闭：停遥测任务、关闭 WS。
     */
    public void shutdown() {
        if (telemetryTask != null) {
            telemetryTask.cancel();
            telemetryTask = null;
        }
        client.shutdown();
    }

    private void registerTelemetry() {
        if (!MapConfig.isTelemetryEnabled()) {
            return;
        }
        long interval = Math.max(1L, MapConfig.getTelemetryIntervalTicks());
        telemetryTask = new TrainTelemetryTask(plugin, client)
                .runTaskTimer(plugin, interval, interval);
    }

    private JsonNode buildHelloData() {
        ObjectNode data = Envelope.newData();
        data.put("serverId", MapConfig.getServerId());
        data.put("pluginVersion", plugin.getPluginMeta().getVersion());
        var worlds = data.putArray("worlds");
        for (World w : Bukkit.getWorlds()) {
            worlds.add(w.getName());
        }
        return data;
    }

    /**
     * 握手完成：全量同步快照 + 重推绑定列表（须在已连接后）。
     */
    private void onWelcome() {
        snapshotPublisher.publishAll();
        bindPublisher.republishAll();
    }

    /**
     * 入站消息分派（ping 已在 client 内处理）。
     *
     * @param type 消息类型
     * @param env  完整信封
     */
    private void dispatch(String type, Envelope env) {
        switch (type) {
            case MsgType.PURCHASE_REQUEST -> purchaseHandler.handle(env);
            case MsgType.SYNC_REQUEST -> syncHandler.handle(env);
            default -> {
                // 未知消息类型，忽略
            }
        }
    }
}
