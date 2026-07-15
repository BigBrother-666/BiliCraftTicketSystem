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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    /**
     * 是否已有一次连接正在建立（互斥，避免并发多条连接被后端 supersede）。
     */
    private volatile boolean connecting;
    /**
     * 是否已有一次重连在排队（去重，避免 onClose / onError / whenComplete 各排一条）。
     */
    private volatile boolean reconnectScheduled;
    /**
     * 连接代次：每次发起连接自增。旧代次监听器的回调一律忽略，
     * 用于强制重连时立即作废旧连接、防止旧 onClose 与新连接串扰。
     */
    private final AtomicInteger connGen = new AtomicInteger();
    @Getter
    private volatile long lastSnapshotTime;

    /**
     * onText 分片累积缓冲（WebSocket 文本帧可能分多次回调）。
     */
    private final StringBuilder textBuffer = new StringBuilder();

    /**
     * 重连次数
     */
    private int retryCount;


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
        reconnectScheduled = false;
        if (closing || !MapConfig.isEnabled()) {
            return;
        }
        // 互斥：已连接或已有连接在途时不再重复发起，避免同 serverId 多连被后端 supersede
        if (connected || connecting) {
            return;
        }
        String url = MapConfig.getBackendUrl();
        if (url == null || url.isEmpty()) {
            log("未配置 backend-url，跳过连接", NamedTextColor.RED);
            return;
        }
        connecting = true;
        // 新一代连接：旧代次的监听器回调将被忽略
        int gen = connGen.incrementAndGet();
        // 建新连接前先关掉上一轮遗留的 HttpClient，防止旧 TCP 连接仍在、线程泄漏
        // （在 Bukkit 异步线程执行，不会阻塞 HttpClient 回调线程）
        closeHttpClient();
        try {
            httpClient = HttpClient.newHttpClient();
            httpClient.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + MapConfig.getSharedToken())
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(url), new Listener(gen))
                    .whenComplete((ws, err) -> {
                        if (err != null && gen == connGen.get()) {
                            connecting = false;
                            if (retryCount < 5) {
                                log("连接后端失败：" + err.getMessage(), NamedTextColor.RED);
                            }
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            connecting = false;
            if (retryCount < 5) {
                log("连接后端异常：" + e.getMessage(), NamedTextColor.RED);
            }
            scheduleReconnect();
        }
    }

    /**
     * 强制重连：无视互斥与退避，立即断开当前连接并发起一次新连接。
     * 供管理指令手动触发。作废旧连接代次，避免旧连接的关闭回调再排重连。
     */
    public void reconnect() {
        if (closing || !MapConfig.isEnabled()) {
            return;
        }
        // 作废当前连接：自增代次后，旧监听器 onClose/onError 将被忽略
        connGen.incrementAndGet();
        connected = false;
        connecting = false;
        reconnectScheduled = false;
        retryCount = 0;
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.abort();
            } catch (Exception ignored) {
            }
        }
        // 在 Bukkit 异步线程发起新连接（connect 内部会 closeHttpClient，避免在此阻塞）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connect);
    }

    /**
     * 优雅关闭（onDisable 调用）。
     */
    public void shutdown() {
        closing = true;
        connected = false;
        connecting = false;
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin-disable");
            } catch (Exception ignored) {
            }
        }
        sendExecutor.shutdownNow();
        closeHttpClient();
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
        connected = false;
        connecting = false;
        if (closing || !MapConfig.isEnabled()) {
            return;
        }
        // 去重：任意时刻只排一条重连，避免 onClose / onError / whenComplete 叠加成风暴
        synchronized (this) {
            if (reconnectScheduled) {
                return;
            }
            reconnectScheduled = true;
        }
        // 注意：不在此处 closeHttpClient()。本方法可能运行在 HttpClient 自身的回调线程
        // （onClose/onError），而 hc.close() 会阻塞至所有操作结束，可能自锁。
        // 旧客户端由下一次 connect() 在 Bukkit 异步线程安全关闭。
        // 指数退避
        long delay = Math.min(
                1L << Math.min(retryCount, 10),
                MapConfig.getMaxReconnectSeconds()
        );
        retryCount++;
        Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                this::connect,
                delay * 20L
        );
    }

    /**
     * 关闭并释放当前 {@link HttpClient}（幂等）。
     */
    private void closeHttpClient() {
        HttpClient hc = this.httpClient;
        if (hc != null) {
            try {
                hc.close();
            } catch (Exception ignored) {
            }
            this.httpClient = null;
        }
        this.webSocket = null;
    }

    private void log(String msg, NamedTextColor color) {
        plugin.getComponentLogger().info(Component.text("[WebLink] " + msg, color));
    }

    /**
     * WebSocket 事件监听：累积文本帧、处理握手与心跳、分派业务消息。
     */
    private class Listener implements WebSocket.Listener {
        /**
         * 本监听器所属连接代次；与 {@link #connGen} 不一致时说明已被更新的连接取代，回调忽略。
         */
        private final int gen;

        Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket ws) {
            if (gen != connGen.get()) {
                // 已被强制重连作废：主动断开这条过期连接，不改动共享状态
                try {
                    ws.abort();
                } catch (Exception ignored) {
                }
                return;
            }
            webSocket = ws;
            connected = true;
            connecting = false;
            log("已连接后端，发送 hello", NamedTextColor.GREEN);
            try {
                send(Envelope.of(MsgType.HELLO, helloDataSupplier.get()));
            } catch (Exception e) {
                log("发送 hello 失败：" + e.getMessage(), NamedTextColor.RED);
            }
            // 连接稳定 30s 后才清零退避计数：若被 supersede 而在此前断开，退避照常增长，避免每秒风暴
            sendExecutor.schedule(() -> {
                if (connected && webSocket == ws) {
                    retryCount = 0;
                }
            }, 30, TimeUnit.SECONDS);
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
            if (gen != connGen.get()) {
                // 过期连接（已被强制重连取代）的关闭，忽略，避免重复排重连
                return null;
            }
            log("连接关闭（" + statusCode + " " + reason + "）", NamedTextColor.YELLOW);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (gen != connGen.get()) {
                return;
            }
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
