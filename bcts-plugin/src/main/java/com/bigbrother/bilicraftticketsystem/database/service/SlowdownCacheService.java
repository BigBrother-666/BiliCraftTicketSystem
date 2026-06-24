package com.bigbrother.bilicraftticketsystem.database.service;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.dao.SlowdownCacheDao;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * slowdown 减速距离缓存服务：内存缓存 + 异步持久化。
 * <p>
 * slowdown 控制牌每次预测 slowdown 到 platform 的距离开销较大，故缓存计算结果。内存以
 * <b>slowdown 铁轨坐标</b>为 key，值为该 slowdown 的所有缓存条目列表（含车种、到达 platform 坐标、
 * 车站名、距离）。支持三类查询：
 * <ul>
 *   <li>{@link #getCommon}：普通车站站停，固定减速到下一个 platform，按 slowdown 坐标取其普通车条目。</li>
 *   <li>{@link #getExpress}：直达车按 {@code (slowdown 坐标, 终点 platform 坐标)} 精确取——同站多站台
 *       站名相同，必须用坐标区分。</li>
 *   <li>{@link #getAnyStation}：取该 slowdown 任意一条记录的车站名，供直达车「中间站快速跳过」
 *       （终点站名 != 记录站名即中间站，无需探测）。</li>
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
     * 内存缓存：key = slowdown 铁轨坐标 key，value = 该 slowdown 的所有缓存条目。
     */
    private final ConcurrentHashMap<String, List<Entry>> cache = new ConcurrentHashMap<>();

    public SlowdownCacheService(BiliCraftTicketSystem plugin, SlowdownCacheDao slowdownCacheDao) {
        this.plugin = plugin;
        this.slowdownCacheDao = slowdownCacheDao;
        reload();
    }

    /**
     * 一条缓存条目（隶属某 slowdown）。
     *
     * @param trainType 车种（{@link #TRAIN_TYPE_EXPRESS} / {@link #TRAIN_TYPE_COMMON}）
     * @param station   到达的 platform 车站名
     * @param platformX platform 铁轨方块 x
     * @param platformY platform 铁轨方块 y
     * @param platformZ platform 铁轨方块 z
     * @param distance  slowdown 到 platform 的距离（block）
     */
    public record Entry(String trainType, String station, int platformX, int platformY, int platformZ,
                        double distance) {
    }

    /**
     * 从数据库重新载入全部缓存到内存（启动 / 重载调用）。
     */
    public void reload() {
        cache.clear();
        for (SlowdownCacheDao.SlowdownCacheRow row : slowdownCacheDao.findAll()) {
            String railKey = railKey(row.world(), row.x(), row.y(), row.z());
            cache.computeIfAbsent(railKey, k -> new CopyOnWriteArrayList<>())
                    .add(new Entry(row.trainType(), row.station(), row.platformX(), row.platformY(),
                            row.platformZ(), row.distance()));
        }
    }

    /**
     * 取该 slowdown 任意一条缓存记录的车站名（用于直达车「中间站快速跳过」）。
     * <p>
     * 一个 slowdown 物理上只通向一个车站，故任意一条记录的站名都代表该 slowdown 的目标车站。
     *
     * @param rail slowdown 所在铁轨方块
     * @return 车站名；该 slowdown 尚无任何缓存返回 null
     */
    public String getAnyStation(Block rail) {
        List<Entry> entries = cache.get(railKey(rail));
        if (entries == null) {
            return null;
        }
        for (Entry e : entries) {
            if (e.station() != null && !e.station().isEmpty()) {
                return e.station();
            }
        }
        return null;
    }

    /**
     * 查询普通车缓存（按 slowdown 坐标取其普通车条目）。
     *
     * @param rail slowdown 所在铁轨方块
     * @return 普通车缓存条目；未命中返回 null
     */
    public Entry getCommon(Block rail) {
        List<Entry> entries = cache.get(railKey(rail));
        if (entries == null) {
            return null;
        }
        for (Entry e : entries) {
            if (TRAIN_TYPE_COMMON.equals(e.trainType())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 查询直达车缓存（按 slowdown 坐标 + 终点 platform 铁轨坐标精确匹配）。
     *
     * @param rail      slowdown 所在铁轨方块
     * @param platformX 终点 platform 铁轨方块 x
     * @param platformY 终点 platform 铁轨方块 y
     * @param platformZ 终点 platform 铁轨方块 z
     * @return 直达车缓存条目；未命中返回 null
     */
    public Entry getExpress(Block rail, int platformX, int platformY, int platformZ) {
        List<Entry> entries = cache.get(railKey(rail));
        if (entries == null) {
            return null;
        }
        for (Entry e : entries) {
            if (TRAIN_TYPE_EXPRESS.equals(e.trainType())
                    && e.platformX() == platformX && e.platformY() == platformY && e.platformZ() == platformZ) {
                return e;
            }
        }
        return null;
    }

    /**
     * 写入普通车缓存（内存即时生效，数据库异步落盘）。
     *
     * @param rail      slowdown 所在铁轨方块
     * @param station   到达的 platform 站名
     * @param platform  到达的 platform 铁轨方块
     * @param distance  slowdown 到 platform 的距离（block）
     */
    public void putCommon(Block rail, String station, Block platform, double distance) {
        put(rail, TRAIN_TYPE_COMMON, station, platform, distance);
    }

    /**
     * 写入直达车缓存（内存即时生效，数据库异步落盘）。
     *
     * @param rail      slowdown 所在铁轨方块
     * @param station   到达的 platform 站名
     * @param platform  到达的 platform 铁轨方块（终点站台）
     * @param distance  slowdown 到 platform 的距离（block）
     */
    public void putExpress(Block rail, String station, Block platform, double distance) {
        put(rail, TRAIN_TYPE_EXPRESS, station, platform, distance);
    }

    /**
     * 写入一条缓存：更新内存（同车种 + 同 platform 坐标视为同一条，覆盖）并异步落盘。
     */
    private void put(Block rail, String trainType, String station, Block platform, double distance) {
        String railKey = railKey(rail);
        int px = platform.getX();
        int py = platform.getY();
        int pz = platform.getZ();
        Entry entry = new Entry(trainType, station, px, py, pz, distance);
        List<Entry> entries = cache.computeIfAbsent(railKey, k -> new CopyOnWriteArrayList<>());
        // 移除同车种 + 同 platform 的旧条目再加入新条目（与数据库唯一键口径一致）
        entries.removeIf(e -> e.trainType().equals(trainType)
                && e.platformX() == px && e.platformY() == py && e.platformZ() == pz);
        entries.add(entry);

        String world = rail.getWorld().getName();
        int x = rail.getX();
        int y = rail.getY();
        int z = rail.getZ();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                slowdownCacheDao.upsert(world, x, y, z, trainType, station, px, py, pz, distance));
    }

    /**
     * slowdown 铁轨方块 -> 坐标 key。
     */
    private String railKey(Block rail) {
        return railKey(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ());
    }

    private String railKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
