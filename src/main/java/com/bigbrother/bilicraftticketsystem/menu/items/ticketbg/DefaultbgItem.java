package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;

public class DefaultbgItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player viewer) {
        return new ItemBuilder(Utils.loadItemFromFile("defaultbg"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        TicketbgInfo ticketbgInfo = MenuTicketbg.getTicketbgUsageMapping().get(player.getUniqueId());
        if (ticketbgInfo != null) {
            trainDatabaseManager.updateUsageTicketbg(null, player.getUniqueId().toString());
            MenuTicketbg.getTicketbgUsageMapping().remove(player.getUniqueId());
            MenuTicketbg.updateAllWindows();
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("设置默认背景图成功", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("已经是默认背景图！", NamedTextColor.GREEN)));
        }
    }
}
