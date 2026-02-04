package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.database.dao.BcspawnCoordDao;
import com.bigbrother.bilicraftticketsystem.database.dao.BcspawnRecordDao;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BcspawnService {
    private final BiliCraftTicketSystem plugin;
    private final BcspawnCoordDao coordDao;
    private final BcspawnRecordDao recordDao;

    public BcspawnService(BiliCraftTicketSystem plugin, BcspawnCoordDao coordDao, BcspawnRecordDao recordDao) {
        this.plugin = plugin;
        this.coordDao = coordDao;
        this.recordDao = recordDao;
    }

    public void addBcspawnCoord(String startPlatformTag, int x, int y, int z, String world) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MermaidGraph.Node bcSpawnNode = TrainRoutes.graph.getNodeFromPtag(startPlatformTag);
            if (bcSpawnNode == null) {
                return;
            }

            String station = bcSpawnNode.getStationName();
            String railway = bcSpawnNode.getRailwayName();
            String direction = bcSpawnNode.getRailwayDirection();

            if (station == null || railway == null) {
                return;
            }

            BcspawnInfo bcspawnInfo = coordDao.findByLocation(station, railway, direction);
            if (bcspawnInfo == null) {
                coordDao.insertCoord(station, direction, railway, bcSpawnNode.getTag(), x, y, z, world);
            } else if (!(bcspawnInfo.getCoordX() == x && bcspawnInfo.getCoordY() == y && bcspawnInfo.getCoordY() == z)) {
                coordDao.updateCoord(station, direction, railway, x, y, z, world);
            }
        });
    }

    public List<BcspawnInfo> getAllBcspawnInfo() {
        return coordDao.findAll();
    }

    public void addBcspawnInfo(MermaidGraph.Node bcSpawnNode, List<String> dateTime) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (bcSpawnNode == null) {
                return;
            }

            String station = bcSpawnNode.getStationName();
            String railway = bcSpawnNode.getRailwayName();
            String direction = bcSpawnNode.getRailwayDirection();

            if (station == null || railway == null) {
                return;
            }

            for (String d : dateTime) {
                String spawnTime = d != null ? d : nowAsString();
                recordDao.insertRecord(spawnTime, station, direction, railway);
            }
        });
    }

    public void addBcspawnInfo(String startPlatformTag, String dateTime) {
        addBcspawnInfo(TrainRoutes.graph.getNodeFromPtag(startPlatformTag), List.of(dateTime));
    }

    public void addBcspawnInfo(String startPlatformTag) {
        List<String> noDate = new ArrayList<>();
        noDate.add(null);
        addBcspawnInfo(TrainRoutes.graph.getNodeFromPtag(startPlatformTag), noDate);
    }

    public Component getDailySpawn(int days) {
        Component header = CommonUtils.legacyStr2Component("%-20s &7|&6 %-15s".formatted("&6date", "spawn count&6"));
        List<BcspawnRecordDao.DailySpawnRow> rows = recordDao.findDailySpawnWithinDays(days);
        Component result = header;
        for (BcspawnRecordDao.DailySpawnRow row : rows) {
            Component line = CommonUtils.legacyStr2Component("\n%-15s &7|&6 %-15s".formatted(row.day(), row.dailySpawn()));
            line = line.hoverEvent(HoverEvent.showText(getSpawnRecordsByDate(row.day())));
            result = result.append(line);
        }
        return result;
    }

    public Component getSpawnRecordsByDate(String date) {
        Component header = CommonUtils.legacyStr2Component("%-20s &7|&6 %-15s &7|&6 %-15s &7|&6 %-15s".formatted("&6spawn time", "station", "railway", "direction&6"));
        List<BcspawnRecordDao.BcspawnRecordRow> rows = recordDao.findRecordsByDate(date);
        Component result = header;
        for (BcspawnRecordDao.BcspawnRecordRow row : rows) {
            result = result.append(CommonUtils.legacyStr2Component("\n%-20s &7|&6 ".formatted(row.spawnTime())))
                    .append(CommonUtils.legacyStr2Component("%-15s &7|&6 ".formatted(row.spawnStation())))
                    .append(CommonUtils.legacyStr2Component("%-15s &7|&6 ".formatted(row.spawnRailway())))
                    .append(CommonUtils.legacyStr2Component("%-15s".formatted(row.spawnDirection())));
        }
        return result;
    }

    private String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
