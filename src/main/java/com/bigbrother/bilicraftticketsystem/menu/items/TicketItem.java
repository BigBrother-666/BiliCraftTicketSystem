package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.econ;
import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

public class TicketItem extends AbstractItem {
    private final BCTicket ticket;

    public TicketItem(BCTicket ticket) {
        this.ticket = ticket;
    }

    @Override
    public ItemProvider getItemProvider() {
        if (ticket == null || !TicketStore.isTicketItem(ticket.getTicket())) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta barrierMeta = barrier.getItemMeta();
            barrierMeta.displayName(Component.text("所选两站暂时没有直达方案！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            barrier.setItemMeta(barrierMeta);
            return new ItemBuilder(barrier);
        }
        return new ItemBuilder(ticket.getTicket());
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (ticket == null || !TicketStore.isTicketItem(ticket.getTicket())) {
            return;
        }

        if (!clickType.isCreativeAction()) {
            EconomyResponse r = econ.withdrawPlayer(player, ticket.getTotalPrice());
            if (r.transactionSuccess()) {
                ticket.give();

                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(message.get("buy-success", "您成功花费 %.2f 购买了 %s")
                                .formatted(r.amount, ticket.getItemName())).decoration(TextDecoration.ITALIC, false));
                // 记录log
                Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize(
                        "<gold>[帕拉伦国有铁路车票系统] <green>玩家 %s 成功花费 %.2f 购买了 %s"
                                .formatted(player.getName(), r.amount, ticket.getItemName())));
                // 记录到数据库
                trainDatabaseManager.addTicketInfo(player.getName(), player.getUniqueId().toString(), r.amount, CommonItemStack.of(ticket.getTicket()).getCustomData());
            } else {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                message.get("buy-failure", "车票购买失败：%s")
                                        .formatted(r.errorMessage))
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            ticket.give();
        }
        MenuMain.getMenu(player).close();
    }

    public void updateLore() {
        if (ticket != null) {
            ticket.updateTicketInfo();
        }
    }
}
