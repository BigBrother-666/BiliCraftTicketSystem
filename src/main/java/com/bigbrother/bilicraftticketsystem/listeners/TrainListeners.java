package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.GroupRemoveEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.signactions.SignActionShowroute;
import com.bigbrother.bilicraftticketsystem.addon.signactions.component.RouteBossbar;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

public class TrainListeners implements Listener {
    // 记录列车和车票的关系
    public static final Map<MinecartGroup, BCTicket> trainTicketInfo = new HashMap<>();
    // 记录已经使用车票上车的玩家
    private static final Map<MinecartGroup, Set<UUID>> trainPlayerInfo = new HashMap<>();
    // 记录快速购票信息显示情况，防止重复显示
    private static final Map<MinecartGroup, Set<UUID>> trainHintRecord = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMemberSeatEnter(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }
        MinecartMember<?> newMember = event.getMember();
        Entity passenger = event.getEntity();
        if (event.isPlayerInitiated() && passenger instanceof Player player && newMember != null) {
            CartProperties prop = newMember.getProperties();
            MinecartGroup group = newMember.getGroup();
            if (!prop.getPlayersEnter() || newMember.isUnloaded()) {
                return;
            }
            if (prop.getCanOnlyOwnersEnter() && !prop.hasOwnership(player)) {
                return;
            }

            TrainProperties trainProperties = group.getProperties();

            // 普通车直接返回
            if (!trainProperties.getTickets().contains(MainConfig.expressTicketName) && !trainTicketInfo.containsKey(group)) {
                return;
            }

            // 已经使用过车票，直接上车
            if (trainPlayerInfo.containsKey(group) && trainPlayerInfo.get(group).contains(player.getUniqueId())) {
                return;
            }

            BCTicket ticket = BCTicket.fromItemStack(HumanHand.getItemInMainHand(player), player);
            Collection<String> trainTags = trainProperties.getTags();
            // 主手持车票？
            if (ticket == null) {
                // =============================== 无票 ===============================
                if (!trainTicketInfo.containsKey(group)) {
                    // 1.正常情况，第一个不持票上车
                    // 设置为无票车
                    trainProperties.clearTickets();
                    trainProperties.getHolder().onPropertiesChanged();
                    return;
                }
                // else 2.不持票上快速车
            } else {
                // =============================== 有票 ===============================
                CommonItemStack commonTicket = ticket.getTicket();
                CommonTagCompound nbt = commonTicket.getCustomData();
                List<String> ticketTags = List.of(nbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));

                // 其他玩家的车票
                if (!ticket.isTicketOwner(player)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("owner-conflict", "不能使用其他玩家的车票")));
                    event.setCancelled(true);
                    return;
                }

                // 过期的车票
                if (ticket.isTicketExpired()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("expired-ticket", "车票已过期")));
                    event.setCancelled(true);
                    return;
                }

                // 不支持的车票
                if (ticketTags.get(0).isEmpty()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("not-support-ticket", "不支持该车票")));
                    event.setCancelled(true);
                    return;
                }

                // 旧版本车票
                if (nbt.getValue(BCTicket.KEY_TICKET_PLUGIN, "").equals("TrainCarts")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("old-ticket", "旧版车票已禁用")));
                    event.setCancelled(true);
                    return;
                }

                // ================= 使用正确车票上车 =================
                // 检查站台是否正确
                if (verifyPlatform(nbt.getValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, ""), trainTags, player)) {
                    event.setCancelled(true);
                    return;
                }

                // 列车为初始车 或 主手车票tag和列车tag一致，可以上车
                if (trainTags.contains(MainConfig.commonTrainTag) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags)) {
                    trainProperties.clearTickets();
                    trainTicketInfo.put(group, ticket);

                    // 设置skip
                    String[] split = MainConfig.skip.split(" ");
                    if (split.length == 3) {
                        trainProperties.setSkipOptions(SignSkipOptions.create(Integer.parseInt(split[1]), Integer.parseInt(split[2]), split[0]));
                    }

                    // 设置speed tag
                    trainProperties.setSpeedLimit(nbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0));
                    trainProperties.setTags(ticketTags.toArray(new String[0]));
                    trainProperties.getHolder().onPropertiesChanged();

                    // 使用次数+1
                    ticket.useTicket();

                    // 所有车厢显示bossbar
                    for (MinecartMember<?> minecartMember : group) {
                        RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
                        String ticketName = nbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, String.class, null);
                        if ((bossbar == null || bossbar.getRouteId() != null) && ticketName != null) {
                            bossbar = new RouteBossbar(ticketName, ticketTags.size());
                            SignActionShowroute.bossbarMapping.put(minecartMember, bossbar);
                        }
                    }

                    // 记录已经使用车票上车，再次上车不需要车票
                    if (trainPlayerInfo.containsKey(trainProperties.getHolder())) {
                        trainPlayerInfo.get(group).add(player.getUniqueId());
                    } else {
                        HashSet<UUID> set = new HashSet<>();
                        set.add(player.getUniqueId());
                        trainPlayerInfo.put(group, set);
                    }

                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("used", "成功使用一张（次）%s 车票").formatted(nbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME))));
                    return;
                }
            }

            // 不能上车，显示快速购买信息
            event.setCancelled(true);
            if (trainTicketInfo.containsKey(group)) {
                showQuickBuy(group, player, trainTags);
            }
        }
    }

    private boolean verifyPlatform(String pTag, Collection<String> trainTags, Player player) {
        MermaidGraph.Node ticketStartStationNode = TrainRoutes.graph.getNodeFromPtag(pTag);
        if (ticketStartStationNode != null) {
            for (String trainTag : trainTags) {
                MermaidGraph.Node bcSpawnNode = TrainRoutes.graph.getNodeFromPtag(trainTag);
                if (bcSpawnNode != null) {
                    // 找到
                    if (!ticketStartStationNode.getTag().equals(bcSpawnNode.getTag()) ||
                            !ticketStartStationNode.getRailwayDirection().equals(bcSpawnNode.getRailwayDirection())) {
                        // 错误的站台
                        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("wrong-platform", "")));
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private void showQuickBuy(MinecartGroup group, Player player, Collection<String> trainTags) {
        // 防止提示出现多次
        if (trainHintRecord.containsKey(group)) {
            if (!trainHintRecord.get(group).add(player.getUniqueId())) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("forbidden-get-on", "")).decoration(TextDecoration.ITALIC, false));
                return;
            }
        } else {
            trainHintRecord.put(group, new HashSet<>());
            trainHintRecord.get(group).add(player.getUniqueId());
        }

        BCTicket originTicket = trainTicketInfo.get(group);

        // 没有使用过车票且中途试图上车的，不发送快速购票信息
        if (!trainTags.containsAll(List.of(originTicket.getTicket().getCustomData().getValue(BCTicket.KEY_TICKET_TAGS, "").split(",")))) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("forbidden-get-on", "")).decoration(TextDecoration.ITALIC, false));
            return;
        }

        // 检票失败提示
        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("check-failed", "")).decoration(TextDecoration.ITALIC, false));

        // 获取单程票
        BCTicket cloned = originTicket.getNewSingleTicket(player);
        if (cloned == null) {
            return;
        }
        CommonItemStack commonTicket = cloned.getTicket();
        List<Component> ticketLore = commonTicket.toBukkit().getItemMeta().lore();

        // 发送车票信息
        if (ticketLore != null) {
            for (Component lore : ticketLore) {
                player.sendMessage(lore);
            }
        }

        // 单程票价值 < 0 或 没有version 不显示快速购票
        if (commonTicket.getCustomData().getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1.0) < 0) {
            return;
        }

        double discountPrice = cloned.getDiscountPrice();
        // 发送购买按钮
        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("quick-buy", "").formatted(discountPrice))
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.callback(audience -> cloned.purchase())));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupRemove(GroupRemoveEvent event) {
        trainTicketInfo.remove(event.getGroup());
        trainHintRecord.remove(event.getGroup());
        trainPlayerInfo.remove(event.getGroup());
    }
}
