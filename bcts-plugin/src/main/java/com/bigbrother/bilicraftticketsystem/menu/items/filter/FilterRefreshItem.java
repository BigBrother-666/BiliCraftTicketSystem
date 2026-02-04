package com.bigbrother.bilicraftticketsystem.menu.items.filter;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.HashSet;

public class FilterRefreshItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(CommonUtils.loadItemFromFile("refresh"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuFilter menuFilter = MenuFilter.getMenu(player);
        MenuMain menuMain = MenuMain.getMenu(player);
        menuFilter.getFilterStations().clear();
        for (Item item : menuFilter.getFilterLocItems()) {
            if (item instanceof FilterLocItem filterLocItem) {
                filterLocItem.setSelected(false);
                filterLocItem.notifyWindows();
            }
        }
        menuMain.filterTickets(new HashSet<>());
    }
}
