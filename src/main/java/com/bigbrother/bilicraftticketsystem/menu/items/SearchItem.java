package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.ArrayList;
import java.util.List;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class SearchItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider() {
        ItemStack itemStack = Utils.loadItemFromFile("search");
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menu = MenuMain.getMenu(player);
        PlayerOption option = menu.getPlayerOption();

        // cooldown 1s
        if (option.isSearchedFlag() || !option.canSearch()) {
            return;
        }
        option.setSearchedFlag(true);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> option.setSearchedFlag(false), 20);

        // 异步计算路径并显示结果
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Item> tickets = new ArrayList<>();
            List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(option.getStartStationString(), option.getEndStationString());
            if (pathInfoList.isEmpty()) {
                tickets.add(new TicketItem(null));
            } else {
                // 显示车票
                for (TrainRoutes.PathInfo pathInfo : pathInfoList) {
                    BCTicket ticket = new BCTicket(option, pathInfo, player);
                    tickets.add(new TicketItem(ticket));
                }
            }
            menu.setTickets(tickets);
            notifyWindows();
        });
    }
}
