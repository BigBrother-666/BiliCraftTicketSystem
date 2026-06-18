package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.location.LocationItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollUpItem;
import com.bigbrother.bilicraftticketsystem.menu.items.location.NearestLocItem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;


public class MenuLocation extends Menu {
    @Getter
    private static final Map<UUID, MenuLocation> locationMenuMapping = new HashMap<>();

    private final ScrollGui<@NotNull Item> gui;
    private NearestLocItem nearestLocItem;
    @Getter
    @Setter
    private boolean isStart;

    private MenuLocation(Player player, boolean isStart) {
        this.isStart = isStart;

        FileConfiguration locationConfig = MenuConfig.getLocationMenuConfig();

        this.gui = ScrollGui.items()
                .setStructure(buildStructure(locationConfig, itemName -> switch (itemName) {
                    case "content" -> Markers.CONTENT_LIST_SLOT_HORIZONTAL;
                    case "scrollup" -> new ScrollUpItem();
                    case "scrolldown" -> new ScrollDownItem();
                    case "nearest" -> (nearestLocItem = new NearestLocItem(player));
                    default -> null;
                }))
                .build();

        // 默认展示全部车站（拼音正序）；调用方可在 open 前用 setStations 注入过滤后的列表
        setStations(StationProvider.listStations());

        this.window = buildSingleWindow(player, locationConfig, gui);

        locationMenuMapping.put(player.getUniqueId(), this);
    }

    /**
     * 动态设置本界面展示的车站列表（按系统过滤 / 铁砧搜索结果 / 全部）。
     *
     * @param stations 车站条目列表
     */
    public void setStations(List<StationProvider.StationEntry> stations) {
        List<Item> items = new ArrayList<>();
        for (StationProvider.StationEntry entry : stations) {
            items.add(new LocationItem(StationProvider.buildIcon(entry)));
        }
        gui.setContent(items);
    }

    @Override
    public void open() {
        super.open();
        if (nearestLocItem != null) {
            nearestLocItem.calcNearestStation();
        }
    }

    public static void reload() {
        for (Map.Entry<UUID, MenuLocation> entry : locationMenuMapping.entrySet()) {
            entry.getValue().close();
        }
        locationMenuMapping.clear();
        NearestLocItem.setPlatfromInfoList(plugin.getTrainDatabaseManager().getBcspawnService().getAllPlatfromInfo());
    }

    public static MenuLocation getMenu(Player player) {
        return getMenu(player, null);
    }

    public static MenuLocation getMenu(Player player, Boolean isStart) {
        if (locationMenuMapping.containsKey(player.getUniqueId())) {
            MenuLocation menuLocation = locationMenuMapping.get(player.getUniqueId());
            if (isStart != null) {
                menuLocation.setStart(isStart);
            }
            return menuLocation;
        } else {
            return new MenuLocation(player, isStart != null && isStart);
        }
    }
}
