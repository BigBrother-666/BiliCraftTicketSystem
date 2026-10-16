package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

/**
 * 推送营收事件 {@code revenue.event}（购票 / 交通卡充值 / 交通卡刷卡乘车），经 {@link EventOutbox} 可靠送达。
 * <p>
 * 购票 / 充值与插件 {@code revenue_info} 表一一对应：{@link com.bigbrother.bilicraftticketsystem.database.service.RevenueService}
 * 每写一行收入流水就发一条事件；{@code allocations} 取自同一笔交易传给
 * {@link com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig#addIncome} 的分摊结果。
 */
public class RevenueEventPublisher {
    public static final String TYPE_TICKET = "ticket";
    public static final String TYPE_CARD_CHARGE = "card_charge";
    /** 交通卡刷卡乘车：卡内余额分摊给各铁路系统（插件 revenue_info 不记此类，见 RevenueService#recordCardRide）。 */
    public static final String TYPE_CARD_RIDE = "card_ride";

    private final EventOutbox outbox;

    public RevenueEventPublisher(EventOutbox outbox) {
        this.outbox = outbox;
    }

    /**
     * 发布一条营收事件。线程安全。
     *
     * @param type        {@link #TYPE_TICKET} / {@link #TYPE_CARD_CHARGE} / {@link #TYPE_CARD_RIDE}
     * @param playerUuid  玩家 UUID（可空）
     * @param playerName  玩家名
     * @param amount      金额
     * @param detail      明细（购票 / 刷卡乘车为「起点 -> 终点」，充值为交通卡 uuid）
     * @param allocations 系统 id -> 分摊金额（充值无分摊，可空）
     * @param time        交易时间（epoch ms）
     */
    public void publish(String type, String playerUuid, String playerName, double amount, String detail,
                        Map<String, Double> allocations, long time) {
        ObjectNode data = EventOutbox.newEventData();
        data.put("type", type);
        ObjectNode player = data.putObject("player");
        player.put("uuid", playerUuid == null ? "" : playerUuid.toLowerCase(Locale.ROOT));
        player.put("name", playerName == null ? "" : playerName);
        data.put("amount", amount);
        if (detail != null) {
            data.put("detail", detail);
        }
        ObjectNode alloc = data.putObject("allocations");
        if (allocations != null) {
            for (Map.Entry<String, Double> e : allocations.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    alloc.put(e.getKey(), e.getValue());
                }
            }
        }
        data.put("time", time);
        outbox.enqueue(MsgType.REVENUE_EVENT, data);
    }
}
