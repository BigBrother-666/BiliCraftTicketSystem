package com.bigbrother.bilicraftticketsystem.menu.items.location;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

public class LocationItem extends AbstractItem {
    protected final ItemStack itemStack;

    public LocationItem(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
        itemStack.setItemMeta(itemMeta);
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
            menu.getPlayerOption().setStartStation(itemStack.getItemMeta().displayName());
        } else {
            menu.getPlayerOption().setEndStation(itemStack.getItemMeta().displayName());
        }
        MenuFilter.getMenu(player).getFilterStations().clear();
        menu.clearTickets();
        menu.open();
    }
}
