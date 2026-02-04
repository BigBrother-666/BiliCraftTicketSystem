package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;
import com.bigbrother.bilicraftticketsystem.database.entity.CardInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class CardInfoDao extends BaseDao {

    public CardInfoDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public void insert(CardInfo cardInfo) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME
                + " (card_uuid, start_station, mm_start_station, end_station, mm_end_station, max_speed, balance)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardInfo.getCardUuid());
            stmt.setString(2, cardInfo.getStartStation());
            stmt.setString(3, cardInfo.getMmStartStation());
            stmt.setString(4, cardInfo.getEndStation());
            stmt.setString(5, cardInfo.getMmEndStation());
            if (cardInfo.getMaxSpeed() == null) {
                stmt.setNull(6, Types.REAL);
            } else {
                stmt.setDouble(6, cardInfo.getMaxSpeed());
            }
            if (cardInfo.getBalance() == null) {
                stmt.setNull(7, Types.REAL);
            } else {
                stmt.setDouble(7, cardInfo.getBalance());
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public CardInfo findByCardUuid(String cardUuid) {
        String sql = "SELECT id, card_uuid, start_station, mm_start_station, end_station, mm_end_station, max_speed, balance"
                + " FROM " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME + " WHERE card_uuid = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardUuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapToCardInfo(rs);
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return null;
    }

    public List<CardInfo> findAll() {
        String sql = "SELECT id, card_uuid, start_station, mm_start_station, end_station, mm_end_station, max_speed, balance"
                + " FROM " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME;
        List<CardInfo> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(mapToCardInfo(rs));
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return result;
    }

    public int updateByCardUuid(CardInfo cardInfo) {
        String sql = "UPDATE " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME
                + " SET start_station = ?, mm_start_station = ?, end_station = ?, mm_end_station = ?, max_speed = ?, balance = ?"
                + " WHERE card_uuid = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardInfo.getStartStation());
            stmt.setString(2, cardInfo.getMmStartStation());
            stmt.setString(3, cardInfo.getEndStation());
            stmt.setString(4, cardInfo.getMmEndStation());
            if (cardInfo.getMaxSpeed() == null) {
                stmt.setNull(5, Types.REAL);
            } else {
                stmt.setDouble(5, cardInfo.getMaxSpeed());
            }
            if (cardInfo.getBalance() == null) {
                stmt.setNull(6, Types.REAL);
            } else {
                stmt.setDouble(6, cardInfo.getBalance());
            }
            stmt.setString(7, cardInfo.getCardUuid());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
        return 0;
    }

    public int deleteByCardUuid(String cardUuid) {
        String sql = "DELETE FROM " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME + " WHERE card_uuid = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardUuid);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
        return 0;
    }

    private CardInfo mapToCardInfo(ResultSet rs) throws SQLException {
        return new CardInfo(
                rs.getInt("id"),
                rs.getString("card_uuid"),
                rs.getString("start_station"),
                rs.getString("mm_start_station"),
                rs.getString("end_station"),
                rs.getString("mm_end_station"),
                getNullableDouble(rs, "max_speed"),
                getNullableDouble(rs, "balance")
        );
    }

    private Double getNullableDouble(ResultSet rs, String columnLabel) throws SQLException {
        double value = rs.getDouble(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
