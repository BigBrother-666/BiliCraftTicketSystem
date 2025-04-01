package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;

public class TicketbgItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player viewer) {
        return new ItemBuilder(Utils.loadItemFromFile("ticketbg"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        TicketbgInfo info = trainDatabaseManager.getCurrTicketbgInfo(player.getUniqueId().toString());
        if (info != null) {
            MenuTicketbg.getTicketbgUsageMapping().put(player.getUniqueId(), info);
        }
        MenuTicketbg.getMenu(player).open();
    }
}
