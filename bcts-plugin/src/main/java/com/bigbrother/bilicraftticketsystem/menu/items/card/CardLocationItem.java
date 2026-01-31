package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
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

    public CardLocationItem(ItemStack itemStack, MenuLocationCard fromMenu) {
        this.fromMenu = fromMenu;
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
        BCCard card = BCCard.fromHeldItem(player);
        if (card == null) {
            player.closeInventory();
            return;
        }

        MenuCard menu = MenuCard.getMenu(player);
        if (fromMenu.isStart()) {
            card.setStartStation(itemStack.getItemMeta().displayName());
        } else {
            card.setEndStation(itemStack.getItemMeta().displayName());
        }
//        card.refreshCard(true);
        menu.open();
    }
}
