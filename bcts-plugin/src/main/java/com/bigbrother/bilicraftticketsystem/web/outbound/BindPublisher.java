package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * 把网页账号绑定 / 解绑同步给后端（见 docs/PLUGIN_ADDENDUM.md §七·补）。
 * <p>
 * 后端据此维护「允许登录网页」白名单。重连握手后调 {@link #republishAll()} 全量重推纠偏。
 */
public class BindPublisher {
    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;

    public BindPublisher(BiliCraftTicketSystem plugin, WebLinkClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * 推 {@code auth.bind}。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名
     */
    public void bind(String uuid, String name) {
        ObjectNode data = Envelope.newData();
        data.put("uuid", uuid);
        data.put("name", name);
        data.put("enabled", true);
        client.send(Envelope.of(MsgType.AUTH_BIND, data));
    }

    /**
     * 推 {@code auth.unbind}。
     *
     * @param uuid 玩家 UUID
     */
    public void unbind(String uuid) {
        ObjectNode data = Envelope.newData();
        data.put("uuid", uuid);
        client.send(Envelope.of(MsgType.AUTH_UNBIND, data));
    }

    /**
     * 重连后把本地所有绑定全量重推给后端，纠正断连期间的状态漂移。
     */
    public void republishAll() {
        Map<String, String> all = plugin.getGeoDatabaseManager().getAllBoundPlayers();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            bind(entry.getKey(), entry.getValue());
        }
    }
}
