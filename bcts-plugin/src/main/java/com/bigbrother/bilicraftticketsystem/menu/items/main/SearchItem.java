package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.search.TicketRanker;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.ticket.ThroughTicket;
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
            String start = option.getStartStationString();
            String end = option.getEndStationString();

            // 直达候选池：只需覆盖「距离前 N ∪ 票价前 M」，取两者之和作上限即可满足混合排序，
            // 避免不限条数(k=16)在大图上的重 KSP。任一维度不限(<=0)时退回不限条数。
            int directPool;
            if (MainConfig.maxDistanceResults <= 0 || MainConfig.maxPriceResults <= 0) {
                directPool = 0;
            } else {
                directPool = MainConfig.maxDistanceResults + MainConfig.maxPriceResults;
            }
            List<BCTicket> directTickets = new ArrayList<>();
            for (GeoRoutePath path : GeoRouteEngine.findByStation(start, end, directPool)) {
                directTickets.add(new BCTicket(option, path, player));
            }
            // 换乘（联程）候选：限方案数 + 限候选换乘站数，防止大线组合爆炸
            List<ThroughTicket> throughTickets = new ArrayList<>();
            for (JourneyPlan plan : GeoRouteEngine.findTransferJourneys(start, end,
                    MainConfig.maxTransferResults, MainConfig.maxTransferCandidates,
                    MainConfig.transferMinImprovement)) {
                throughTickets.add(new ThroughTicket(option, plan, player));
            }

            // 汇总为排序候选：直达票在前、联程票在后（下标空间连续）
            List<TicketRanker.Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < directTickets.size(); i++) {
                BCTicket t = directTickets.get(i);
                candidates.add(new TicketRanker.Candidate(i, t.getPathInfo().getDistance(), t.getPrice()));
            }
            for (int i = 0; i < throughTickets.size(); i++) {
                ThroughTicket t = throughTickets.get(i);
                candidates.add(new TicketRanker.Candidate(directTickets.size() + i, t.getTotalDistance(), t.getTotalPrice()));
            }

            List<Integer> order = TicketRanker.rankWithMinDirect(candidates, directTickets.size(),
                    MainConfig.maxDistanceResults, MainConfig.maxPriceResults,
                    MainConfig.searchWeightDistance, MainConfig.searchWeightPrice,
                    MainConfig.minDirectResults);

            List<Item> tickets = new ArrayList<>();
            for (int idx : order) {
                if (idx < directTickets.size()) {
                    tickets.add(new TicketItem(directTickets.get(idx)));
                } else {
                    tickets.add(new ThroughTicketItem(throughTickets.get(idx - directTickets.size())));
                }
            }

            if (player.isConnected()) {
                menu.setTickets(tickets);
                notifyWindows();
            }
        });
    }
}
