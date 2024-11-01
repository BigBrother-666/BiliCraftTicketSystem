package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.GroupRemoveEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.econ;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

public class TrainListeners implements Listener {
    private static final Map<String, CommonItemStack> trainTicketInfo = new HashMap<>();
    private static final Map<String, Set<UUID>> trainHintRecord = new HashMap<>();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMemberSeatEnter(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }
        MinecartMember<?> new_member = event.getMember();
        Entity passenger = event.getEntity();
        if (event.isPlayerInitiated() && passenger instanceof Player && new_member != null) {
            Player player = ((Player) passenger).getPlayer();
            if (player == null) {
                return;
            }
            CartProperties prop = new_member.getProperties();
            if (!prop.getPlayersEnter()) {
                return;
            }
            if (prop.getCanOnlyOwnersEnter() && !prop.hasOwnership(player)) {
                return;
            }
            // 使用车票前的处理
            // 主手持车票？
            CommonItemStack mainHand = CommonItemStack.of(HumanHand.getItemInMainHand(player));
            Ticket ticket = TicketStore.getTicketFromItem(mainHand);
            TrainProperties trainProperties = new_member.getGroup().getProperties();
            Collection<String> trainTags = trainProperties.getTags();
            if (!trainProperties.getTickets().contains("/")) {
                return;
            }
            if (ticket == null && trainTags.contains(MainConfig.commonTrainTag)) {
                // 设置为无票车
                trainProperties.clearTickets();
                trainProperties.getHolder().onPropertiesChanged();
                return;
            }

            List<String> ticketTags = null;
            CommonTagCompound nbt = mainHand.getCustomData();
            if (ticket != null) {
                ticketTags = List.of(nbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));
            }

            if (ticketTags != null) {
                // 列车为初始车 或 主手车票tag和列车tag一致，可以上车
                if (trainTags.contains(MainConfig.commonTrainTag) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags)) {
                    // 设置其他属性
                    trainProperties.apply(ticket.getProperties());
                    // 设置速度和tag
                    if (nbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0) > trainProperties.getSpeedLimit()) {
                        trainProperties.setSpeedLimit(nbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, 4.0));
                    }
                    trainProperties.clearTags();
                    for (String ticketTag : ticketTags) {
                        trainProperties.addTags(ticketTag);
                    }
                    trainTicketInfo.put(trainProperties.getTrainName(), mainHand);
                    trainProperties.getHolder().onPropertiesChanged();

                    if (nbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, 0) <= nbt.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0) + 1) {
                        mainHand.updateCustomData(tag -> tag.putValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, MainConfig.maxUses - 1));
                        HumanHand.setItemInMainHand(player, mainHand.toBukkit());
                    }
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
                    }
                    return;
                } else {
                    trainHintRecord.put(trainProperties.getTrainName(), new HashSet<>());
                    trainHintRecord.get(trainProperties.getTrainName()).add(player.getUniqueId());
                }

                CommonItemStack cloned = BCTicket.deepCopy(player, trainTicketInfo.get(trainProperties.getTrainName()));
                ItemStack trainTicket = cloned.toBukkit();
                CommonTagCompound ticketNbt = cloned.getCustomData();
                ItemMeta itemMeta = trainTicket.getItemMeta();

                // 更新车票名
                String ticketName = PlainTextComponentSerializer.plainText().serialize(itemMeta.displayName()).split(" ")[0] + " 单次票";
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

                player.sendMessage(MiniMessage.miniMessage().deserialize(message.get("quick-buy", "").formatted(ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, 0.0)))
                        .decoration(TextDecoration.ITALIC, false)
                        .clickEvent(ClickEvent.callback(audience -> {
                            EconomyResponse r = econ.withdrawPlayer(player, ticketNbt.getValue(BCTicket.KEY_TICKET_ORIGIN_PRICE, 0.0));


                            if (r.transactionSuccess()) {
                                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                                message.get("buy-success", "您成功花费 %.2f 购买了 %s")
                                                        .formatted(r.amount, ticketName))
                                        .decoration(TextDecoration.ITALIC, false));
                                if (!player.getInventory().addItem(trainTicket).isEmpty()) {
                                    // 背包满 车票丢到地上
                                    player.getWorld().dropItemNaturally(player.getLocation(), trainTicket);
                                }
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
    }
}
