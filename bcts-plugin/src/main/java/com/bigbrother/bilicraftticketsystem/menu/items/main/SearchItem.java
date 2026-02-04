package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    public ItemProvider getItemProvider(Player player) {
        ItemStack itemStack = CommonUtils.loadItemFromFile("search");
        MenuMain menu = MenuMain.getMenu(player);
        PlayerOption option = menu.getPlayerOption();
        if (!option.isStationNotEmpty()) {
            List<Component> lore = itemStack.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text("搜索不可用，请先选择起始站和终到站", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            itemStack.lore(lore);
        }
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menu = MenuMain.getMenu(player);
        PlayerOption option = menu.getPlayerOption();

        // cooldown 1s
        if (option.isSearchedFlag() || !option.isStationNotEmpty()) {
            return;
        }
        option.setSearchedFlag(true);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> option.setSearchedFlag(false), 20);

        // 异步计算路径并显示结果
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Item> tickets = new ArrayList<>();
            List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(option.getStartStationString(), option.getEndStationString());
            if (pathInfoList.isEmpty()) {
                menu.setTickets(tickets);
                return;
            } else {
                // 显示车票
                for (TrainRoutes.PathInfo pathInfo : pathInfoList) {
                    BCTicket ticket = new BCTicket(option, pathInfo, player);
                    tickets.add(new TicketItem(ticket));
                }
            }
            if (player.isConnected()) {
                menu.setTickets(tickets);
                MenuFilter.getMenu(player).setFilterLocItems(player);
                menu.getFilterItem().notifyWindows();
                notifyWindows();
            }
        });
    }
}
