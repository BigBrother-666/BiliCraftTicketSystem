package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;
import com.bigbrother.bilicraftticketsystem.database.entity.FullTicketbgInfo;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.items.ticketbg.SortField;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TicketbgDao extends BaseDao {

    public TicketbgDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    public TicketbgInfo findCurrentTicketbg(String uuid) {
        String sql = "SELECT " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + ".id AS id, "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + ".player_uuid AS player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + ", " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME
                + " WHERE " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME + ".player_uuid=? AND "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + ".id=" + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME + ".bg_id";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return toTicketbgInfo(rs);
            }

        } catch (SQLException e) {
            logSQLException(e);
        }
        return null;
    }

    public List<TicketbgInfo> findAllSharedTickets(SortField sortField) {
        List<TicketbgInfo> ticketbgInfoList = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " WHERE shared=? AND deleted=? ORDER BY " + sortField.getField() + " DESC";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, true);
            stmt.setBoolean(2, false);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ticketbgInfoList.add(toTicketbgInfo(rs));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }
        return ticketbgInfoList;
    }

    public List<TicketbgInfo> findAllSelfTickets(String uuid, SortField sortField) {
        List<TicketbgInfo> ticketbgInfoList = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " WHERE player_uuid=? AND deleted=? ORDER BY " + sortField.getField() + " DESC";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setBoolean(2, false);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ticketbgInfoList.add(toTicketbgInfo(rs));
            }

        } catch (SQLException e) {
            logSQLException(e);
        }
        return ticketbgInfoList;
    }

    public int updateUsageTicketbg(Integer bgId, String uuid) {
        String sql = "UPDATE " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME + " SET bg_id=?, usage_time=? WHERE player_uuid=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            setBgId(preparedStatement, 1, bgId);
            preparedStatement.setString(2, nowAsString());
            preparedStatement.setString(3, uuid);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
        return 0;
    }

    public void deleteTicketbg(int bgId) {
        String sql = "DELETE FROM " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " WHERE id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public int logicalDeleteTicketbg(int bgId) {
        String sql = "UPDATE " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " SET deleted=? WHERE id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, true);
            preparedStatement.setInt(2, bgId);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
        return 0;
    }

    public void updateTicketbgUsageCount(int bgId) {
        String sql = "UPDATE " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " SET usage_count=(SELECT COUNT(*) FROM "
                + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME + " WHERE bg_id=?) WHERE id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, bgId);
            preparedStatement.setInt(2, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public FullTicketbgInfo findById(int bgId) {
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, deleted, font_color FROM "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " WHERE id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, bgId);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return new FullTicketbgInfo(
                        rs.getInt("id"),
                        rs.getString("player_name"),
                        rs.getString("player_uuid"),
                        rs.getString("upload_time"),
                        rs.getInt("usage_count"),
                        rs.getString("item_name"),
                        rs.getString("file_path"),
                        rs.getBoolean("shared"),
                        rs.getString("font_color"),
                        rs.getBoolean("deleted")
                );
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return null;
    }

    public void setShared(int bgId, boolean shared) {
        String sql = "UPDATE " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " SET shared=? WHERE id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, shared);
            preparedStatement.setInt(2, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public int countPlayerTicketbg(String uuid) {
        String sql = "SELECT COUNT(*) AS count FROM " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME + " WHERE player_uuid=? AND deleted=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (uuid == null) {
                preparedStatement.setNull(1, Types.VARCHAR);
            } else {
                preparedStatement.setString(1, uuid);
            }
            preparedStatement.setBoolean(2, false);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return -1;
    }

    public void insertTicketbgUsageInfo(String uuid, Integer bgId) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME + " (`player_uuid`, `usage_time`, `bg_id`) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, uuid);
            preparedStatement.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            if (bgId == null) {
                preparedStatement.setNull(3, Types.INTEGER);
            } else {
                preparedStatement.setInt(3, bgId);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public void insertTicketbgInfo(String playerName, String uuid, String itemName, String filePath, String fontColor, boolean shared) {
        String sql = "INSERT INTO " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME
                + " (`player_uuid`, `player_name`, `upload_time`, `usage_count`, `item_name`, `file_path`, `shared`, `font_color`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            setNullableUuid(preparedStatement, 1, uuid);
            preparedStatement.setString(2, playerName);
            preparedStatement.setString(3, nowAsString());
            preparedStatement.setInt(4, 0);
            preparedStatement.setString(5, itemName);
            preparedStatement.setString(6, filePath);
            preparedStatement.setBoolean(7, shared);
            preparedStatement.setString(8, fontColor);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    public void updatePlayerNameByUuid(String playerUuid, String newName) {
        super.updatePlayerNameByUuid(playerUuid, newName, TrainDatabaseConstants.TICKET_BG_TABLE_NAME);
    }

    private void setBgId(PreparedStatement preparedStatement, int index, Integer bgId) throws SQLException {
        if (bgId == null) {
            preparedStatement.setNull(index, Types.INTEGER);
        } else {
            preparedStatement.setInt(index, bgId);
        }
    }

    private void setNullableUuid(PreparedStatement preparedStatement, int index, String uuid) throws SQLException {
        if (uuid == null) {
            preparedStatement.setNull(index, Types.VARCHAR);
        } else {
            preparedStatement.setString(index, uuid);
        }
    }

    private String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private TicketbgInfo toTicketbgInfo(ResultSet rs) throws SQLException {
        return new TicketbgInfo(
                rs.getInt("id"),
                rs.getString("player_name"),
                rs.getString("player_uuid"),
                rs.getString("upload_time"),
                rs.getInt("usage_count"),
                rs.getString("item_name"),
                rs.getString("file_path"),
                rs.getBoolean("shared"),
                rs.getString("font_color")
        );
    }
}
