package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcTrainIdProperty;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 周期采集列车遥测并推送 {@code realtime.trains}（见 docs/PLUGIN_ADDENDUM.md §五）。
 * <p>
 * 在<b>主线程</b>遍历全局列车集合，为每列车构建遥测 DTO，汇总成一个数组一帧推送。
 * 受 telemetry.enabled 约束。
 */
public class TrainTelemetryTask extends BukkitRunnable {
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final BiliCraftTicketSystem plugin;
    private final WebLinkClient client;

    public TrainTelemetryTask(BiliCraftTicketSystem plugin, WebLinkClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    @Override
    public void run() {
        if (!client.isConnected() || !MapConfig.isTelemetryEnabled()) {
            return;
        }
        ArrayNode trains = Envelope.MAPPER.createArrayNode();
        for (MinecartGroup group : MinecartGroupStore.getGroups().cloneAsIterable()) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            ObjectNode dto = buildTrainDto(group);
            if (dto != null) {
                trains.add(dto);
            }
        }
        ObjectNode data = Envelope.MAPPER.createObjectNode();
        data.set("trains", trains);
        client.send(Envelope.of(MsgType.REALTIME_TRAINS, data));
    }

    /**
     * 为一列车构建遥测 DTO；按 {@code include-empty-trains} 决定是否跳过空车，返回 null 表示跳过。
     */
    private ObjectNode buildTrainDto(MinecartGroup group) {
        String lineIdProp = BcLineIdProperty.read(group);
        if (lineIdProp == null || lineIdProp.isEmpty()) {
            // 忽略不是bcspawn发的车
            return null;
        }

        MinecartMember<?> head = group.head();
        if (head == null) {
            return null;
        }
        // 乘客名（所有车厢）
        ArrayNode passengers = Envelope.MAPPER.createArrayNode();
        for (MinecartMember<?> member : group) {
            for (Player p : member.getEntity().getPlayerPassengers()) {
                passengers.add(p.getName());
            }
        }
        if (passengers.isEmpty() && !MapConfig.isTelemetryIncludeEmptyTrains()) {
            return null;
        }

        Location loc = head.getEntity().getLocation();
        ObjectNode dto = Envelope.MAPPER.createObjectNode();
        dto.put("trainId", BcTrainIdProperty.ensure(group));
        dto.put("world", loc.getWorld() == null ? "" : loc.getWorld().getName());

        ObjectNode headNode = dto.putObject("head");
        headNode.put("x", loc.getX());
        headNode.put("y", loc.getY());
        headNode.put("z", loc.getZ());
        headNode.put("yaw", loc.getYaw());
        TrainProperties properties = group.getProperties();
        dto.put("speedKph", CommonUtils.mpt2Kph(properties.hasHolder() ? properties.getHolder().head().getRealSpeedLimited() : 0.0));
        dto.put("cartCount", group.size());
        dto.set("passengers", passengers);

        boolean express = TrainListeners.trainTicketInfo.containsKey(group);
        dto.put("express", express);

        String lineId = BCTransitPass.getTrainLineId(group);
        if (lineId != null && !lineId.isEmpty()) {
            dto.put("lineId", lineId);
        }

        if (express) {
            BCTransitPass pass = TrainListeners.trainTicketInfo.get(group);
            GeoRoutePath path = pass == null ? null : pass.getPathInfo();
            if (path != null) {
                dto.put("destination", path.getEndStationName());
                ArrayNode routeNodeIds = dto.putArray("routeNodeIds");
                for (GeoNode node : path.getNodes()) {
                    routeNodeIds.add(node.getId());
                }
            }
        }
        return dto;
    }
}
