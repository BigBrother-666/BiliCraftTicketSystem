package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.items.common.NextpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.PrevpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.main.*;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;

public class MenuMain extends Menu {
    @Getter
    private static final Map<UUID, MenuMain> mainMenuMapping = new HashMap<>();
    PagedGui<@NotNull Item> gui;
    private final Player player;

    @Getter
    @Setter
    private PlayerOption playerOption;
    @Getter
    private List<Item> menuTicketList;
    @Getter
    private FilterItem filterItem;

    private MenuMain(Player player) {
        this(player, null, null);
    }

    private MenuMain(Player player, Component startStation, Component endStation) {
        this.player = player;
        playerOption = new PlayerOption();
        if (startStation != null) {
            playerOption.setStartStation(startStation);
        }
        if (endStation != null) {
            playerOption.setEndStation(endStation);
        }

        FileConfiguration mainConfig = MenuConfig.getMainMenuConfig();

        PagedGui.@NotNull Builder<@NotNull Item> guiBuilder = PagedGui.items()
                .setStructure(mainConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : mainConfig.getList("mapping", String.class, Collections.emptyList())) {
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
                    case "start":
                        guiBuilder.addIngredient(split[0].charAt(0), new StartEndItem(true));
                        break;
                    case "end":
                        guiBuilder.addIngredient(split[0].charAt(0), new StartEndItem(false));
                        break;
                    case "speed":
                        guiBuilder.addIngredient(split[0].charAt(0), new SpeedItem());
                        break;
                    case "uses":
                        guiBuilder.addIngredient(split[0].charAt(0), new UsesItem());
                        break;
                    case "search":
                        guiBuilder.addIngredient(split[0].charAt(0), new SearchItem());
                        break;
                    case "warn":
                        guiBuilder.addIngredient(split[0].charAt(0), new WarnItem());
                        break;
                    case "nextpage":
                        guiBuilder.addIngredient(split[0].charAt(0), new NextpageItem());
                        break;
                    case "prevpage":
                        guiBuilder.addIngredient(split[0].charAt(0), new PrevpageItem());
                        break;
                    case "filter":
                        filterItem = new FilterItem();
                        guiBuilder.addIngredient(split[0].charAt(0), filterItem);
                        break;
                    case "ticketbg":
                        guiBuilder.addIngredient(split[0].charAt(0), new TicketbgItem());
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

        this.gui = guiBuilder.build();
        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(Component.text(mainConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();

        mainMenuMapping.put(player.getUniqueId(), this);
    }

    public void setTickets(List<Item> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            gui.setContent(List.of(new TicketItem("所选两站没有直达方案")));
            return;
        }
        this.menuTicketList = tickets;
        gui.setContent(tickets);
    }

    public void filterTickets(Set<String> filter) {
        if (filter == null || filter.isEmpty()) {
            gui.setContent(menuTicketList);
            return;
        }

        List<Item> filteredTickets = new ArrayList<>();

        for (Item item : menuTicketList) {
            if (item instanceof TicketItem ticketItem &&
                    ticketItem.getTicket() != null &&
                    new HashSet<>(ticketItem.getTicket().getPathInfo().getStationSequence()).containsAll(filter)) {
                filteredTickets.add(item);
            }
        }

        if (filteredTickets.isEmpty()) {
            // 无车票符合条件，显示屏障
            Component errMsg = Utils.mmStr2Component(
                    MainConfig.message.get("filter-empty","").formatted(
                    playerOption.getStartStationString(),
                    playerOption.getEndStationString(),
                    String.join(",", filter))
            ).decoration(TextDecoration.ITALIC,false).color(NamedTextColor.RED);
            gui.setContent(List.of(new TicketItem(errMsg)));
        } else {
            gui.setContent(filteredTickets);
        }
    }

    /**
     * 通知gui更新车票的lore
     */
    public void updateTicketInfo() {
        if (menuTicketList == null) {
            return;
        }
        for (Item ticket : menuTicketList) {
            if (ticket instanceof TicketItem ticketItem) {
                ticketItem.updateLore(playerOption);
                ticketItem.notifyWindows();
            }
        }
    }

    @Override
    public void open() {
        filterTickets(MenuFilter.getMenu(player).getFilterStations());
        super.open();
    }

    public static void reload() {
        for (Map.Entry<UUID, MenuMain> entry : mainMenuMapping.entrySet()) {
            entry.getValue().close();
        }
        mainMenuMapping.clear();
    }

    public static MenuMain getMenu(Player player) {
        if (mainMenuMapping.containsKey(player.getUniqueId())) {
            return mainMenuMapping.get(player.getUniqueId());
        } else {
            return new MenuMain(player);
        }
    }

    public void clearTickets() {
        menuTicketList = new ArrayList<>();
        gui.setContent(menuTicketList);
    }
}
