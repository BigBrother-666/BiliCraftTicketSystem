package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 网页后端事件 outbox 表 {@code web_outbox} 的数据访问。
 * <p>
 * 需可靠送达的事件（revenue.event / ride.event / ride.payment）先写入本表再发送，后端落库后回
 * {@code event.ack}，据此删除；断线期间积压的行在重连后按 {@code created_at} 顺序补发。
 * 所有方法都是阻塞 IO，须在异步线程调用。
 */
public class WebOutboxDao extends BaseDao {
    private static final String TABLE = TrainDatabaseConstants.WEB_OUTBOX_TABLE_NAME;

    public WebOutboxDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        super(plugin, dataSource);
    }

    /**
     * 写入一条事件（event_id 重复则忽略）。
     */
    public void insert(String eventId, String type, String payload, long createdAt) {
        String sql = "INSERT OR IGNORE INTO " + TABLE + " (event_id, type, payload, created_at) VALUES (?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, type);
            ps.setString(3, payload);
            ps.setLong(4, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    /**
     * 删除已确认的事件。
     */
    public void deleteAll(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM " + TABLE + " WHERE event_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (String id : eventIds) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }

    /**
     * 按 (created_at, event_id) 升序分页读取，游标为上一页最后一行的 (createdAt, eventId)。
     *
     * @param afterCreatedAt 游标 created_at（首页传 Long.MIN_VALUE）
     * @param afterEventId   游标 event_id（首页传空串）
     * @param limit          本页最大行数
     * @return 本页行
     */
    public List<OutboxRow> findPage(long afterCreatedAt, String afterEventId, int limit) {
        List<OutboxRow> rows = new ArrayList<>();
        String sql = "SELECT event_id, type, payload, created_at FROM " + TABLE
                + " WHERE created_at > ? OR (created_at = ? AND event_id > ?)"
                + " ORDER BY created_at, event_id LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, afterCreatedAt);
            ps.setLong(2, afterCreatedAt);
            ps.setString(3, afterEventId);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OutboxRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                }
            }
        } catch (SQLException e) {
            logSQLException(e);
        }
        return rows;
    }

    /**
     * 清理过期 / 超量积压：删除早于 {@code minCreatedAt} 的行，并只保留最新的 {@code maxRows} 行。
     *
     * @return 删除的行数
     */
    public int prune(long minCreatedAt, int maxRows) {
        int deleted = 0;
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            deleted += st.executeUpdate("DELETE FROM " + TABLE + " WHERE created_at < " + minCreatedAt);
            deleted += st.executeUpdate("DELETE FROM " + TABLE + " WHERE event_id NOT IN (SELECT event_id FROM "
                    + TABLE + " ORDER BY created_at DESC, event_id DESC LIMIT " + maxRows + ")");
        } catch (SQLException e) {
            logSQLException(e);
        }
        return deleted;
    }

    public record OutboxRow(String eventId, String type, String payload, long createdAt) {
    }
}
