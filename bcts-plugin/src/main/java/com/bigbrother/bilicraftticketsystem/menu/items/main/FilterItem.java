package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FilterItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemStack itemStack = CommonUtils.loadItemFromFile("filter");
        List<Item> tickets = MenuMain.getMenu(player).getMenuTicketList();
        List<Component> lore = itemStack.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        if (tickets == null || tickets.isEmpty()) {
            // 添加 “当前不可用”
            lore.add(Component.text("筛选不可用，请先搜索车票", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            MenuFilter menuFilter = MenuFilter.getMenu(player);
            lore.add(Component.text("已选择的途经车站：", NamedTextColor.DARK_AQUA));
            lore.add(getFormattedComponent(menuFilter));
        }
        itemStack.lore(lore);
        return new ItemBuilder(itemStack);
    }

    public static @NotNull Component getFormattedComponent(MenuFilter menuFilter) {
        Component lore = Component.text("");
        Set<String> filterStations = menuFilter.getFilterStations();
        int i = 1;
        for (String filterStation : filterStations) {
            if (i % 10 == 0) {
                if (i == filterStations.size()) {
                    lore = lore.append(Component.text(filterStation, NamedTextColor.AQUA));
                } else {
                    lore = lore.append(Component.text(filterStation + ", \n", NamedTextColor.AQUA));
                }
            } else {
                if (i == filterStations.size()) {
                    lore = lore.append(Component.text(filterStation, NamedTextColor.AQUA));
                } else {
                    lore = lore.append(Component.text(filterStation + ", ", NamedTextColor.AQUA));
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
