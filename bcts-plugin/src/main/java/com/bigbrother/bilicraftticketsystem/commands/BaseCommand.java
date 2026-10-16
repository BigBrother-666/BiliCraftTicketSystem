package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.menu.search.TicketSearcher;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

/**
 * 玩家基础指令：打开购票界面 / 背景设置界面 / 指令快速购票。
 */
public class BaseCommand {
    @CommandDescription("打开车票系统界面")
    @Command("ticket")
    @Permission("bcts.ticket.open")
    public void openTicketGui(Player player) {
        MenuMain.getMenu(player).open();
    }

    @CommandDescription("打开车票系统背景图设置界面")
    @Command("ticketbg")
    @Permission("bcts.ticket.open")
    public void openBgGui(Player player) {
        MenuTicketbg.getMenu(player).open();
    }

    /**
     * 指令快速购票：自动扣费购买起点站到终点站排名第一的车票（直达或联程）。
     * <p>
     * 与购票界面同一套搜索与排序（{@link TicketSearcher}，内部用 {@code TicketRanker} 按距离 / 票价混合排序）。
     * 先提示 {@code ticket-buy-searching}，再在异步线程寻路，扣费与交付车票切回主线程执行。
     * 直达与联程都没有时发送 {@code ticket-buy-no-path} 提示。
     *
     * @param player       购票玩家
     * @param startStation 起点站名
     * @param endStation   终点站名
     */
    @CommandDescription("自动扣费购买一张两站间排名第一的直达或联程车票")
    @Command("ticket buy <startStation> <endStation>")
    @Permission("bcts.ticket.open")
    public void buyTicket(
            Player player,
            @Argument(value = "startStation", description = "起点站名", suggestions = "stationName") String startStation,
            @Argument(value = "endStation", description = "终点站名", suggestions = "stationName") String endStation
    ) {
        player.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("ticket-buy-searching", "正在查找 %s → %s 的路线...")
                        .formatted(startStation, endStation)).decoration(TextDecoration.ITALIC, false)
        ));

        // 速度 / 次数沿用购票界面的默认值（单程票）
        PlayerOption option = new PlayerOption();
        option.setStartStationComponent(Component.text(startStation));
        option.setEndStationComponent(Component.text(endStation));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TicketSearcher.Result result = TicketSearcher.search(option, player);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isConnected()) {
                    return;
                }
                if (result.isEmpty()) {
                    player.sendMessage(MainConfig.prefix.append(
                            CommonUtils.mmStr2Component(message.get("ticket-buy-no-path", "没有 %s 到 %s 的路线")
                                    .formatted(startStation, endStation)).decoration(TextDecoration.ITALIC, false)
                    ));
                    return;
                }
                // 购买排序第一的车票：下标 < 直达数为直达票，否则为联程票
                int best = result.order().getFirst();
                int directCount = result.direct().size();
                if (best < directCount) {
                    result.direct().get(best).purchase();
                } else {
                    result.through().get(best - directCount).purchase();
                }
            });
        });
    }
}
