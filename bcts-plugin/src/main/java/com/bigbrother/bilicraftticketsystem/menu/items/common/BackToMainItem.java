package com.bigbrother.bilicraftticketsystem.menu.items.common;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

public class BackToMainItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemStack itemStack = CommonUtils.loadItemFromFile("back");
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menuMain = MenuMain.getMenu(player);
        menuMain.open();
    }
}
