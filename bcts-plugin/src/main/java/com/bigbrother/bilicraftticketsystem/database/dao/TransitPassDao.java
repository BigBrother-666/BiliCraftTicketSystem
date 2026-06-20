package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
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

    public void updatePlayerNameByUuid(String playerUuid, String newName) {
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME);
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TICKET_BG_TABLE_NAME);
    }

    public void insertTransitPassUsage(String playerUuid, String playerName, String boardingTime, String startStation,
                                       String startNodeId, String endStation, Double maxSpeed, String priceJson, String passType, String cardUuid) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME
                + " (`player_uuid`, `player_name`, `boarding_time`, `start_station`, `start_node_id`, `end_station`, `max_speed`, `price`, `pass_type`, `card_uuid`)"
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
            preparedStatement.setString(5, startNodeId);
            preparedStatement.setString(6, endStation);
            if (maxSpeed == null) {
                preparedStatement.setNull(7, Types.REAL);
            } else {
                preparedStatement.setDouble(7, maxSpeed);
            }
            if (priceJson == null) {
                preparedStatement.setNull(8, Types.VARCHAR);
            } else {
                preparedStatement.setString(8, priceJson);
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
        String sql = "SELECT player_name, player_uuid, boarding_time, start_station, start_node_id, end_station, max_speed, price, pass_type FROM "
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
                        rs.getString("start_node_id"),
                        rs.getString("end_station"),
                        getNullableDouble(rs, "max_speed"),
                        rs.getString("price"),
                        rs.getString("pass_type")
                ));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private Double getNullableDouble(ResultSet rs, String columnLabel) throws SQLException {
        double value = rs.getDouble(columnLabel);
        return rs.wasNull() ? null : value;
    }

    public record DailyUsageRow(String day, int usageCount) {
    }

    public record UsageRecordRow(String playerName, String playerUuid, String boardingTime, String startStation,
                                 String startNodeId, String endStation, Double maxSpeed, String price, String passType) {
        public double getSpeedKph() {
            return CommonUtils.mpt2Kph(maxSpeed);
        }
    }
}
