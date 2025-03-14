package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.menu.MenuFilter;
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
        if (MenuLocation.getMenu(player).isStart()) {
            menu.getPlayerOption().setStartStation(itemStack.displayName());
        } else {
            menu.getPlayerOption().setEndStation(itemStack.displayName());
        }
        MenuFilter.getMenu(player).getFilterStations().clear();
        menu.clearTickets();
        menu.open();
    }
}
