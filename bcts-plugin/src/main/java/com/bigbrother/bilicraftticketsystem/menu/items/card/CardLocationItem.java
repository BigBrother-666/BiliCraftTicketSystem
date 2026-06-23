package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
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
    protected ItemStack itemStack;
    protected final MenuLocationCard fromMenu;
    /**
     * 车站真实站名（== geojson 站名）。设站以此为准，不读图标 displayName。
     */
    protected final String stationName;
    /**
     * 当前车站列表所属铁路系统 id（系统入口已知；搜索入口为 null）。成员拖旗帜覆盖图标时归到此系统名下。
     */
    protected final String systemId;

    public CardLocationItem(ItemStack itemStack, MenuLocationCard fromMenu) {
        this(itemStack, fromMenu, null, null);
    }

    public CardLocationItem(ItemStack itemStack, MenuLocationCard fromMenu, String stationName, String systemId) {
        this.fromMenu = fromMenu;
        ItemMeta itemMeta = itemStack.getItemMeta();
        //noinspection deprecation
        itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        itemStack.setItemMeta(itemMeta);
        this.itemStack = itemStack;
        this.stationName = stationName;
        this.systemId = systemId;
    }

    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (stationName == null) {
            return;
        }
        // 铁路系统成员把旗帜拖到车站按钮上：覆盖图标（不消耗旗帜），不进入选站逻辑
        if (StationProvider.tryApplyBannerIcon(player, systemId, stationName, inventoryClickEvent.getCursor())) {
            this.itemStack = StationProvider.buildIcon(stationName, systemId);
            notifyWindows();
            return;
        }
        BCCard card = BCCard.fromHeldItem(player);
        if (card == null) {
            player.closeInventory();
            return;
        }

        MenuCard menu = MenuCard.getMenu(player);
        if (fromMenu.isStart()) {
            card.setStartStation(StationProvider.stationNameComponent(stationName));
        } else {
            card.setEndStation(StationProvider.stationNameComponent(stationName));
        }
        menu.open();
    }
}
