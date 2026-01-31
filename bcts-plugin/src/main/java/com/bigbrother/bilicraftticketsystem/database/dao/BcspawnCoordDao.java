package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BcspawnCoordDao extends BaseDao {

    public BcspawnCoordDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public void insertCoord(String station, String direction, String railway, String tag, int x, int y, int z, String world) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME
                + " (`spawn_station`, `spawn_direction`, `spawn_railway`, `tag`, `coord_x`, `coord_y`, `coord_z`, `world`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, station);
            preparedStatement.setString(2, direction);
            preparedStatement.setString(3, railway);
            preparedStatement.setString(4, tag);
            preparedStatement.setInt(5, x);
            preparedStatement.setInt(6, y);
            preparedStatement.setInt(7, z);
            preparedStatement.setString(8, world);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public void updateCoord(String station, String direction, String railway, int x, int y, int z, String world) {
        String sql = "UPDATE " + TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME
                + " SET coord_x=?, coord_y=?, coord_z=?, world=? WHERE spawn_station=? AND spawn_direction=? AND spawn_railway=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, x);
            preparedStatement.setInt(2, y);
            preparedStatement.setInt(3, z);
            preparedStatement.setString(4, world);
            preparedStatement.setString(5, station);
            preparedStatement.setString(6, direction);
            preparedStatement.setString(7, railway);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public BcspawnInfo findByCoord(int x, int y, int z) {
        String sql = "SELECT `spawn_station`, `spawn_direction`, `spawn_railway`, `tag`, `coord_x`, `coord_y`, `coord_z`, `world` FROM "
                + TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME + " WHERE coord_x=? AND coord_y=? AND coord_z=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, x);
            preparedStatement.setInt(2, y);
            preparedStatement.setInt(3, z);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return toBcspawnInfo(rs);
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return null;
    }

    public BcspawnInfo findByLocation(String station, String railway, String direction) {
        String sql = "SELECT `spawn_station`, `spawn_direction`, `spawn_railway`, `tag`, `coord_x`, `coord_y`, `coord_z`, `world` FROM "
                + TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME + " WHERE spawn_station=? AND spawn_direction=? AND spawn_railway=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, station);
            preparedStatement.setString(2, direction);
            preparedStatement.setString(3, railway);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return toBcspawnInfo(rs);
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return null;
    }

    public List<BcspawnInfo> findAll() {
        List<BcspawnInfo> result = new ArrayList<>();
        String sql = "SELECT `spawn_station`, `spawn_direction`, `spawn_railway`, `tag`, `coord_x`, `coord_y`, `coord_z`, `world` FROM "
                + TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME;
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                result.add(toBcspawnInfo(rs));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return result;
    }

    private BcspawnInfo toBcspawnInfo(ResultSet rs) throws SQLException {
        return new BcspawnInfo(
                rs.getString("spawn_station"),
                rs.getString("spawn_direction"),
                rs.getString("spawn_railway"),
                rs.getString("tag"),
                rs.getInt("coord_x"),
                rs.getInt("coord_y"),
                rs.getInt("coord_z"),
                rs.getString("world")
        );
    }
}
