package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.menu.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.List;

public class LocationItem extends AbstractItem {
    private final ItemStack itemStack;

    public LocationItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menu = MenuMain.getMenu(player);
        if (MenuLocation.getMenu(player, null).isStart()) {
            menu.getPlayerOption().setStartStation(itemStack.displayName());
        } else {
            menu.getPlayerOption().setEndStation(itemStack.displayName());
        }
        menu.setTickets(List.of());
        menu.open();
    }
}
