package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CardListeners implements Listener {
    // 记录正在输入的玩家
    public static final Set<UUID> inputModePlayers = new HashSet<>();

    // 右键交通卡打开菜单
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        if (event.getHand() == EquipmentSlot.HAND && BCCard.isBctsCard(mainHand) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            Player player = event.getPlayer();
            BCCard card = BCCard.fromHeldItem(player);

            if (card != null) {
                MenuCard.getMenu(player).open();
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    // 接收玩家输入的充值金额
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent chatEvent) {
        Player player = chatEvent.getPlayer();
        if (inputModePlayers.contains(player.getUniqueId())) {
            chatEvent.setCancelled(true);
            String chatStr = chatEvent.getMessage().trim();

            BCCard card = BCCard.fromHeldItem(player);

            if (card != null) {
                try {
                    double chargeNum = Double.parseDouble(chatStr);
                    if (chargeNum <= 0) {
                        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("充值金额必须>0！请重新输入或点击取消按钮。")));
                        return;
                    }
                    if (!card.charge(chargeNum, player)) {
                        inputModePlayers.remove(player.getUniqueId());
                        MenuCard.getMenu(player).open();
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("不是有效的数字！请重新输入或点击取消按钮。")));
                }
            }
        }
    }
}
