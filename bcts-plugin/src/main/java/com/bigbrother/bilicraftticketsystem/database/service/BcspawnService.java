package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.database.dao.BcspawnRecordDao;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BcspawnService {
    private final BiliCraftTicketSystem plugin;
    private final BcspawnRecordDao recordDao;

    public BcspawnService(BiliCraftTicketSystem plugin, BcspawnRecordDao recordDao) {
        this.plugin = plugin;
        this.recordDao = recordDao;
    }

    /**
     * 获取全部车站坐标点，供"最近车站"功能按距离检索。
     * <p>
     * 数据来源为 geojson 路由图的车站节点（每个站台节点一项），不再依赖数据库坐标表。
     * 一个站名可能对应多个站台节点，各自独立参与最近距离计算。
     *
     * @return 车站坐标点列表
     */
    public List<BcspawnInfo> getAllBcspawnInfo() {
        List<BcspawnInfo> result = new ArrayList<>();
        for (GeoNode node : GeoRouteEngine.allStationNodes()) {
            String world = node.getWorld();
            if (world == null) {
                continue;
            }
            result.add(new BcspawnInfo(
                    node.getName(),
                    "",
                    "",
                    "",
                    (int) Math.round(node.getX()),
                    (int) Math.round(node.getY()),
                    (int) Math.round(node.getZ()),
                    world
            ));
        }
        return result;
    }

    /**
     * 记录一次发车事件（新 bcspawn 控制牌使用）。
     * <p>
     * 新控制牌直接在牌面携带车站名与线路 id，无需再经图解析。
     * 线路 id 暂存入 spawn_railway 列，方向列留空（数据库 schema 的进一步调整见 Phase 6）。
     *
     * @param station 当前车站名
     * @param lineId  线路 id
     */
    public void recordSpawn(String station, String lineId) {
        if (station == null || station.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                recordDao.insertRecord(nowAsString(), station, "", lineId));
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
