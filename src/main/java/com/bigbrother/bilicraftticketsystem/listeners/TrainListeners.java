package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.entity.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrainListeners implements Listener {
    private static Map<String, Ticket> trainTicketInfo = new HashMap<>();

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
                return;
            }
            List<String> ticketTags = null;
            if (ticket != null) {
                CommonTagCompound nbt = mainHand.getCustomData();
                ticketTags = List.of(nbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","));
            }

            if (ticketTags != null) {
                // 列车为初始车 或 主手车票tag和列车tag一致，可以上车
                if (trainTags.contains(MainConfig.commonTrainTag) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags)) {
                    // 设置其他属性
                    trainProperties.apply(ticket.getProperties());
                    // 设置速度和tag

                    return;
                }
            }

            // 不能上车，显示快速购买信息
            event.setCancelled(true);
            player.sendMessage(Component.text("点我购买一张单程票", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.UNDERLINED, true)
                    .clickEvent(ClickEvent.callback(audience -> {

                    })));
        }
    }
}
