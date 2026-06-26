package com.bigbrother.bilicraftticketsystem.web.outbound;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcTrainIdProperty;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.MsgType;
import com.bigbrother.bilicraftticketsystem.web.WebLinkClient;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.entity.Player;

/**
 * Publishes ride node events when a train physically triggers platform/bcswitcher signs.
 */
public class RideEventPublisher {
    private final WebLinkClient client;

    public RideEventPublisher(WebLinkClient client) {
        this.client = client;
    }

    public void publish(MinecartGroup group, String nodeId, String stationName) {
        if (group == null || nodeId == null || nodeId.isEmpty() || !client.isConnected()) {
            return;
        }
        String trainId = BcTrainIdProperty.ensure(group);
        if (trainId == null || trainId.isEmpty()) {
            return;
        }

        boolean express = TrainListeners.trainTicketInfo.containsKey(group);
        ObjectNode data = Envelope.newData();
        data.put("trainId", trainId);
        data.put("trainType", express ? "express" : "common");
        data.put("express", express);
        data.put("nodeId", nodeId);
        if (stationName != null && !stationName.isEmpty()) {
            data.put("stationName", stationName);
        }
        String lineId = BCTransitPass.getTrainLineId(group);
        if (lineId != null && !lineId.isEmpty()) {
            data.put("lineId", lineId);
        }

        ArrayNode passengers = data.putArray("passengers");
        for (MinecartMember<?> member : group) {
            if (member.getEntity() == null) {
                continue;
            }
            for (Player player : member.getEntity().getPlayerPassengers()) {
                ObjectNode p = passengers.addObject();
                p.put("uuid", player.getUniqueId().toString());
                p.put("name", player.getName());
            }
        }

        client.send(Envelope.of(MsgType.RIDE_EVENT, data));
    }

    public void publishPayment(MinecartGroup group, Player player, BCTransitPass pass) {
        if (group == null || player == null || pass == null || !client.isConnected()) {
            return;
        }
        GeoRoutePath path = pass.getPathInfo();
        if (path == null) {
            return;
        }
        String trainId = BcTrainIdProperty.ensure(group);
        if (trainId == null || trainId.isEmpty()) {
            return;
        }

        ObjectNode data = Envelope.newData();
        data.put("trainId", trainId);
        data.put("trainType", "express");
        data.put("express", true);
        data.put("startStation", path.getStartStationName());
        data.put("endStation", path.getEndStationName());
        data.put("distance", path.getDistance());
        data.put("paidFare", pass.getRideHistoryFare());
        data.put("paidAt", System.currentTimeMillis());

        ObjectNode playerNode = data.putObject("player");
        playerNode.put("uuid", player.getUniqueId().toString());
        playerNode.put("name", player.getName());

        ArrayNode routeNodeIds = data.putArray("routeNodeIds");
        for (GeoNode node : path.getNodes()) {
            routeNodeIds.add(node.getId());
        }

        client.send(Envelope.of(MsgType.RIDE_PAYMENT, data));
    }
}
