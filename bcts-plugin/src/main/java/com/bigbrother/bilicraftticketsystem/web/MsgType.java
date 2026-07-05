package com.bigbrother.bilicraftticketsystem.web;

/**
 * 插件 ↔ 后端 WebSocket 消息类型常量（点分命名空间，见 docs/BACKEND_PROMPT.md §4）。
 */
public final class MsgType {
    private MsgType() {
    }

    // 插件 → 后端
    public static final String HELLO = "hello";
    public static final String SNAPSHOT_GEO = "snapshot.geo";
    public static final String SNAPSHOT_SYSTEMS = "snapshot.systems";
    public static final String SNAPSHOT_LINES = "snapshot.lines";
    public static final String REALTIME_TRAINS = "realtime.trains";
    public static final String REALTIME_REMOVED = "realtime.removed";
    public static final String PURCHASE_RESULT = "purchase.result";
    public static final String RIDE_EVENT = "ride.event";
    public static final String RIDE_PAYMENT = "ride.payment";
    public static final String PONG = "pong";
    public static final String AUTH_BIND = "auth.bind";
    public static final String AUTH_UNBIND = "auth.unbind";

    // 后端 → 插件
    public static final String WELCOME = "welcome";
    public static final String PING = "ping";
    public static final String PURCHASE_REQUEST = "purchase.request";
    public static final String SYNC_REQUEST = "sync.request";
}
