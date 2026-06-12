package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardLocationItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardNearestLocItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollUpItem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;


public class MenuLocationCard extends Menu {
    private CardNearestLocItem nearestLocItem;
    @Getter
    @Setter
    private boolean isStart;

    private MenuLocationCard(Player player, boolean isStart) {
        this.isStart = isStart;

        FileConfiguration locationConfig = MenuConfig.getLocationMenuConfig();

        ScrollGui.@NotNull Builder<@NotNull Item> guiBuilder = ScrollGui.items()
                .setStructure(locationConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : locationConfig.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length == 2) {
                String itemName;
                if (split[1].startsWith("item-")) {
                    itemName = split[1].replaceFirst("item-", "");
                } else {
                    itemName = split[1];
                }
                switch (itemName) {
                    case "content":
                        guiBuilder.addIngredient(split[0].charAt(0), Markers.CONTENT_LIST_SLOT_HORIZONTAL);
                        break;
                    case "scrollup":
                        guiBuilder.addIngredient(split[0].charAt(0), new ScrollUpItem());
                        break;
                    case "scrolldown":
                        guiBuilder.addIngredient(split[0].charAt(0), new ScrollDownItem());
                        break;
                    case "nearest":
                        nearestLocItem = new CardNearestLocItem(player,this);
                        guiBuilder.addIngredient(split[0].charAt(0), nearestLocItem);
                        break;
                    default:
                        try {
                            guiBuilder.addIngredient(split[0].charAt(0), new SimpleItem(new ItemBuilder(Material.valueOf(itemName))));
                        } catch (IllegalArgumentException e) {
                            guiBuilder.addIngredient(split[0].charAt(0), new ItemBuilder(CommonUtils.loadItemFromFile(itemName)));
                        }
                }
            }
        }

        // 添加物品（车站列表实时来自 geojson 路由图）
        for (StationProvider.StationEntry entry : StationProvider.listStations()) {
            guiBuilder.addContent(new CardLocationItem(StationProvider.buildIcon(entry), this));
        }

        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(CommonUtils.mmStr2Component(locationConfig.get("title", String.class, ""))))
                .setGui(guiBuilder.build())
                .build();
    }

    @Override
    public void open() {
        super.open();
        if (nearestLocItem != null) {
            nearestLocItem.calcNearestStation();
        }
    }

    public static MenuLocationCard getMenu(Player player, Boolean isStart) {
        return new MenuLocationCard(player, isStart);
    }
}
