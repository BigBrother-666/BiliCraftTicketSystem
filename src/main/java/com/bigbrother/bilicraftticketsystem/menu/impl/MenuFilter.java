package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.ScrollUpItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.BackToMainItem;
import com.bigbrother.bilicraftticketsystem.menu.items.filter.FilterLocItem;
import com.bigbrother.bilicraftticketsystem.menu.items.filter.FilterRefreshItem;
import com.bigbrother.bilicraftticketsystem.menu.items.main.TicketItem;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.Utils.loadItemFromFile;

public class MenuFilter implements Menu {
    @Getter
    private static final Map<UUID, MenuFilter> filterMenuMapping = new HashMap<>();
    @Getter
    private Set<String> filterStations;
    @Getter
    private List<Item> filterLocItems;

    private final Window window;

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
                            guiBuilder.addIngredient(split[0].charAt(0), new ItemBuilder(Utils.loadItemFromFile(itemName)));
                        }
                }
            }
        }

        gui = guiBuilder.build();

        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(Component.text(filterConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();

        filterMenuMapping.put(player.getUniqueId(), this);
    }

    @Override
    public void open() {
        if (window.isOpen()) {
            close();
            return;
        }
        this.window.open();
    }

    @Override
    public void close() {
        Bukkit.getScheduler().runTask(plugin, window::close);
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
                    for (TrainRoutes.StationAndRailway stationAndRailway : ticketItem.getTicket().getPathInfo().getPath()) {
                        stations.add(stationAndRailway.getStationName());
                    }
                }
            }
        }

        // 移除起始站 终到站
        PlayerOption playerOption = MenuMain.getMenu(player).getPlayerOption();
        stations.remove(playerOption.getStartStationString());
        stations.remove(playerOption.getEndStationString());

        // 添加物品
        ConfigurationNode contents = MenuConfig.getLocationMenuConfig().getNode("content");
        for (Map.Entry<String, Object> entry : contents.getValues().entrySet()) {
            if (!stations.contains(entry.getKey())) {
                continue;
            }
            ConfigurationNode item = contents.getNode(entry.getKey());
            if (item == null) {
                continue;
            }
            // 设置物品信息
            String material = item.get("material", "");
            ItemStack customItem;
            if (material.startsWith("item-")) {
                customItem = loadItemFromFile(material.replaceFirst("item-", "").trim());
            } else {
                customItem = new ItemStack(Material.valueOf(item.get("material", "").trim()));
            }
            ItemMeta itemMeta = customItem.getItemMeta();
            itemMeta.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
            List<Component> lore = itemMeta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            for (String s : item.getList("lore", String.class, Collections.emptyList())) {
                lore.add(MiniMessage.miniMessage().deserialize(s).decoration(TextDecoration.ITALIC, false));
            }
            itemMeta.lore(lore);
            itemMeta.displayName(MiniMessage.miniMessage().deserialize(item.get("name", String.class, "")).decoration(TextDecoration.ITALIC, false));
            customItem.setItemMeta(itemMeta);

            // 添加物品
            FilterLocItem filterLocItem = new FilterLocItem(customItem);
            filterLocItems.add(filterLocItem);
        }

        gui.setContent(filterLocItems);
    }

    public static void clearAll() {
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
