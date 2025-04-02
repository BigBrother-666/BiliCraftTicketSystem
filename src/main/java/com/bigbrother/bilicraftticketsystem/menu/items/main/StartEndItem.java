package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.List;

public class StartEndItem extends AbstractItem {
    private final boolean isStart;
    private final ItemStack startItemStack;
    private final ItemStack endItemStack;

    public StartEndItem(boolean isStart) {
        this.isStart = isStart;
        this.startItemStack = Utils.loadItemFromFile("start");
        this.endItemStack = Utils.loadItemFromFile("end");
    }

    @Override
    public ItemProvider getItemProvider(Player player) {
        MenuMain menu = MenuMain.getMenu(player);
        ItemMeta itemMeta;
        if (isStart) {
            itemMeta = startItemStack.getItemMeta();
            itemMeta.lore(List.of(Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(Component.text(menu.getPlayerOption().getStartStationString()))));
            startItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(startItemStack);
        } else {
            itemMeta = endItemStack.getItemMeta();
            itemMeta.lore(List.of(Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(Component.text(menu.getPlayerOption().getEndStationString()))));
            endItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(endItemStack);
        }
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuLocation.getMenu(player, isStart).open();
    }
}
