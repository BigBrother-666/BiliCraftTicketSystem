package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一收入表 {@code revenue_info} 的数据访问。
 * <p>
 * 购票款（type=ticket）与交通卡充值款（type=card_charge）都记入此表，营收统计按 amount 汇总。
 */
public class RevenueDao extends BaseDao {

    public static final String TYPE_TICKET = "ticket";
    public static final String TYPE_CARD_CHARGE = "card_charge";

    public RevenueDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public void insertRevenue(String type, String playerUuid, String playerName, String time, double amount, String detail) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.REVENUE_TABLE_NAME
                + " (`time`, `type`, `player_uuid`, `player_name`, `amount`, `detail`) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, time);
            ps.setString(2, type);
            if (playerUuid == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, playerUuid);
            }
            ps.setString(4, playerName);
            ps.setDouble(5, amount);
            ps.setString(6, detail);
            ps.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public List<DailyRevenueRow> findDailyRevenueWithinDays(int n) {
        List<DailyRevenueRow> result = new ArrayList<>();
        String sql = "SELECT DATE(time) AS day, SUM(amount) AS daily_revenue FROM "
                + TrainDatabaseConstants.REVENUE_TABLE_NAME
                + " WHERE time >= DATE('now', ?) GROUP BY day ORDER BY day";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "-" + n + " days");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new DailyRevenueRow(
                        rs.getString("day"),
                        rs.getDouble("daily_revenue")
                ));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public List<RevenueRecordRow> findRevenueRecordsByDate(String date) {
        List<RevenueRecordRow> result = new ArrayList<>();
        String sql = "SELECT time, type, player_name, amount, detail FROM "
                + TrainDatabaseConstants.REVENUE_TABLE_NAME
                + " WHERE DATE(time) = ? ORDER BY time";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new RevenueRecordRow(
                        rs.getString("time"),
                        rs.getString("type"),
                        rs.getString("player_name"),
                        rs.getDouble("amount"),
                        rs.getString("detail")
                ));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public void updatePlayerNameByUuid(String playerUuid, String newName) {
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.REVENUE_TABLE_NAME);
    }

    public record DailyRevenueRow(String day, double dailyRevenue) {
    }

    public record RevenueRecordRow(String time, String type, String playerName, double amount, String detail) {
    }
}
