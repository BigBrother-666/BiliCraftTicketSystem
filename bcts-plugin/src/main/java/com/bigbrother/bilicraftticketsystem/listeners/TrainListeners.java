package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.GroupRemoveEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPassFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

public class TrainListeners implements Listener {
    // 记录列车和车票的关系
    public static final Map<MinecartGroup, BCTransitPass> trainTicketInfo = new HashMap<>();
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
            if (isCommonTrain(group)) {
                return;
            }

            // 已经使用过车票，直接上车
            if (trainPlayerInfo.containsKey(group) && trainPlayerInfo.get(group).contains(player.getUniqueId())) {
                return;
            }

            // 优先使用主手车票/交通卡
            BCTransitPass transitPass = BCTransitPassFactory.fromHeldItem(player);
            boolean mainHand = true;

            // 其余格子有卡？
            if (transitPass == null) {
                transitPass = BCCard.findCardFromInventory(player);
                mainHand = false;
            }

            // 如果主手没有车票 或 找到交通卡且列车是普通车 或 交通卡不在主手且是初始车
            if (transitPass == null || (transitPass instanceof BCCard && (isCommonTrain(group) || !mainHand && isInitTrain(group)))) {
                // =============================== 无凭证 ===============================
                if (!trainTicketInfo.containsKey(group)) {
                    // 1.正常情况，第一个不持凭证上车
                    // 设置为普通车
                    trainProperties.clearTickets();
                    trainProperties.getHolder().onPropertiesChanged();
                    return;
                }
                // else 2.不持凭证上快速车，弹出快速购买
            } else {
                // =============================== 有凭证 ===============================
                // 验证坐车凭证是否可使用
                if (!transitPass.verify(player, group)) {
                    event.setCancelled(true);
                } else {
                    // 标记列车为快速车
                    trainTicketInfo.putIfAbsent(group, transitPass);

                    // 修改坐车凭证属性
                    transitPass.useTransitPass(player);

                    // 记录已经使用坐车凭证，再次上车不需要坐车凭证
                    if (trainPlayerInfo.containsKey(trainProperties.getHolder())) {
                        trainPlayerInfo.get(group).add(player.getUniqueId());
                    } else {
                        HashSet<UUID> set = new HashSet<>();
                        set.add(player.getUniqueId());
                        trainPlayerInfo.put(group, set);
                    }

                    // 应用列车属性
                    transitPass.applyTo(player, group);
                    return;
                }
            }

            // 不能上车，显示快速购买信息
            event.setCancelled(true);
            if (trainTicketInfo.containsKey(group)) {
                showQuickBuy(group, player);
            }
        }
    }

    private void showQuickBuy(MinecartGroup group, Player player) {
        // 防止提示出现多次
        if (trainHintRecord.containsKey(group)) {
            if (!trainHintRecord.get(group).add(player.getUniqueId())) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                        CommonUtils.mmStr2Component(message.get("forbidden-get-on", "")).decoration(TextDecoration.ITALIC, false)
                ));
                return;
            }
        } else {
            trainHintRecord.put(group, new HashSet<>());
            trainHintRecord.get(group).add(player.getUniqueId());
        }

        // 检票失败提示
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                CommonUtils.mmStr2Component(message.get("ticket-check-failed", "")).decoration(TextDecoration.ITALIC, false)
        ));

        // 获取单程票
        BCTicket cloned = trainTicketInfo.get(group).getNewSingleTicket(player);
        if (cloned == null) {
            return;
        }
        ItemStack ticket = cloned.getItemStack();
        List<Component> ticketLore = ticket.getItemMeta().lore();

        // 发送车票信息
        if (ticketLore != null) {
            for (Component lore : ticketLore) {
                player.sendMessage(lore);
            }
        }

        // 单程票价值 < 0 不显示快速购票
        if (CommonItemStack.of(ticket).getCustomData().getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, -1.0) < 0) {
            return;
        }

        double discountPrice = cloned.getPrice();
        // 发送购买按钮
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(CommonUtils.mmStr2Component(message.get("quick-buy", "").formatted(discountPrice)))
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.callback(audience -> cloned.purchase())));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupRemove(GroupRemoveEvent event) {
        trainTicketInfo.remove(event.getGroup());
        trainHintRecord.remove(event.getGroup());
        trainPlayerInfo.remove(event.getGroup());
    }

    // 判断是初始车（没人上过车）
    private boolean isInitTrain(MinecartGroup group) {
        // 不管是普通车还是快速车，上车后tc的ticket属性都会清除
        return group.getProperties().getTickets().contains(MainConfig.expressTicketName);
    }

    // 判断是普通车
    private boolean isCommonTrain(MinecartGroup group) {
        // trainTicketInfo没有不一定就是普通车，还有可能是初始车
        return !trainTicketInfo.containsKey(group) && !isInitTrain(group);
    }

    // 判断是快速车
    private boolean isExpressTrain(MinecartGroup group) {
        return !isCommonTrain(group);
    }
}
