package com.bigbrother.bilicraftticketsystem.menu.items.location;

import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
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

public class LocationItem extends AbstractItem {
    protected ItemStack itemStack;
    /**
     * 车站真实站名（== geojson 站名）。设站时以此为准，<b>不</b>读图标 displayName，
     * 避免图标显示名（含颜色/样式）被当成站名造成寻路 / 数据不稳。
     */
    protected final String stationName;
    /**
     * 当前车站列表所属铁路系统 id（系统入口已知；搜索入口为 null）。成员拖旗帜覆盖图标时归到此系统名下。
     */
    protected final String systemId;

    public LocationItem(ItemStack itemStack) {
        this(itemStack, null, null);
    }

    public LocationItem(ItemStack itemStack, String stationName, String systemId) {
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
        MenuMain menu = MenuMain.getMenu(player);
        if (MenuLocation.getMenu(player).isStart()) {
            menu.getPlayerOption().setStartStationComponent(StationProvider.stationNameComponent(stationName));
        } else {
            menu.getPlayerOption().setEndStationComponent(StationProvider.stationNameComponent(stationName));
        }
        menu.clearTickets();
        menu.open();
    }
}
