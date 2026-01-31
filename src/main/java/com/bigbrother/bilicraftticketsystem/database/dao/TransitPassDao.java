package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TransitPassDao extends BaseDao {

    public TransitPassDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public void insertTicket(String playerUuid, String playerName, String purchaseTime, String startStation, String endStation, Integer maxUses, Float maxSpeed, double price) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.TICKET_TABLE_NAME
                + " (`player_uuid`, `player_name`, `purchase_time`, `start_station`, `end_station`, `max_uses`, `max_speed`, `price`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (playerUuid == null) {
                preparedStatement.setNull(1, Types.VARCHAR);
            } else {
                preparedStatement.setString(1, playerUuid);
            }
            preparedStatement.setString(2, playerName);
            preparedStatement.setString(3, purchaseTime);
            preparedStatement.setString(4, startStation);
            preparedStatement.setString(5, endStation);
            if (maxUses == null) {
                preparedStatement.setNull(6, Types.INTEGER);
            } else {
                preparedStatement.setInt(6, maxUses);
            }
            if (maxSpeed == null) {
                preparedStatement.setNull(7, Types.REAL);
            } else {
                preparedStatement.setFloat(7, maxSpeed);
            }
            preparedStatement.setDouble(8, price);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public void updatePlayerNameByUuid(String playerUuid, String newName) {
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TICKET_TABLE_NAME);
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME);
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TICKET_BG_TABLE_NAME);
    }

    public List<DailyRevenueRow> findDailyRevenueWithinDays(int n) {
        List<DailyRevenueRow> result = new ArrayList<>();
        String sql = "SELECT DATE(purchase_time) AS day, SUM(price) AS daily_revenue FROM "
                + TrainDatabaseConstants.TICKET_TABLE_NAME
                + " WHERE purchase_time >= DATE('now', ?) GROUP BY day ORDER BY day";

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

    public List<PurchaseRecordRow> findPurchaseRecordsByDate(String date) {
        List<PurchaseRecordRow> result = new ArrayList<>();
        String sql = "SELECT player_name, purchase_time, start_station, end_station, max_uses, max_speed, price FROM "
                + TrainDatabaseConstants.TICKET_TABLE_NAME
                + " WHERE DATE(purchase_time) = ? ORDER BY purchase_time";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(new PurchaseRecordRow(
                        rs.getString("player_name"),
                        rs.getString("purchase_time"),
                        rs.getString("start_station"),
                        rs.getString("end_station"),
                        getNullableInteger(rs, "max_uses"),
                        getNullableDouble(rs, "max_speed"),
                        rs.getDouble("price")
                ));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public void insertTransitPassUsage(String playerUuid, String playerName, String boardingTime, String startStation,
                                       String startPlatformTag, String endStation, Double maxSpeed, Double price, String passType, String cardUuid) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME
                + " (`player_uuid`, `player_name`, `boarding_time`, `start_station`, `start_platform_tag`, `end_station`, `max_speed`, `price`, `pass_type`, `card_uuid`)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (playerUuid == null) {
                preparedStatement.setNull(1, Types.VARCHAR);
            } else {
                preparedStatement.setString(1, playerUuid);
            }
            preparedStatement.setString(2, playerName);
            preparedStatement.setString(3, boardingTime);
            preparedStatement.setString(4, startStation);
            preparedStatement.setString(5, startPlatformTag);
            preparedStatement.setString(6, endStation);
            if (maxSpeed == null) {
                preparedStatement.setNull(7, Types.REAL);
            } else {
                preparedStatement.setDouble(7, maxSpeed);
            }
            if (price == null) {
                preparedStatement.setNull(8, Types.REAL);
            } else {
                preparedStatement.setDouble(8, price);
            }
            preparedStatement.setString(9, passType);
            preparedStatement.setString(10, cardUuid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public List<DailyUsageRow> findDailyUsageWithinDays(int n) {
        List<DailyUsageRow> result = new ArrayList<>();
        String sql = "SELECT DATE(boarding_time) AS day, COUNT(*) AS usage_count FROM "
                + TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME
                + " WHERE boarding_time >= DATE('now', ?) GROUP BY day ORDER BY day";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "-" + n + " days");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(new DailyUsageRow(
                        rs.getString("day"),
                        rs.getInt("usage_count")
                ));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public List<UsageRecordRow> findUsageRecordsByDate(String date) {
        List<UsageRecordRow> result = new ArrayList<>();
        String sql = "SELECT player_name, player_uuid, boarding_time, start_station, start_platform_tag, end_station, max_speed, price, pass_type FROM "
                + TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME
                + " WHERE DATE(boarding_time) = ? ORDER BY boarding_time";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(new UsageRecordRow(
                        rs.getString("player_name"),
                        rs.getString("player_uuid"),
                        rs.getString("boarding_time"),
                        rs.getString("start_station"),
                        rs.getString("start_platform_tag"),
                        rs.getString("end_station"),
                        getNullableDouble(rs, "max_speed"),
                        getNullableDouble(rs, "price"),
                        rs.getString("pass_type")
                ));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    private Integer getNullableInteger(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(ResultSet rs, String columnLabel) throws SQLException {
        double value = rs.getDouble(columnLabel);
        return rs.wasNull() ? null : value;
    }

    public record DailyRevenueRow(String day, double dailyRevenue) {
    }

    public record PurchaseRecordRow(String playerName, String purchaseTime, String startStation, String endStation, Integer maxUses, Double maxSpeed, double price) {
        public double getSpeedKph() {
            return Utils.mpt2Kph(maxSpeed);
        }
    }

    public record DailyUsageRow(String day, int usageCount) {
    }

    public record UsageRecordRow(String playerName, String playerUuid, String boardingTime, String startStation,
                                 String startPlatformTag, String endStation, Double maxSpeed, Double price, String passType) {
        public double getSpeedKph() {
            return Utils.mpt2Kph(maxSpeed);
        }
    }
}
