package com.bigbrother.bilicraftticketsystem.menu;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import com.bigbrother.bilicraftticketsystem.menu.items.*;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class MenuMain implements Menu {
    @Getter
    private static final Map<UUID, MenuMain> mainMenuMapping = new HashMap<>();
    private final Window window;
    ScrollGui<@NotNull Item> gui;

    @Getter
    @Setter
    private PlayerOption playerOption;
    private List<Item> tickets;

    private MenuMain(Player player) {
        this(player, null, null);
    }

    private MenuMain(Player player, Component startStation, Component endStation) {
        playerOption = new PlayerOption();
        if (startStation != null) {
            playerOption.setStartStation(startStation);
        }
        if (endStation != null) {
            playerOption.setEndStation(endStation);
        }

        FileConfiguration mainConfig = new FileConfiguration(plugin, EnumConfig.MENU_MAIN.getFileName());
        mainConfig.load();

        ScrollGui.@NotNull Builder<@NotNull Item> guiBuilder = ScrollGui.items()
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
                    default:
                        guiBuilder.addIngredient(split[0].charAt(0), new SimpleItem(new ItemBuilder(Material.valueOf(split[1]))));
                }
            }
        }

        gui = guiBuilder.build();
        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(Component.text(mainConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();

        mainMenuMapping.put(player.getUniqueId(), this);
    }

    public void setTickets(List<Item> tickets) {
        this.tickets = tickets;
        gui.setContent(tickets);
    }

    /**
     * 通知gui更新车票的lore
     */
    public void updateTicketInfo() {
        if (tickets == null) {
            return;
        }
        for (Item ticket : tickets) {
            if (ticket instanceof TicketItem ticketItem) {
                ticketItem.updateLore();
                ticketItem.notifyWindows();
            }
        }
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

    public static void clearAll() {
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
}
