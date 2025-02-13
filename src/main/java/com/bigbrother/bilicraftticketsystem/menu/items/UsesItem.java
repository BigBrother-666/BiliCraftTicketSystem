package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.ArrayList;
import java.util.List;

public class UsesItem extends AbstractItem {
    private int uses = 1;

    @Override
    public ItemProvider getItemProvider() {
        ItemStack itemStack = Utils.loadItemFromFile("uses");
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("当前选择的使用次数：%d次".formatted(uses), NamedTextColor.GOLD));
        lore.add(Component.text("左键+1次，右键-1次", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("shift左键+5次", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menu = MenuMain.getMenu(player);
        if (clickType.isLeftClick()) {
            int targetUses;
            if (clickType.isShiftClick()) {
                targetUses = Math.min(MainConfig.maxUses, uses + 5);
            } else {
                targetUses = Math.min(MainConfig.maxUses, uses + 1);
            }
            uses = targetUses;
            menu.getPlayerOption().setUses(targetUses);
        } else if (clickType.isRightClick()) {
            int targetUses = Math.max(1, uses - 1);
            uses = targetUses;
            menu.getPlayerOption().setUses(targetUses);
        }
        notifyWindows();
        menu.updateTicketInfo();
    }
}
