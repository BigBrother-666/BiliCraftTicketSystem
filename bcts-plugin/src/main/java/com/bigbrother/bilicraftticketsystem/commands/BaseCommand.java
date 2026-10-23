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
import org.jetbrains.annotations.Nullable;

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
     * <p>
     * 可选速度单位为 km/h，须在 config {@code speed.min ~ speed.max} 范围内，超出时发送
     * {@code ticket-buy-speed-out-of-range} 提示且不购票；不填则沿用购票界面的默认速度。
     *
     * @param player       购票玩家
     * @param startStation 起点站名
     * @param endStation   终点站名
     * @param speed        最大速度（km/h），可为 null
     */
    @CommandDescription("自动扣费购买一张两站间排名第一的直达或联程车票")
    @Command("ticket buy <startStation> <endStation> [speed]")
    @Permission("bcts.ticket.open")
    public void buyTicket(
            Player player,
            @Argument(value = "startStation", description = "起点站名", suggestions = "stationName") String startStation,
            @Argument(value = "endStation", description = "终点站名", suggestions = "stationName") String endStation,
            @Nullable @Argument(value = "speed", description = "最大速度（km/h）") Double speed
    ) {
        if (speed != null) {
            double minKph = CommonUtils.mpt2Kph(MainConfig.minSpeed);
            double maxKph = CommonUtils.mpt2Kph(MainConfig.maxSpeed);
            // 容差
            if (!(speed >= minKph - 1e-6 && speed <= maxKph + 1e-6)) {
                player.sendMessage(MainConfig.prefix.append(
                        CommonUtils.mmStr2Component(message.get("ticket-buy-speed-out-of-range", "速度须在 %.1f ~ %.1f km/h 之间")
                                .formatted(minKph, maxKph)).decoration(TextDecoration.ITALIC, false)
                ));
                return;
            }
        }

        player.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("ticket-buy-searching", "正在查找 %s → %s 的路线...")
                        .formatted(startStation, endStation)).decoration(TextDecoration.ITALIC, false)
        ));

        // 未指定速度时速度 / 次数沿用购票界面的默认值（单程票）
        PlayerOption option = new PlayerOption();
        option.setStartStationComponent(Component.text(startStation));
        option.setEndStationComponent(Component.text(endStation));
        if (speed != null) {
            option.setSpeed(CommonUtils.kph2Mpt(speed));
        }

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
