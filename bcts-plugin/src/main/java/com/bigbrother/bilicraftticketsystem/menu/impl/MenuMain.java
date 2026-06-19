package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.items.common.NextpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.PrevpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.main.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;

import java.util.*;

public class MenuMain extends Menu {
    @Getter
    private static final Map<UUID, MenuMain> mainMenuMapping = new HashMap<>();
    private final PagedGui<@NotNull Item> gui;

    @Getter
    @Setter
    private PlayerOption playerOption;
    @Getter
    private List<Item> menuTicketList;

    private MenuMain(Player player) {
        playerOption = new PlayerOption();

        FileConfiguration mainConfig = MenuConfig.getMainMenuConfig();

        this.gui = PagedGui.items()
                .setStructure(buildStructure(mainConfig, itemName -> switch (itemName) {
                    case "content" -> Markers.CONTENT_LIST_SLOT_HORIZONTAL;
                    case "start" -> new StartEndItem(true);
                    case "end" -> new StartEndItem(false);
                    case "speed" -> new SpeedItem();
                    case "uses" -> new UsesItem();
                    case "search" -> new SearchItem();
                    case "warn" -> new WarnItem();
                    case "nextpage" -> new NextpageItem();
                    case "prevpage" -> new PrevpageItem();
                    case "ticketbg" -> new TicketbgItem();
                    default -> null;
                }))
                .build();
        this.window = buildSingleWindow(player, mainConfig, gui);

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
