package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

public class WarnItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(Utils.loadItemFromFile("warn"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                Utils.mmStr2Component(MainConfig.message.get("wiki", "[这里什么也没有]"))
        ));
        MenuMain.getMenu(player).close();
    }
}
