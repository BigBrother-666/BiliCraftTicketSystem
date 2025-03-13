package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.ArrayList;
import java.util.List;

public class FilterBackItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemStack itemStack = Utils.loadItemFromFile("back");
        MenuFilter menuFilter = MenuFilter.getMenu(player);
        List<Component> lore = itemStack.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.add(Component.text("已选择的途径车站：", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(FilterItem.getFormattedComponent(menuFilter).decoration(TextDecoration.ITALIC, false));
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menuMain = MenuMain.getMenu(player);
        menuMain.open();
    }
}
