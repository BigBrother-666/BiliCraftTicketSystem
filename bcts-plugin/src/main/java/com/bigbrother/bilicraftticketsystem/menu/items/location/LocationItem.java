package com.bigbrother.bilicraftticketsystem.menu.items.location;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
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
    /**
     * 车站真实站名（== geojson 站名）。设站时以此为准，<b>不</b>读图标 displayName，
     * 避免图标显示名（含颜色/样式）被当成站名造成寻路 / 数据不稳。
     */
    protected final String stationName;

    public LocationItem(ItemStack itemStack) {
        this(itemStack, null);
    }

    public LocationItem(ItemStack itemStack, String stationName) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        //noinspection deprecation
        itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        itemStack.setItemMeta(itemMeta);
        this.itemStack = itemStack;
        this.stationName = stationName;
    }

    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (stationName == null) {
            return;
        }
        MenuMain menu = MenuMain.getMenu(player);
        if (MenuLocation.getMenu(player).isStart()) {
            menu.getPlayerOption().setStartStationComponent(StationProvider.stationNameComponent(stationName));
        } else {
            menu.getPlayerOption().setEndStationComponent(StationProvider.stationNameComponent(stationName));
        }
        menu.clearTickets();
        menu.open();
    }
}
