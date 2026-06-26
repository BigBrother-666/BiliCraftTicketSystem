package com.bigbrother.bilicraftticketsystem.web;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 出站 WebSocket 客户端：插件主动连接铁路线路图后端的内部端点（见 docs/PLUGIN_ADDENDUM.md §三）。
 * <p>
 * 用 Java 内置 {@link java.net.http.WebSocket}，握手时带 {@code Authorization: Bearer <token>}。
 * 负责连接 / 握手 / 心跳（被动回 pong）/ 断线重连，并把收到的消息分派给 {@link #inboundDispatcher}。
 * 所有需要触碰 Bukkit / TC / Vault 的操作由分派目标自行切回主线程。
 * <p>
 * 线程模型：收发都在 HttpClient 的内部线程；{@link #send} 经单线程 {@link #sendExecutor} 串行化，
 * 保证发送顺序且不阻塞调用方。
 */
public class WebLinkClient {
    private final BiliCraftTicketSystem plugin;

    /**
     * 握手首帧 {@code hello} 的负载提供者（由主插件注入，含 serverId / 版本 / worlds）。
     */
    private final Supplier<JsonNode> helloDataSupplier;
    /**
     * 握手完成（收到 {@code welcome}）后的回调：触发全量同步 + 重推绑定。
     */
    private final Runnable onWelcome;
    /**
     * 入站消息分派器：参数为 (消息 type, 完整信封)。{@code ping} 已在内部处理，不进此分派。
     */
    private final BiConsumer<String, Envelope> inboundDispatcher;

    private final ScheduledExecutorService sendExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bcts-weblink-send");
                t.setDaemon(true);
                return t;
            });

    private volatile WebSocket webSocket;

    private volatile HttpClient httpClient;
    @Getter
    private volatile boolean connected;
    private volatile boolean closing;
    @Getter
    private volatile long lastSnapshotTime;

    /**
     * onText 分片累积缓冲（WebSocket 文本帧可能分多次回调）。
     */
    private final StringBuilder textBuffer = new StringBuilder();

    public WebLinkClient(BiliCraftTicketSystem plugin,
                         Supplier<JsonNode> helloDataSupplier,
                         Runnable onWelcome,
                         BiConsumer<String, Envelope> inboundDispatcher) {
        this.plugin = plugin;
        this.helloDataSupplier = helloDataSupplier;
        this.onWelcome = onWelcome;
        this.inboundDispatcher = inboundDispatcher;
    }

    /**
     * 异步发起连接（不阻塞调用线程）。失败按配置重连。
     */
    public void connect() {
        if (closing) {
            return;
        }
        String url = MapConfig.getBackendUrl();
        if (url == null || url.isEmpty()) {
            log("未配置 backend-url，跳过连接", NamedTextColor.RED);
            return;
        }
        try {
            httpClient = HttpClient.newHttpClient();
            httpClient.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + MapConfig.getSharedToken())
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(url), new Listener())
                    .whenComplete((ws, err) -> {
                        if (err != null) {
                            log("连接后端失败：" + err.getMessage(), NamedTextColor.RED);
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            log("连接后端异常：" + e.getMessage(), NamedTextColor.RED);
            scheduleReconnect();
        }
    }

    /**
     * 优雅关闭（onDisable 调用）。
     */
    public void shutdown() {
        closing = true;
        connected = false;
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin-disable");
            } catch (Exception ignored) {
            }
        }
        sendExecutor.shutdownNow();
        HttpClient hc = this.httpClient;
        if (hc != null) {
            try {
                hc.close();
            } catch (Exception ignored) {
            }
            this.httpClient = null;
        }
    }

    /**
     * 发送一条消息（线程安全，经单线程串行化）。未连接时静默丢弃。
     *
     * @param envelope 信封
     */
    public void send(Envelope envelope) {
        WebSocket ws = this.webSocket;
        if (ws == null || !connected) {
            return;
        }
        sendExecutor.execute(() -> {
            try {
                ws.sendText(envelope.encode(), true);
                if (envelope.type != null && envelope.type.startsWith("snapshot.")) {
                    lastSnapshotTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                log("发送消息失败（" + envelope.type + "）：" + e.getMessage(), NamedTextColor.RED);
            }
        });
    }

    private void scheduleReconnect() {
        if (closing) {
            return;
        }
        connected = false;
        long ticks = Math.max(1L, MapConfig.getReconnectSeconds()) * 20L;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::connect, ticks);
    }

    private void log(String msg, NamedTextColor color) {
        plugin.getComponentLogger().info(Component.text("[WebLink] " + msg, color));
    }

    /**
     * WebSocket 事件监听：累积文本帧、处理握手与心跳、分派业务消息。
     */
    private class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            connected = true;
            log("已连接后端，发送 hello", NamedTextColor.GREEN);
            try {
                send(Envelope.of(MsgType.HELLO, helloDataSupplier.get()));
            } catch (Exception e) {
                log("发送 hello 失败：" + e.getMessage(), NamedTextColor.RED);
            }
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String json = textBuffer.toString();
                textBuffer.setLength(0);
                handleMessage(json);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log("连接关闭（" + statusCode + " " + reason + "）", NamedTextColor.YELLOW);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log("连接错误：" + error.getMessage(), NamedTextColor.RED);
            scheduleReconnect();
        }
    }

    /**
     * 处理一条完整文本消息：心跳就地回 pong，welcome 触发同步回调，其余交分派器。
     *
     * @param json 完整 JSON 文本
     */
    private void handleMessage(String json) {
        Envelope env;
        try {
            env = Envelope.decode(json);
        } catch (Exception e) {
            log("无法解析消息：" + e.getMessage(), NamedTextColor.RED);
            return;
        }
        if (env.type == null) {
            return;
        }
        switch (env.type) {
            case MsgType.PING -> send(Envelope.of(MsgType.PONG, null));
            case MsgType.WELCOME -> {
                log("握手完成，开始全量同步", NamedTextColor.GREEN);
                if (onWelcome != null) {
                    onWelcome.run();
                }
            }
            default -> {
                if (inboundDispatcher != null) {
                    inboundDispatcher.accept(env.type, env);
                }
            }
        }
    }
}
