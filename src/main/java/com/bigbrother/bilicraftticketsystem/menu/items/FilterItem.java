package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.List;
import java.util.Set;

public class FilterItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemBuilder itemBuilder = new ItemBuilder(Utils.loadItemFromFile("filter"));
        List<Item> tickets = MenuMain.getMenu(player).getMenuTicketList();
        if (tickets == null || tickets.isEmpty()) {
            // 添加 “当前不可用”
            itemBuilder.addLoreLines(new AdventureComponentWrapper(Component.text("筛选不可用，请先搜索车票", NamedTextColor.RED)));
        } else {
            MenuFilter menuFilter = MenuFilter.getMenu(player);
            itemBuilder.addLoreLines(new AdventureComponentWrapper(Component.text("已选择的途径车站：", NamedTextColor.GOLD)));
            itemBuilder.addLoreLines(new AdventureComponentWrapper(getFormattedComponent(menuFilter)));
        }
        return itemBuilder;
    }

    public static @NotNull Component getFormattedComponent(MenuFilter menuFilter) {
        Component lore = Component.text("");
        Set<String> filterStations = menuFilter.getFilterStations();
        int i = 1;
        for (String filterStation : filterStations) {
            if (i % 10 == 0) {
                if (i == filterStations.size()) {
                    lore = lore.append(Component.text(filterStation, NamedTextColor.GREEN));
                } else {
                    lore = lore.append(Component.text(filterStation + ", \n", NamedTextColor.GREEN));
                }
            } else {
                if (i == filterStations.size()) {
                    lore = lore.append(Component.text(filterStation, NamedTextColor.GREEN));
                } else {
                    lore = lore.append(Component.text(filterStation + ", ", NamedTextColor.GREEN));
                }
            }
            i++;
        }
        return lore;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        List<Item> tickets = MenuMain.getMenu(player).getMenuTicketList();
        if (tickets != null && !tickets.isEmpty()) {
            MenuFilter.getMenu(player).open();
        }
    }
}
