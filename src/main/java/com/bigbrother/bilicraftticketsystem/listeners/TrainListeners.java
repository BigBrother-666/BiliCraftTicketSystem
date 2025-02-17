package com.bigbrother.bilicraftticketsystem.listeners;

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
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionShowroute;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbar;
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

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.econ;
import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;
import static com.bigbrother.bilicraftticketsystem.ticket.BCTicket.getDiscountPrice;

public class TrainListeners implements Listener {
    private static final Map<String, CommonItemStack> trainTicketInfo = new HashMap<>();
    private static final Map<String, Set<UUID>> trainPlayerInfo = new HashMap<>();
    private static final Map<String, Set<UUID>> trainHintRecord = new HashMap<>();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMemberSeatEnterParalonRailway(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }
        // 如果是国铁，取消原版TC监听
        if (event.getMember().getGroup().getProperties().getTickets().contains(MainConfig.expressTicketName)) {
            event.setCancelled(true);
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

            // 是否是国铁列车
            if (!trainProperties.getTickets().contains(MainConfig.expressTicketName)) {
                return;
            }

            // 使用车票前的处理
            // 主手持车票？
            CommonItemStack mainHand = CommonItemStack.of(HumanHand.getItemInMainHand(player));
            Ticket ticket = TicketStore.getTicketFromItem(mainHand);
            Collection<String> trainTags = trainProperties.getTags();

            if (ticket == null && trainTags.contains(MainConfig.commonTrainTag)) {
                // 设置为无票车
                trainProperties.clearTickets();
                trainProperties.getHolder().onPropertiesChanged();
                event.setCancelled(false);
                return;
            }

            if (ticket != null && !ticket.getName().equals(MainConfig.expressTicketName)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("not-support-ticket", "不支持该车票")));
                event.setCancelled(true);
                return;
            }

            // 该玩家已经使用过车票，可直接上车
            if (trainPlayerInfo.containsKey(trainProperties.getTrainName()) && trainPlayerInfo.get(trainProperties.getTrainName()).contains(player.getUniqueId())) {
                event.setCancelled(false);
                return;
            }

            List<String> ticketTags = null;
            CommonTagCompound originNbt = mainHand.getCustomData();
            if (ticket != null) {
                ticketTags = List.of(originNbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));
            }

            if (!TicketStore.isTicketOwner(player, mainHand)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("owner-conflict", "不能使用其他玩家的车票")));
                event.setCancelled(true);
                return;
            }

            if (ticketTags != null && !ticketTags.get(0).isEmpty()) {
                // 检查是否是正确的站台
                // 寻找用来判断的tag
                String ticketStartStationTag = originNbt.getValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, "");
                if (!ticketStartStationTag.isEmpty()) {
                    for (String trainTag : trainTags) {
                        String[] split = trainTag.split("-");
                        if (split.length == 2) {
                            // 找到
                            if (!ticketStartStationTag.split("-")[0].equals(split[0]) || !ticketStartStationTag.split("-")[1].startsWith(split[1])) {
                                // 错误的站台
                                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("wrong-platform", "")));
                                event.setCancelled(true);
                                return;
                            }
                            break;
                        }
                    }
                }

                // 列车为初始车 或 主手车票tag和列车tag一致，可以上车
                if (trainTags.contains(MainConfig.commonTrainTag) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags)) {
                    // 设置skip
                    String[] split = MainConfig.skip.split(" ");
                    if (split.length == 3) {
                        trainProperties.setSkipOptions(SignSkipOptions.create(Integer.parseInt(split[1]), Integer.parseInt(split[2]), split[0]));
                    }

                    // 设置其他属性
                    trainProperties.apply(ticket.getProperties());
                    // 设置速度和tag
                    if (originNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0) > trainProperties.getSpeedLimit()) {
                        trainProperties.setSpeedLimit(originNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0));
                    }
                    trainProperties.clearTags();
                    for (String ticketTag : ticketTags) {
                        trainProperties.addTags(ticketTag);
                    }
                    trainTicketInfo.put(trainProperties.getTrainName(), mainHand);
                    trainProperties.getHolder().onPropertiesChanged();

                    // 检查是否是最后一次使用车票（动态设置车票最大使用次数需要）
                    if (originNbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0) > 0 && originNbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0) <= originNbt.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0) + 1) {
                        mainHand.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, MainConfig.maxUses - 1));
                        HumanHand.setItemInMainHand(player, mainHand.toBukkit());
                    }

                    // 所有车厢显示bossbar
                    for (MinecartMember<?> minecartMember : event.getMember().getGroup()) {
                        RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
                        String ticketName = originNbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, String.class, null);
                        if ((bossbar == null || bossbar.getRouteId() != null) && ticketName != null) {
                            bossbar = new RouteBossbar(ticketName);
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

                    player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("used", "成功使用一张（次）%s 车票").formatted(originNbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME))));
                    event.setCancelled(false);

                    // 原版TC逻辑
                    TicketStore.handleTickets(player, trainProperties);
                    return;
                }
            }

            // 不能上车，显示快速购买信息
            event.setCancelled(true);
            if (trainTicketInfo.containsKey(trainProperties.getTrainName())) {

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
                String ticketName = ticketNbt.getValue(BCTicket.KEY_TICKET_DISPLAY_NAME, String.class, "Unknown → Unknown") + " 单次票";
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
                if (ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1) < 0 || !originNbt.containsKey(BCTicket.KEY_TICKET_VERSION)) {
                    return;
                }

                double discountPrice = getDiscountPrice(player, 1, ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1.0));
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("quick-buy", "").formatted(discountPrice))
                        .decoration(TextDecoration.ITALIC, false)
                        .clickEvent(ClickEvent.callback(audience -> {
                            EconomyResponse r = econ.withdrawPlayer(player, discountPrice);

                            if (r.transactionSuccess()) {
                                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                                message.get("buy-success", "您成功花费 %.2f 购买了 %s")
                                                        .formatted(r.amount, ticketName))
                                        .decoration(TextDecoration.ITALIC, false));
                                if (!player.getInventory().addItem(trainTicket).isEmpty()) {
                                    // 背包满 车票丢到地上
                                    player.getWorld().dropItemNaturally(player.getLocation(), trainTicket);
                                }
                                // 记录log
                                Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<gold>[帕拉伦国有铁路车票系统] <green>玩家 %s 成功花费 %.2f 购买了 %s"
                                                .formatted(player.getName(), r.amount, ticketName)));
                                // 写入数据库
                                trainDatabaseManager.addTicketInfo(player.getName(), player.getUniqueId().toString(), r.amount, ticketNbt);
                            } else {
                                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                                message.get("buy-failure", "车票购买失败：%s")
                                                        .formatted(r.errorMessage))
                                        .decoration(TextDecoration.ITALIC, false));
                            }
                        })));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupRemove(GroupRemoveEvent event) {
        trainTicketInfo.remove(event.getGroup().getProperties().getTrainName());
        trainHintRecord.remove(event.getGroup().getProperties().getTrainName());
        trainPlayerInfo.remove(event.getGroup().getProperties().getTrainName());
    }
}
