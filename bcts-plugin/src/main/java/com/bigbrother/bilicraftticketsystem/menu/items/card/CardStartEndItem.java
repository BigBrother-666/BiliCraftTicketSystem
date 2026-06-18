package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuStationSearch;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuSystem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.CoolDownItem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
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
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(card != null ? card.getCardInfo().getStartStation().decoration(TextDecoration.ITALIC, true) : CommonUtils.NOT_AVAILABLE_COMPONENT),
                    Component.text(""),
                    Component.text("左键选择起始站，右键清除选择", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift+左键搜索车站", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)
            ));
            startItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(startItemStack);
        } else {
            itemMeta = endItemStack.getItemMeta();
            itemMeta.lore(List.of(
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(card != null ? card.getCardInfo().getEndStation().decoration(TextDecoration.ITALIC, true) : CommonUtils.NOT_AVAILABLE_COMPONENT),
                    Component.text(""),
                    Component.text("左键选择终到站，右键清除选择", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift+左键搜索车站", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)
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

        if (clickType == ClickType.SHIFT_LEFT) {
            // shift+左键：铁砧搜索车站，确认后打开搜索结果车站列表
            MenuStationSearch.open(player, keyword ->
                    openStations(player, StationProvider.searchStations(keyword)));
        } else if (clickType.isLeftClick()) {
            // 左键：先选铁路系统（系统数 ≤ 1 时自动跳过），再打开该系统的车站列表
            MenuSystem.openOrSkip(player, systemId ->
                    openStations(player, StationProvider.listStationsOfSystem(systemId)));
        } else if (clickType.isRightClick()) {
            if (isStart) {
                card.setStartStation(null);
            } else {
                card.setEndStation(null);
            }
            this.notifyWindows();
        }
    }

    /**
     * 用指定车站列表打开交通卡的车站选择界面。
     */
    private void openStations(Player player, java.util.List<StationProvider.StationEntry> stations) {
        MenuLocationCard menu = MenuLocationCard.getMenu(player, isStart);
        menu.setStations(stations);
        menu.open();
    }
}
