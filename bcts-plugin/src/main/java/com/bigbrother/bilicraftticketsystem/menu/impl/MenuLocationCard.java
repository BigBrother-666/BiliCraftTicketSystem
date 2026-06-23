package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardLocationItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardNearestLocItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollUpItem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;

import java.util.ArrayList;
import java.util.List;


public class MenuLocationCard extends Menu {
    private final ScrollGui<@NotNull Item> gui;
    private CardNearestLocItem nearestLocItem;
    @Getter
    @Setter
    private boolean isStart;

    private MenuLocationCard(Player player, boolean isStart) {
        this.isStart = isStart;

        FileConfiguration locationConfig = MenuConfig.getLocationMenuConfig();

        this.gui = ScrollGui.items()
                .setStructure(buildStructure(locationConfig, itemName -> switch (itemName) {
                    case "content" -> Markers.CONTENT_LIST_SLOT_HORIZONTAL;
                    case "scrollup" -> new ScrollUpItem();
                    case "scrolldown" -> new ScrollDownItem();
                    case "nearest" -> (nearestLocItem = new CardNearestLocItem(player, this));
                    default -> null;
                }))
                .build();

        // 默认展示全部车站（拼音正序）；调用方可在 open 前用 setStations 注入过滤后的列表
        setStations(StationProvider.listStations(), null);

        this.window = buildSingleWindow(player, locationConfig, gui);
    }

    /**
     * 动态设置本界面展示的车站列表（按系统过滤 / 铁砧搜索结果 / 全部）。
     *
     * @param stations 车站条目列表
     * @param systemId 当前所属铁路系统 id（系统入口已知；搜索入口为 null）；决定图标取哪个系统的自定义旗帜，
     *                 以及成员拖旗帜时把图标存到哪个系统名下
     */
    public void setStations(List<StationProvider.StationEntry> stations, String systemId) {
        List<Item> items = new ArrayList<>();
        for (StationProvider.StationEntry entry : stations) {
            items.add(new CardLocationItem(StationProvider.buildIcon(entry, systemId), this, entry.name(), systemId));
        }
        gui.setContent(items);
    }

    /**
     * 用 {@code railway_system} 占位符值刷新标题：系统名（按系统选择进入）或「关键词: 搜索词」（搜索进入）。
     *
     * @param railwaySystem 占位符值
     */
    public void updateTitle(String railwaySystem) {
        applyTitle(MenuConfig.getLocationMenuConfig(), java.util.Map.of("railway_system", railwaySystem));
    }

    @Override
    public void open() {
        super.open();
        if (nearestLocItem != null) {
            nearestLocItem.calcNearestStation();
        }
    }

    public static MenuLocationCard getMenu(Player player, Boolean isStart) {
        return new MenuLocationCard(player, isStart != null && isStart);
    }
}
