package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * slowdown 控制牌减速距离缓存的数据访问。
 * <p>
 * 每行记录一块 slowdown 控制牌（按所在铁轨方块坐标定位）对某<b>车种</b>（直达 / 普通），到达某
 * <b>platform</b>（按 platform 铁轨坐标定位）时，slowdown 到 platform 的减速距离；并附带该 platform
 * 的车站名 {@code station}（供直达车「中间站快速跳过」）。计算开销较大，故持久化以复用。
 */
public class SlowdownCacheDao extends BaseDao {

    public SlowdownCacheDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    /**
     * 写入 / 更新一条缓存（按 world,x,y,z,train_type,platform_x,platform_y,platform_z 唯一键 upsert）。
     *
     * @param world     slowdown 铁轨所在世界名
     * @param x         slowdown 铁轨方块 x
     * @param y         slowdown 铁轨方块 y
     * @param z         slowdown 铁轨方块 z
     * @param trainType 车种（{@code express} / {@code common}）
     * @param station   到达的 platform 车站名
     * @param platformX platform 铁轨方块 x
     * @param platformY platform 铁轨方块 y
     * @param platformZ platform 铁轨方块 z
     * @param distance  slowdown 到 platform 的距离（block）
     */
    public void upsert(String world, int x, int y, int z, String trainType, String station,
                       int platformX, int platformY, int platformZ, double distance) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.SLOWDOWN_CACHE_TABLE_NAME
                + " (world, x, y, z, train_type, station, platform_x, platform_y, platform_z, distance)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT(world, x, y, z, train_type, platform_x, platform_y, platform_z)"
                + " DO UPDATE SET station = excluded.station, distance = excluded.distance";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setString(5, trainType);
            ps.setString(6, station);
            ps.setInt(7, platformX);
            ps.setInt(8, platformY);
            ps.setInt(9, platformZ);
            ps.setDouble(10, distance);
            ps.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    /**
     * 读取全部缓存行（启动 / 重载时载入内存）。
     *
     * @return 缓存行列表
     */
    public List<SlowdownCacheRow> findAll() {
        List<SlowdownCacheRow> result = new ArrayList<>();
        String sql = "SELECT world, x, y, z, train_type, station, platform_x, platform_y, platform_z, distance FROM "
                + TrainDatabaseConstants.SLOWDOWN_CACHE_TABLE_NAME;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new SlowdownCacheRow(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("train_type"),
                        rs.getString("station"),
                        rs.getInt("platform_x"),
                        rs.getInt("platform_y"),
                        rs.getInt("platform_z"),
                        rs.getDouble("distance")
                ));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return result;
    }

    public record SlowdownCacheRow(String world, int x, int y, int z, String trainType, String station,
                                   int platformX, int platformY, int platformZ, double distance) {
    }
}
