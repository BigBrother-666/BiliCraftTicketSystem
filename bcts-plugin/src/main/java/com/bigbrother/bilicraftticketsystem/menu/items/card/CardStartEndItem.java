package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
import com.bigbrother.bilicraftticketsystem.menu.items.common.CoolDownItem;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
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

import java.util.List;

public class CardStartEndItem extends CoolDownItem {
    private final boolean isStart;
    private final ItemStack startItemStack;
    private final ItemStack endItemStack;

    public CardStartEndItem(boolean isStart) {
        this.isStart = isStart;
        this.startItemStack = CommonUtils.loadItemFromFile("start");
        this.endItemStack = CommonUtils.loadItemFromFile("end");
    }

    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemMeta itemMeta;
        BCCard card = BCCard.fromHeldItem(player);
        if (isStart) {
            itemMeta = startItemStack.getItemMeta();
            itemMeta.lore(List.of(
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(card != null ? card.getCardInfo().getStartStation().decoration(TextDecoration.ITALIC, true) : PlayerOption.NOT_AVALIABLE_COMPONENT),
                    Component.text(""),
                    Component.text("左键选择起始站，右键清除选择", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)
            ));
            startItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(startItemStack);
        } else {
            itemMeta = endItemStack.getItemMeta();
            itemMeta.lore(List.of(
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(card != null ? card.getCardInfo().getEndStation().decoration(TextDecoration.ITALIC, true) : PlayerOption.NOT_AVALIABLE_COMPONENT),
                    Component.text(""),
                    Component.text("左键选择终到站，右键清除选择", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)
            ));
            endItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(endItemStack);
        }
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (isCooldown(player)) {
            return;
        }

        BCCard card = BCCard.fromHeldItem(player);
        if (card == null) {
            player.closeInventory();
            return;
        }

        if (clickType.isLeftClick()) {
            MenuLocationCard.getMenu(player, isStart).open();
        } else if (clickType.isRightClick()) {
            if (isStart) {
                card.setStartStation(null);
            } else {
                card.setEndStation(null);
            }
            this.notifyWindows();
        }
    }
}
