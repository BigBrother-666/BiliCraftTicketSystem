package com.bigbrother.bilicraftticketsystem.database.service;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.dao.TransitPassDao;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransitPassService {
    private final BiliCraftTicketSystem plugin;
    private final TransitPassDao transitPassDao;

    public TransitPassService(BiliCraftTicketSystem plugin, TransitPassDao transitPassDao) {
        this.plugin = plugin;
        this.transitPassDao = transitPassDao;
    }

    public void addTicketInfo(String playerName, String uuid, double price, CommonTagCompound ticketNbt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (uuid != null) {
                transitPassDao.updatePlayerNameByUuid(uuid, playerName);
            }
            String purchaseTime = nowAsString();
            String startStation = ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, "Unknown");
            String endStation = ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, "Unknown");
            Integer maxUses = ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, Integer.class, null);
            Float maxSpeed = ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, Float.class, null);
            transitPassDao.insertTicket(uuid, playerName, purchaseTime, startStation, endStation, maxUses, maxSpeed, price);
        });
    }

    public Component getDailyRevenue(int days) {
        Component header = Utils.legacyStr2Component("%-20s &7|&6 %-15s".formatted("&6date", "revenue&6"));
        List<TransitPassDao.DailyRevenueRow> rows = transitPassDao.findDailyRevenueWithinDays(days);

        Component result = header;
        for (TransitPassDao.DailyRevenueRow row : rows) {
            String revenue = "%.2f".formatted(row.dailyRevenue());
            Component line = Utils.legacyStr2Component("\n%-15s &7|&6 %-15s".formatted(row.day(), revenue));
            Component hover = getPurchaseRecordsByDate(row.day());
            line = line.hoverEvent(HoverEvent.showText(hover));
            result = result.append(line);
        }

        return result;
    }

    public Component getPurchaseRecordsByDate(String date) {
        Component header = Utils.legacyStr2Component("%-25s &7|&6 %-25s &7|&6 %-10s &7|&6 %-10s &7|&6 %-12s &7|&6 %-12s &7|&6 %-9s".formatted("&6player name", "purchase time", "start", "end", "max uses", "max speed", "price&6"));
        List<TransitPassDao.PurchaseRecordRow> rows = transitPassDao.findPurchaseRecordsByDate(date);
        Component result = header;
        for (TransitPassDao.PurchaseRecordRow row : rows) {
            String maxUses = row.maxUses() == null ? "-" : row.maxUses().toString();
            String maxSpeed = row.maxSpeed() == null ? "-" : "%.2fkm/h".formatted(row.getSpeedKph());
            String price = "%.2f".formatted(row.price());
            result = result.append(Utils.legacyStr2Component("\n%-20s &7|&6 ".formatted(row.playerName())))
                    .append(Utils.legacyStr2Component("%-20s &7|&6 ".formatted(row.purchaseTime())))
                    .append(Utils.legacyStr2Component("%-8s &7|&6 ".formatted(row.startStation())))
                    .append(Utils.legacyStr2Component("%-8s &7|&6 ".formatted(row.endStation())))
                    .append(Utils.legacyStr2Component("%-8s &7|&6 ".formatted(maxUses)))
                    .append(Utils.legacyStr2Component("%-8s &7|&6 ".formatted(maxSpeed)))
                    .append(Utils.legacyStr2Component("%-8s".formatted(price)));
        }
        return result;
    }

    public void addTicketUsage(String playerUuid, String playerName, Double price, String passType, CommonTagCompound ticketNbt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            transitPassDao.updatePlayerNameByUuid(playerUuid, playerName);
            transitPassDao.insertTransitPassUsage(
                    playerUuid,
                    playerName,
                    nowAsString(),
                    ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, null),
                    ticketNbt.getValue(BCTicket.KEY_TICKET_START_PLATFORM_TAG, null),
                    ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, null),
                    ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, null),
                    price,
                    passType,
                    null
            );
        });
    }

    public void addCardUsage(String playerUuid, String playerName, String startStation, String startPlatformTag,
                             String endStation, Double maxSpeed, Double price, String passType, String cardUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> transitPassDao.insertTransitPassUsage(
                playerUuid,
                playerName,
                nowAsString(),
                startStation,
                startPlatformTag,
                endStation,
                maxSpeed,
                price,
                passType,
                cardUuid
        ));
    }

    public Component getDailyTransitPassUsage(int days) {
        Component header = Utils.legacyStr2Component("%-20s &7|&6 %-15s".formatted("&6date", "usage count&6"));
        List<TransitPassDao.DailyUsageRow> rows = transitPassDao.findDailyUsageWithinDays(days);
        Component result = header;
        for (TransitPassDao.DailyUsageRow row : rows) {
            Component line = Utils.legacyStr2Component("\n%-15s &7|&6 %-15s".formatted(row.day(), row.usageCount()));
            line = line.hoverEvent(HoverEvent.showText(getTransitPassUsageRecordsByDate(row.day())));
            result = result.append(line);
        }
        return result;
    }

    public Component getTransitPassUsageRecordsByDate(String date) {
        Component header = Utils.legacyStr2Component(
                "%-18s &7|&6 %-25s &7|&6 %-12s &7|&6 %-14s &7|&6 %-12s &7|&6 %-10s &7|&6 %-8s".formatted(
                        "&6player",
                        "boarding time",
                        "start",
                        "start tag",
                        "end",
                        "max speed",
                        "price&6"
                )
        );
        List<TransitPassDao.UsageRecordRow> rows = transitPassDao.findUsageRecordsByDate(date);
        Component result = header;
        for (TransitPassDao.UsageRecordRow row : rows) {
            String maxSpeed = row.maxSpeed() == null ? "-" : "%.2fkm/h".formatted(row.getSpeedKph());
            String price = row.price() == null ? "-" : "%.2f".formatted(row.price());
            result = result.append(Utils.legacyStr2Component("\n%-16s &7|&6 ".formatted(row.playerName())))
                    .append(Utils.legacyStr2Component("%-23s &7|&6 ".formatted(row.boardingTime())))
                    .append(Utils.legacyStr2Component("%-10s &7|&6 ".formatted(row.startStation())))
                    .append(Utils.legacyStr2Component("%-12s &7|&6 ".formatted(row.startPlatformTag())))
                    .append(Utils.legacyStr2Component("%-10s &7|&6 ".formatted(row.endStation())))
                    .append(Utils.legacyStr2Component("%-8s &7|&6 ".formatted(maxSpeed)))
                    .append(Utils.legacyStr2Component("%-6s".formatted(price)));
            if (row.passType() != null) {
                result = result.append(Utils.legacyStr2Component(" &7|&6 %s".formatted(row.passType())));
            }
        }
        return result;
    }

    private String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
