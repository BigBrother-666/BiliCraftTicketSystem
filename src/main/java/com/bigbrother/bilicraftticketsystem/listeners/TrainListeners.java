package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.GroupRemoveEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.addon.signactions.SignActionShowroute;
import com.bigbrother.bilicraftticketsystem.addon.signactions.component.RouteBossbar;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.*;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.getDiscountPrice;

public class TrainListeners implements Listener {
    private static final Map<String, CommonItemStack> trainTicketInfo = new HashMap<>();
    private static final Map<String, Set<UUID>> trainPlayerInfo = new HashMap<>();
    private static final Map<String, Set<UUID>> trainHintRecord = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMemberSeatEnterParalonRailway(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }

        MinecartMember<?> newMember = event.getMember();
        Entity passenger = event.getEntity();
        if (event.isPlayerInitiated() && passenger instanceof Player && newMember != null) {
            Player player = ((Player) passenger).getPlayer();
            if (player == null) {
                return;
            }
            CartProperties prop = newMember.getProperties();
            if (!prop.getPlayersEnter() || newMember.isUnloaded()) {
                return;
            }
            if (prop.getCanOnlyOwnersEnter() && !prop.hasOwnership(player)) {
                return;
            }

            // 如果是国铁，跳过原版TC监听
            if (event.getMember().getGroup().getProperties().getTickets().contains(MainConfig.expressTicketName)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMemberSeatEnter(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }
        MinecartMember<?> newMember = event.getMember();
        Entity passenger = event.getEntity();
        if (event.isPlayerInitiated() && passenger instanceof Player && newMember != null) {
            Player player = ((Player) passenger).getPlayer();
            if (player == null) {
                return;
            }
            CartProperties prop = newMember.getProperties();
            if (!prop.getPlayersEnter() || newMember.isUnloaded()) {
                return;
            }
            if (prop.getCanOnlyOwnersEnter() && !prop.hasOwnership(player)) {
                return;
            }

            TrainProperties trainProperties = newMember.getGroup().getProperties();

            // 是否是国铁直达车
            // 或 设置为无票车后
            if (!trainProperties.getTickets().contains(MainConfig.expressTicketName)) {
                return;
            }

            // 已经使用过车票，直接上车
            if (trainPlayerInfo.containsKey(trainProperties.getTrainName()) && trainPlayerInfo.get(trainProperties.getTrainName()).contains(player.getUniqueId())) {
                event.setCancelled(false);
                return;
            }

            // 使用车票前的处理
            // 主手持车票？
            CommonItemStack mainHand = CommonItemStack.of(HumanHand.getItemInMainHand(player));
            Ticket ticket = TicketStore.getTicketFromItem(mainHand);
            Collection<String> trainTags = trainProperties.getTags();

            if (ticket == null) {
                // =============================== 无票 ===============================
                // 1.正常情况
                if (trainTags.contains(MainConfig.commonTrainTag)) {
                    // 设置为无票车
                    trainProperties.clearTickets();
                    trainProperties.getHolder().onPropertiesChanged();
                    event.setCancelled(false);
                    return;
                }
                //
            } else {
                // =============================== 有票 ===============================
                CommonTagCompound nbt = mainHand.getCustomData();
                List<String> ticketTags = List.of(nbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));

                // 其他玩家的车票
                if (!TicketStore.isTicketOwner(player, mainHand)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("owner-conflict", "不能使用其他玩家的车票")));
                    event.setCancelled(true);
                    return;
                }

                // 过期的车票
                if (TicketStore.isTicketExpired(mainHand)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("expired-ticket", "车票已过期")));
                    event.setCancelled(true);
                    return;
                }

                // 不支持的车票
                if (!ticket.getName().equals(MainConfig.expressTicketName) || ticketTags.get(0).isEmpty()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("not-support-ticket", "不支持该车票")));
                    event.setCancelled(true);
                    return;
                }

                // 旧版本车票
                if (!nbt.containsKey(BCTicket.KEY_TICKET_VERSION)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("old-ticket", "旧版车票已禁用")));
                    event.setCancelled(true);
                    return;
                }

                // ================= 使用正确车票上车 =================
                // 检查站台是否正确
                if (verifyPlatform(nbt, trainTags, player)) {
                    event.setCancelled(true);
                    return;
                }

                // 列车为初始车 或 主手车票tag和列车tag一致，可以上车
                if (trainTags.contains(MainConfig.commonTrainTag) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags)) {
                    // 设置skip
                    String[] split = MainConfig.skip.split(" ");
                    if (split.length == 3) {
                        trainProperties.setSkipOptions(SignSkipOptions.create(Integer.parseInt(split[1]), Integer.parseInt(split[2]), split[0]));
                    }

                    ConfigurationNode ticketTrainProperties = ticket.getProperties().clone();
                    ticketTrainProperties.remove("carts");

                    // 设置速度
                    if (nbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0) > trainProperties.getSpeedLimit()) {
                        ticketTrainProperties.set("speedLimit", nbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0));
                    }

                    // 设置tag
                    ticketTrainProperties.set("tags", ticketTags);
                    trainProperties.apply(ticketTrainProperties);

                    trainTicketInfo.put(trainProperties.getTrainName(), mainHand);
                    trainProperties.getHolder().onPropertiesChanged();

                    // 使用次数+1
                    mainHand.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, tag.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0) + 1));
                    // 检查是否达到最大次数
                    if (nbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0) > 0 && nbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0) <= nbt.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0) + 1) {
                        mainHand.setAmount(mainHand.getAmount() - 1);
                    }
                    HumanHand.setItemInMainHand(player, mainHand.toBukkit());

                    // 所有车厢显示bossbar
                    for (MinecartMember<?> minecartMember : event.getMember().getGroup()) {
                        RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
                        String ticketName = nbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, String.class, null);
                        if ((bossbar == null || bossbar.getRouteId() != null) && ticketName != null) {
                            bossbar = new RouteBossbar(ticketName, ticketTags.size());
                            SignActionShowroute.bossbarMapping.put(minecartMember, bossbar);
                        }
                    }

                    // 记录已经使用车票上车，再次上车不需要车票
                    if (trainPlayerInfo.containsKey(trainProperties.getTrainName())) {
                        trainPlayerInfo.get(trainProperties.getTrainName()).add(player.getUniqueId());
                    } else {
                        HashSet<UUID> set = new HashSet<>();
                        set.add(player.getUniqueId());
                        trainPlayerInfo.put(trainProperties.getTrainName(), set);
                    }

                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("used", "成功使用一张（次）%s 车票").formatted(nbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME))));
                    event.setCancelled(false);
                    return;
                }
            }

            // 不能上车，显示快速购买信息
            event.setCancelled(true);
            if (trainTicketInfo.containsKey(trainProperties.getTrainName())) {
                showQuickBuy(trainProperties, player, trainTags);
            }
        }
    }

    private static boolean verifyPlatform(CommonTagCompound nbt, Collection<String> trainTags, Player player) {
        MermaidGraph.Node ticketStartStationNode = TrainRoutes.graph.getNodeFromPtag(nbt.getValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, ""));
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

    private static void showQuickBuy(TrainProperties trainProperties, Player player, Collection<String> trainTags) {
        // 防止提示出现多次
        if (trainHintRecord.containsKey(trainProperties.getTrainName())) {
            if (!trainHintRecord.get(trainProperties.getTrainName()).add(player.getUniqueId())) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("forbidden-get-on", "")).decoration(TextDecoration.ITALIC, false));
                return;
            }
        } else {
            trainHintRecord.put(trainProperties.getTrainName(), new HashSet<>());
            trainHintRecord.get(trainProperties.getTrainName()).add(player.getUniqueId());
        }

        CommonItemStack originTicket = trainTicketInfo.get(trainProperties.getTrainName());

        // 没有使用过车票且中途试图上车的，不发送快速购票信息
        if (!trainTags.containsAll(List.of(originTicket.getCustomData().getValue(BCTicket.KEY_TICKET_TAGS, "").split(",")))) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("forbidden-get-on", "")).decoration(TextDecoration.ITALIC, false));
            return;
        }

        CommonItemStack cloned = BCTicket.deepCopy(player, originTicket);
        ItemStack trainTicket = cloned.toBukkit();
        CommonTagCompound ticketNbt = cloned.getCustomData();
        ItemMeta itemMeta = trainTicket.getItemMeta();

        // 更新车票名
        String ticketName = "%s → %s 单次票".formatted(ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, "UnKnown"), ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, "UnKnown"));
        itemMeta.displayName(Component.text(ticketName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        // 更新车票使用次数
        cloned.updateCustomData(tag -> {
            tag.putValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0);
            tag.putValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 1);
        });

        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("check-failed", "")).decoration(TextDecoration.ITALIC, false));
        if (itemMeta.lore() == null) {
            return;
        }
        for (Component lore : itemMeta.lore()) {
            player.sendMessage(lore);
        }
        trainTicket.setItemMeta(itemMeta);

        // 单程票价值 < 0 或 没有version 不显示快速购票
        if (ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1.0) < 0) {
            return;
        }

        double discountPrice = getDiscountPrice(player, 1, ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1.0));
        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("quick-buy", "").formatted(discountPrice))
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.callback(audience -> {
                    EconomyResponse r = plugin.getEcon().withdrawPlayer(player, discountPrice);

                    if (r.transactionSuccess()) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("buy-success", "您成功花费 %.2f 购买了 %s").formatted(r.amount, ticketName)).decoration(TextDecoration.ITALIC, false));
                        if (!player.getInventory().addItem(trainTicket).isEmpty()) {
                            // 背包满 车票丢到地上
                            player.getWorld().dropItemNaturally(player.getLocation(), trainTicket);
                        }
                        // 记录log
                        Bukkit.getConsoleSender().sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("玩家 %s 成功花费 %.2f 购买了 %s".formatted(player.getName(), r.amount, ticketName), NamedTextColor.GREEN)));
                        // 写入数据库
                        plugin.getTrainDatabaseManager().addTicketInfo(player.getName(), player.getUniqueId().toString(), r.amount, ticketNbt);
                    } else {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("buy-failure", "车票购买失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false));
                    }
                })));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupRemove(GroupRemoveEvent event) {
        trainTicketInfo.remove(event.getGroup().getProperties().getTrainName());
        trainHintRecord.remove(event.getGroup().getProperties().getTrainName());
        trainPlayerInfo.remove(event.getGroup().getProperties().getTrainName());
    }
}
