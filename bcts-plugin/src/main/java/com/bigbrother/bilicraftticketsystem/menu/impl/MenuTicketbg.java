package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.common.BackToMainItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.NextpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.PrevpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.ticketbg.*;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class MenuTicketbg extends Menu {
    @Getter
    private static int selfbgMaxCnt = 0;

    static {
        FileConfiguration ticketbgConfig = MenuConfig.getTicketbgMenuConfig();
        // 设置映射
        for (String mapping : ticketbgConfig.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length == 2) {
                if (split[1].startsWith("item-selfbg")) {
                    selfbgMaxCnt++;
                }
            }
        }
    }

    @Getter
    private static final Map<UUID, MenuTicketbg> ticketbgMenuMapping = new HashMap<>();
    @Getter
    private static final Map<UUID, TicketbgInfo> ticketbgUsageMapping = new HashMap<>();
    private final List<BgItem> selfbgItemList;
    private final Player player;
    private final PagedGui<@NotNull Item> gui;
    @Nullable
    private SortItem sortItem;

    private MenuTicketbg(Player player) {
        this.selfbgItemList = new ArrayList<>();
        this.player = player;

        FileConfiguration ticketbgConfig = MenuConfig.getTicketbgMenuConfig();
        PagedGui.@NotNull Builder<@NotNull Item> guiBuilder = PagedGui.items()
                .setStructure(ticketbgConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : ticketbgConfig.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length == 2) {
                String itemName;
                if (split[1].startsWith("item-")) {
                    itemName = split[1].replaceFirst("item-", "");
                } else {
                    itemName = split[1];
                }
                switch (itemName) {
                    case "sharedbg":
                        guiBuilder.addIngredient(split[0].charAt(0), Markers.CONTENT_LIST_SLOT_HORIZONTAL);
                        break;
                    case "selfbg":
                        SelfbgItem selfbgItem = new SelfbgItem(null);
                        selfbgItemList.add(selfbgItem);
                        guiBuilder.addIngredient(split[0].charAt(0), selfbgItem);
                        break;
                    case "nextpage":
                        guiBuilder.addIngredient(split[0].charAt(0), new NextpageItem());
                        break;
                    case "prevpage":
                        guiBuilder.addIngredient(split[0].charAt(0), new PrevpageItem());
                        break;
                    case "back":
                        guiBuilder.addIngredient(split[0].charAt(0), new BackToMainItem());
                        break;
                    case "defaultbg":
                        guiBuilder.addIngredient(split[0].charAt(0), new DefaultbgItem());
                        break;
                    case "sort":
                        SortItem item = new SortItem();
                        guiBuilder.addIngredient(split[0].charAt(0), item);
                        sortItem = item;
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
                .setTitle(new AdventureComponentWrapper(Component.text(ticketbgConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();

        ticketbgMenuMapping.put(player.getUniqueId(), this);
    }

    public void asyncUpdateAllTicketbg() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SortField field;
            if (sortItem != null) {
                field = sortItem.getSortField();
            } else {
                field = SortField.UPLOAD_TIME;
            }
            // 更新自己上传的背景
            List<TicketbgInfo> selfTickets = plugin.getTrainDatabaseManager().getTicketbgService().getAllSelfTickets(player.getUniqueId().toString(), field);
            for (int i = 0; i < selfbgItemList.size(); i++) {
                if (i < selfTickets.size()) {
                    selfbgItemList.get(i).setTicketbgInfo(selfTickets.get(i));
                } else {
                    selfbgItemList.get(i).setTicketbgInfo(null);
                }
                selfbgItemList.get(i).notifyWindows();
            }

            // 更新共享的背景
            List<Item> content = new ArrayList<>();
            List<TicketbgInfo> sharedTickets = plugin.getTrainDatabaseManager().getTicketbgService().getAllSharedTickets(field);
            for (TicketbgInfo sharedTicket : sharedTickets) {
                content.add(new SharedbgItem(sharedTicket));
            }
            gui.setContent(content);
        });
    }

    public static void updateAllWindows() {
        for (Map.Entry<UUID, MenuTicketbg> entry : ticketbgMenuMapping.entrySet()) {
            if (entry.getValue().window.isOpen()) {
                entry.getValue().asyncUpdateAllTicketbg();
            }
        }
    }

    @Override
    public void open() {
        asyncUpdateAllTicketbg();
        super.open();
    }

    public static void reload() {
        for (Map.Entry<UUID, MenuTicketbg> entry : ticketbgMenuMapping.entrySet()) {
            entry.getValue().close();
        }
        ticketbgMenuMapping.clear();
    }

    public static MenuTicketbg getMenu(Player player) {
        if (ticketbgMenuMapping.containsKey(player.getUniqueId())) {
            return ticketbgMenuMapping.get(player.getUniqueId());
        } else {
            return new MenuTicketbg(player);
        }
    }
}
