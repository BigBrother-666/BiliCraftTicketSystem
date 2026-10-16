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
 * 历史营收数据只读访问，供 {@code /ticketadmin weblink backfill} 一次性同步到网页后端。
 * 所有方法都是阻塞 IO，须在异步线程调用。
 */
public class LegacyRevenueDao extends BaseDao {

    public LegacyRevenueDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    /** revenue_info 一行（购票 / 交通卡充值流水）。time 为 "yyyy-MM-dd HH:mm:ss" 本地时间文本。 */
    public record RevenueRow(long id, String time, String type, String playerUuid, String playerName,
                             double amount, String detail) {
    }

    /** transit_pass_usage_info 中一次刷卡乘车（pass_type=card）。price 为 {系统id:{price,distance}} JSON。 */
    public record CardRideRow(long id, String boardingTime, String playerUuid, String playerName,
                              String startStation, String endStation, String priceJson) {
    }

    /**
     * 分页读取 revenue_info（按 id 升序，id &gt; afterId）。
     */
    public List<RevenueRow> findRevenuePage(long afterId, int limit) {
        List<RevenueRow> out = new ArrayList<>();
        String sql = "SELECT id, time, type, player_uuid, player_name, amount, detail FROM "
                + TrainDatabaseConstants.REVENUE_TABLE_NAME + " WHERE id > ? ORDER BY id LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, afterId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RevenueRow(rs.getLong("id"), rs.getString("time"), rs.getString("type"),
                            rs.getString("player_uuid"), rs.getString("player_name"),
                            rs.getDouble("amount"), rs.getString("detail")));
                }
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return out;
    }

    /**
     * 分页读取刷卡乘车记录（按 id 升序，id &gt; afterId）。
     */
    public List<CardRideRow> findCardRidePage(long afterId, int limit) {
        List<CardRideRow> out = new ArrayList<>();
        String sql = "SELECT id, boarding_time, player_uuid, player_name, start_station, end_station, price FROM "
                + TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME
                + " WHERE id > ? AND pass_type = 'card' ORDER BY id LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, afterId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CardRideRow(rs.getLong("id"), rs.getString("boarding_time"),
                            rs.getString("player_uuid"), rs.getString("player_name"),
                            rs.getString("start_station"), rs.getString("end_station"), rs.getString("price")));
                }
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return out;
    }

    /**
     * 本轮回填的截止 id（命令执行瞬间各表的最大 id），之后产生的数据已由实时链路上报，不再回填。
     *
     * @return [revenue_info 最大 id, transit_pass_usage_info 最大 id]
     */
    public long[] maxIds() {
        long[] ids = new long[2];
        String[] tables = {TrainDatabaseConstants.REVENUE_TABLE_NAME, TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME};
        try (Connection c = dataSource.getConnection()) {
            for (int i = 0; i < tables.length; i++) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM " + tables[i]);
                     ResultSet rs = ps.executeQuery()) {
                    ids[i] = rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return ids;
    }
}
