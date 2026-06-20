package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.dao.SlowdownCacheDao;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * slowdown 减速距离缓存服务：内存缓存 + 异步持久化。
 * <p>
 * slowdown 控制牌每次预测 slowdown 到 platform 的距离开销较大，故按车种缓存计算结果。命中直接返回，
 * 未命中由控制牌实时预测后调用 {@code put*} 写回（内存即时可见，数据库异步落盘）。两种车种的缓存
 * 口径不同：
 * <ul>
 *   <li><b>普通车</b>站站停车，固定减速到下一个 platform，故每块 slowdown 只有一条缓存
 *       （按铁轨坐标定位，到达车站名仅记录不参与查询）。</li>
 *   <li><b>直达车</b>跨站直达，需按<b>终点车站名</b>区分（同一 slowdown 对不同终点的直达车减速距离不同），
 *       故缓存按 {@code (铁轨坐标, 车站名)} 定位。</li>
 * </ul>
 * 缓存在插件启动与重载时由构造方法 {@link #reload} 从数据库整体载入。
 */
public class SlowdownCacheService {
    /**
     * 直达车车种标识（写入数据库 train_type 列）。
     */
    public static final String TRAIN_TYPE_EXPRESS = "express";
    /**
     * 普通车车种标识（写入数据库 train_type 列）。
     */
    public static final String TRAIN_TYPE_COMMON = "common";

    private final BiliCraftTicketSystem plugin;
    private final SlowdownCacheDao slowdownCacheDao;
    /**
     * 普通车缓存：key = 铁轨坐标 key，value = 下一个 platform 的站名 + 减速距离。
     */
    private final ConcurrentHashMap<String, CommonResult> commonCache = new ConcurrentHashMap<>();
    /**
     * 直达车缓存：key = 铁轨坐标 key + 终点车站名，value = 减速距离（block）。
     */
    private final ConcurrentHashMap<String, Double> expressCache = new ConcurrentHashMap<>();

    public SlowdownCacheService(BiliCraftTicketSystem plugin, SlowdownCacheDao slowdownCacheDao) {
        this.plugin = plugin;
        this.slowdownCacheDao = slowdownCacheDao;
        reload();
    }

    /**
     * 普通车缓存结果。
     *
     * @param station  下一个 platform 站名（仅记录，不参与查询）
     * @param distance slowdown 到 platform 的距离（block）
     */
    public record CommonResult(String station, double distance) {
    }

    /**
     * 从数据库重新载入全部缓存到内存（启动 / 重载调用）。
     */
    public void reload() {
        commonCache.clear();
        expressCache.clear();
        List<SlowdownCacheDao.SlowdownCacheRow> rows = slowdownCacheDao.findAll();
        for (SlowdownCacheDao.SlowdownCacheRow row : rows) {
            String railKey = railKey(row.world(), row.x(), row.y(), row.z());
            if (TRAIN_TYPE_EXPRESS.equals(row.trainType())) {
                expressCache.put(railKey + "|" + nullToEmpty(row.station()), row.distance());
            } else {
                commonCache.put(railKey, new CommonResult(row.station(), row.distance()));
            }
        }
    }

    /**
     * 查询普通车缓存（按铁轨坐标，与到达车站名无关）。
     *
     * @param rail slowdown 所在铁轨方块
     * @return 缓存结果（站名 + 距离）；未命中返回 null
     */
    public CommonResult getCommon(Block rail) {
        return commonCache.get(railKey(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ()));
    }

    /**
     * 查询直达车缓存（按铁轨坐标 + 终点车站名）。
     *
     * @param rail    slowdown 所在铁轨方块
     * @param station 直达车终点车站名
     * @return 缓存的减速距离（block）；未命中返回 null
     */
    public Double getExpress(Block rail, String station) {
        return expressCache.get(railKey(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ())
                + "|" + nullToEmpty(station));
    }

    /**
     * 写入普通车缓存（内存即时生效，数据库异步落盘）。
     *
     * @param rail     slowdown 所在铁轨方块
     * @param station  到达的 platform 站名（记录用）
     * @param distance slowdown 到 platform 的距离（block）
     */
    public void putCommon(Block rail, String station, double distance) {
        commonCache.put(railKey(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ()),
                new CommonResult(station, distance));
        persist(rail, TRAIN_TYPE_COMMON, station, distance);
    }

    /**
     * 写入直达车缓存（内存即时生效，数据库异步落盘）。
     *
     * @param rail     slowdown 所在铁轨方块
     * @param station  到达的 platform 站名（直达车按此区分终点）
     * @param distance slowdown 到 platform 的距离（block）
     */
    public void putExpress(Block rail, String station, double distance) {
        expressCache.put(railKey(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ())
                + "|" + nullToEmpty(station), distance);
        persist(rail, TRAIN_TYPE_EXPRESS, station, distance);
    }

    /**
     * 异步把一条缓存写入数据库。
     */
    private void persist(Block rail, String trainType, String station, double distance) {
        String world = rail.getWorld().getName();
        int x = rail.getX();
        int y = rail.getY();
        int z = rail.getZ();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                slowdownCacheDao.upsert(world, x, y, z, trainType, nullToEmpty(station), distance));
    }

    /**
     * 生成铁轨坐标 key。
     */
    private String railKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
