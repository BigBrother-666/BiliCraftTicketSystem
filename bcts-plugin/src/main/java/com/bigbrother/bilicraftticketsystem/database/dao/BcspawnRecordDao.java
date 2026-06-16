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

public class BcspawnRecordDao extends BaseDao {

    public BcspawnRecordDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public void insertRecord(String spawnTime, String station, String line, String lineName) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.BCSPAWN_TABLE_NAME
                + " (`spawn_time`, `spawn_station`, `spawn_line`, `spawn_line_name`) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, spawnTime);
            preparedStatement.setString(2, station);
            preparedStatement.setString(3, line);
            preparedStatement.setString(4, lineName);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public List<DailySpawnRow> findDailySpawnWithinDays(int n) {
        List<DailySpawnRow> result = new ArrayList<>();
        String sql = "SELECT DATE(spawn_time) AS day, COUNT(*) AS daily_spawn FROM "
                + TrainDatabaseConstants.BCSPAWN_TABLE_NAME
                + " WHERE spawn_time >= DATE('now', ?) GROUP BY day ORDER BY day";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "-" + n + " days");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(new DailySpawnRow(
                        rs.getString("day"),
                        rs.getInt("daily_spawn")
                ));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public List<BcspawnRecordRow> findRecordsByDate(String date) {
        List<BcspawnRecordRow> result = new ArrayList<>();
        String sql = "SELECT spawn_time, spawn_station, spawn_line, spawn_line_name FROM "
                + TrainDatabaseConstants.BCSPAWN_TABLE_NAME
                + " WHERE DATE(spawn_time) = ? ORDER BY spawn_time";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(new BcspawnRecordRow(
                        rs.getString("spawn_time"),
                        rs.getString("spawn_station"),
                        rs.getString("spawn_line"),
                        rs.getString("spawn_line_name")
                ));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }

        return result;
    }

    public record DailySpawnRow(String day, int dailySpawn) {
    }

    public record BcspawnRecordRow(String spawnTime, String spawnStation, String spawnLine, String spawnLineName) {
    }
}
