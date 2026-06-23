package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuStationSearch;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuSystem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
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

import java.util.List;

public class StartEndItem extends AbstractItem {
    private final boolean isStart;
    private final ItemStack startItemStack;
    private final ItemStack endItemStack;

    public StartEndItem(boolean isStart) {
        this.isStart = isStart;
        this.startItemStack = CommonUtils.loadItemFromFile("start");
        this.endItemStack = CommonUtils.loadItemFromFile("end");
    }

    @Override
    public ItemProvider getItemProvider(Player player) {
        MenuMain menu = MenuMain.getMenu(player);
        ItemMeta itemMeta;
        if (isStart) {
            itemMeta = startItemStack.getItemMeta();
            itemMeta.lore(List.of(
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(menu.getPlayerOption().getStartStationComponent().decoration(TextDecoration.ITALIC, true)),
                    Component.text("左键选择车站，Shift+左键搜索车站", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)));
            startItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(startItemStack);
        } else {
            itemMeta = endItemStack.getItemMeta();
            itemMeta.lore(List.of(
                    Component.text("当前选择：", NamedTextColor.DARK_AQUA).append(menu.getPlayerOption().getEndStationComponent().decoration(TextDecoration.ITALIC, true)),
                    Component.text("左键选择车站，Shift+左键搜索车站", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)));
            endItemStack.setItemMeta(itemMeta);
            return new ItemBuilder(endItemStack);
        }
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (clickType == ClickType.SHIFT_LEFT) {
            // shift+左键：铁砧搜索车站，确认后打开搜索结果车站列表（系统未知 → null）
            MenuStationSearch.open(player, keyword ->
                    openStations(player, StationProvider.searchStations(keyword), "关键词: " + keyword, null));
        } else if (clickType.isLeftClick()) {
            // 左键：先选铁路系统（系统数 ≤ 1 时自动跳过），再打开该系统的车站列表
            MenuSystem.openOrSkip(player, systemId ->
                    openStations(player, StationProvider.listStationsOfSystem(systemId), systemDisplayName(systemId), systemId));
        }
    }

    /**
     * 系统 id → 标题展示用系统名；id 为 null（无系统、展示全部）时回退 {@link CommonUtils#NOT_AVAILABLE}。
     */
    private static String systemDisplayName(String systemId) {
        if (systemId == null) {
            return CommonUtils.NOT_AVAILABLE;
        }
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        return system != null ? system.getName() : CommonUtils.NOT_AVAILABLE;
    }

    /**
     * 用指定车站列表打开车站选择界面（同步设置起点/终点目标后展示），并按入口刷新标题占位符。
     *
     * @param railwaySystem 标题 {@code railway_system} 占位符值（系统名 / 关键词）
     * @param systemId      当前铁路系统 id（系统入口已知；搜索入口 null），用于车站按钮图标与拖旗帜归属
     */
    private void openStations(Player player, java.util.List<StationProvider.StationEntry> stations, String railwaySystem, String systemId) {
        MenuLocation menu = MenuLocation.getMenu(player, isStart);
        menu.setStations(stations, systemId);
        menu.updateTitle(railwaySystem);
        menu.open();
    }
}
