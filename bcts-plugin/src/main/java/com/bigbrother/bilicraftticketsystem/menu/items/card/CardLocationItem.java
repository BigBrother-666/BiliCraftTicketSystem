package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
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

public class CardLocationItem extends AbstractItem {
    protected final ItemStack itemStack;
    protected final MenuLocationCard fromMenu;
    /**
     * 车站真实站名（== geojson 站名）。设站以此为准，不读图标 displayName。
     */
    protected final String stationName;

    public CardLocationItem(ItemStack itemStack, MenuLocationCard fromMenu) {
        this(itemStack, fromMenu, null);
    }

    public CardLocationItem(ItemStack itemStack, MenuLocationCard fromMenu, String stationName) {
        this.fromMenu = fromMenu;
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
        BCCard card = BCCard.fromHeldItem(player);
        if (card == null) {
            player.closeInventory();
            return;
        }

        MenuCard menu = MenuCard.getMenu(player);
        if (fromMenu.isStart()) {
            card.setStartStation(StationProvider.stationNameComponent(stationName));
        } else {
            card.setEndStation(StationProvider.stationNameComponent(stationName));
        }
        menu.open();
    }
}
