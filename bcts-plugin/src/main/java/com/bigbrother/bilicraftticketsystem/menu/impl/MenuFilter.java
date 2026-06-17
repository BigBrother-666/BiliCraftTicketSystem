package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollUpItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.BackToMainItem;
import com.bigbrother.bilicraftticketsystem.menu.items.filter.FilterLocItem;
import com.bigbrother.bilicraftticketsystem.menu.items.filter.FilterRefreshItem;
import com.bigbrother.bilicraftticketsystem.menu.items.main.TicketItem;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;

public class MenuFilter extends Menu {
    @Getter
    private static final Map<UUID, MenuFilter> filterMenuMapping = new HashMap<>();
    @Getter
    private Set<String> filterStations;
    @Getter
    private List<Item> filterLocItems;

    private final ScrollGui<@NotNull Item> gui;

    public MenuFilter(Player player) {
        filterStations = new HashSet<>();
        filterLocItems = new ArrayList<>();
        FileConfiguration filterConfig = MenuConfig.getFilterMenuConfig();

        ScrollGui.@NotNull Builder<@NotNull Item> guiBuilder = ScrollGui.items()
                .setStructure(filterConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : filterConfig.getList("mapping", String.class, Collections.emptyList())) {
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
                    case "back":
                        guiBuilder.addIngredient(split[0].charAt(0), new BackToMainItem());
                        break;
                    case "refresh":
                        guiBuilder.addIngredient(split[0].charAt(0), new FilterRefreshItem());
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

        gui = guiBuilder.build();

        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(CommonUtils.mmStr2Component(filterConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();

        filterMenuMapping.put(player.getUniqueId(), this);
    }

    public void setFilterLocItems(Player player) {
        filterStations = new HashSet<>();
        filterLocItems = new ArrayList<>();

        // 根据显示的车票添加车站
        Set<String> stations = new HashSet<>();
        List<Item> tickets = MenuMain.getMenu(player).getMenuTicketList();
        if (tickets != null && !tickets.isEmpty()) {
            for (Item ticket : tickets) {
                if (ticket instanceof TicketItem ticketItem) {
                    stations.addAll(ticketItem.getTicket().getPathInfo().stationSequence());
                }
            }
        }

        // 移除起始站 终到站
        PlayerOption playerOption = MenuMain.getMenu(player).getPlayerOption();
        stations.remove(playerOption.getStartStationString());
        stations.remove(playerOption.getEndStationString());

        // 添加物品（车站图标实时来自 geojson 路由图，过滤出当前车票途经的中间站）
        for (StationProvider.StationEntry entry : StationProvider.listStations()) {
            if (!stations.contains(entry.name())) {
                continue;
            }
            ItemStack customItem = StationProvider.buildIcon(entry);
            ItemMeta itemMeta = customItem.getItemMeta();
            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            customItem.setItemMeta(itemMeta);

            FilterLocItem filterLocItem = new FilterLocItem(customItem);
            filterLocItems.add(filterLocItem);
        }

        gui.setContent(filterLocItems);
    }

    public static void reload() {
        for (Map.Entry<UUID, MenuFilter> entry : filterMenuMapping.entrySet()) {
            entry.getValue().close();
        }
        filterMenuMapping.clear();
    }

    public static MenuFilter getMenu(Player player) {
        if (filterMenuMapping.containsKey(player.getUniqueId())) {
            return filterMenuMapping.get(player.getUniqueId());
        } else {
            return new MenuFilter(player);
        }
    }
}
