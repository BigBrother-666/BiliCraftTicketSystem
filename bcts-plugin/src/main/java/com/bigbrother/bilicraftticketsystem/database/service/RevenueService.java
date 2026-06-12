package com.bigbrother.bilicraftticketsystem.database.service;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.dao.RevenueDao;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 统一收入服务：购票款与交通卡充值款都记入 {@code revenue_info}，并提供营收统计展示。
 */
public class RevenueService {
    private final BiliCraftTicketSystem plugin;
    private final RevenueDao revenueDao;

    public RevenueService(BiliCraftTicketSystem plugin, RevenueDao revenueDao) {
        this.plugin = plugin;
        this.revenueDao = revenueDao;
    }

    /**
     * 记录一笔购票收入。
     *
     * @param playerName 玩家名
     * @param uuid       玩家 uuid（可空）
     * @param price      购票金额
     * @param ticketNbt  车票 NBT（取起止站作为明细）
     */
    public void recordTicketPurchase(String playerName, String uuid, double price, CommonTagCompound ticketNbt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (uuid != null) {
                revenueDao.updatePlayerNameByUuid(uuid, playerName);
            }
            String startStation = ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, "Unknown");
            String endStation = ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, "Unknown");
            String detail = startStation + " -> " + endStation;
            revenueDao.insertRevenue(RevenueDao.TYPE_TICKET, uuid, playerName, nowAsString(), price, detail);
        });
    }

    /**
     * 记录一笔交通卡充值收入。
     *
     * @param playerUuid 玩家 uuid
     * @param playerName 玩家名
     * @param amount     充值金额
     * @param cardUuid   交通卡 uuid（作为明细）
     */
    public void recordCardCharge(String playerUuid, String playerName, double amount, String cardUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                revenueDao.insertRevenue(RevenueDao.TYPE_CARD_CHARGE, playerUuid, playerName, nowAsString(), amount, cardUuid));
    }

    public Component getDailyRevenue(int days) {
        Component header = CommonUtils.legacyStr2Component("%-20s &7|&6 %-15s".formatted("&6date", "revenue&6"));
        List<RevenueDao.DailyRevenueRow> rows = revenueDao.findDailyRevenueWithinDays(days);

        Component result = header;
        for (RevenueDao.DailyRevenueRow row : rows) {
            String revenue = "%.2f".formatted(row.dailyRevenue());
            Component line = CommonUtils.legacyStr2Component("\n%-15s &7|&6 %-15s".formatted(row.day(), revenue));
            line = line.hoverEvent(HoverEvent.showText(getRevenueRecordsByDate(row.day())));
            result = result.append(line);
        }

        return result;
    }

    private Component getRevenueRecordsByDate(String date) {
        Component header = CommonUtils.legacyStr2Component("%-25s &7|&6 %-14s &7|&6 %-20s &7|&6 %-10s &7|&6 %-20s".formatted(
                "&6time", "type", "player", "amount", "detail&6"));
        List<RevenueDao.RevenueRecordRow> rows = revenueDao.findRevenueRecordsByDate(date);
        Component result = header;
        for (RevenueDao.RevenueRecordRow row : rows) {
            String amount = "%.2f".formatted(row.amount());
            result = result.append(CommonUtils.legacyStr2Component("\n%-20s &7|&6 ".formatted(row.time())))
                    .append(CommonUtils.legacyStr2Component("%-12s &7|&6 ".formatted(row.type())))
                    .append(CommonUtils.legacyStr2Component("%-16s &7|&6 ".formatted(row.playerName())))
                    .append(CommonUtils.legacyStr2Component("%-8s &7|&6 ".formatted(amount)))
                    .append(CommonUtils.legacyStr2Component("%-16s".formatted(row.detail() == null ? "-" : row.detail())));
        }
        return result;
    }

    private String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
