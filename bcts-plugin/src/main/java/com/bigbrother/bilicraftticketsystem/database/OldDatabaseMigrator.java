package com.bigbrother.bilicraftticketsystem.database;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 旧数据库（{@code data.db}）到新库（{@code bcts.db}）的一次性数据迁移。
 * <p>
 * 只迁移玩家资产类数据：交通卡（{@code card_info}）、车票背景上传记录（{@code ticketbg_info}）、
 * 车票背景使用记录（{@code ticketbg_usage_info}）。运营统计类历史（发车 / 营收 / 乘车）不迁移，
 * 新库为准。
 * <p>
 * 采用 {@code INSERT OR IGNORE}：三张表各自有唯一约束（card_uuid / id / player_uuid），
 * 新库已存在同主键记录则跳过，因此可重复执行而不产生重复或覆盖。
 */
public class OldDatabaseMigrator {
    private final BiliCraftTicketSystem plugin;
    private final DataSource newDs;

    public OldDatabaseMigrator(BiliCraftTicketSystem plugin, DataSource newDs) {
        this.plugin = plugin;
        this.newDs = newDs;
    }

    /**
     * 旧库文件是否存在。
     */
    public boolean oldDatabaseExists() {
        return oldDbFile().isFile();
    }

    private File oldDbFile() {
        return new File(plugin.getDataFolder(), "data.db");
    }

    /**
     * 执行迁移（应在异步线程调用）。
     *
     * @return 各表迁移行数
     */
    public Result migrate() {
        Result result = new Result();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + oldDbFile().getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("OldDbMigratePool");
        try (HikariDataSource oldDs = new HikariDataSource(config)) {
            result.cardInfo = migrateCardInfo(oldDs);
            // 先迁背景信息（带 id），再迁使用记录（bg_id 引用 ticketbg_info.id）
            result.ticketbgInfo = migrateTicketbgInfo(oldDs);
            result.ticketbgUsage = migrateTicketbgUsage(oldDs);
        } catch (Exception e) {
            plugin.getComponentLogger().warn(net.kyori.adventure.text.Component.text(
                    "迁移失败：" + e, net.kyori.adventure.text.format.NamedTextColor.RED));
            result.failed = true;
        }
        return result;
    }

    private int migrateCardInfo(DataSource oldDs) {
        String select = "SELECT card_uuid, start_station, mm_start_station, end_station, mm_end_station, max_speed, balance FROM "
                + TrainDatabaseConstants.CARD_INFO_TABLE_NAME;
        String insert = "INSERT OR IGNORE INTO " + TrainDatabaseConstants.CARD_INFO_TABLE_NAME
                + " (card_uuid, start_station, mm_start_station, end_station, mm_end_station, max_speed, balance)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        int count = 0;
        try (Connection oldConn = oldDs.getConnection();
             PreparedStatement read = oldConn.prepareStatement(select);
             ResultSet rs = read.executeQuery();
             Connection newConn = newDs.getConnection();
             PreparedStatement write = newConn.prepareStatement(insert)) {
            while (rs.next()) {
                try {
                    write.setString(1, rs.getString("card_uuid"));
                    write.setString(2, rs.getString("start_station"));
                    write.setString(3, rs.getString("mm_start_station"));
                    write.setString(4, rs.getString("end_station"));
                    write.setString(5, rs.getString("mm_end_station"));
                    write.setDouble(6, rs.getDouble("max_speed"));
                    write.setDouble(7, rs.getDouble("balance"));
                    count += write.executeUpdate();
                } catch (SQLException rowEx) {
                    logRow(rowEx);
                }
            }
        } catch (SQLException e) {
            logTable("card_info", e);
        }
        return count;
    }

    private int migrateTicketbgInfo(DataSource oldDs) {
        // 保留原 id 以维持 ticketbg_usage_info.bg_id 的引用关系
        String select = "SELECT id, player_uuid, player_name, font_color, upload_time, usage_count, item_name, file_path, shared, deleted FROM "
                + TrainDatabaseConstants.TICKET_BG_TABLE_NAME;
        String insert = "INSERT OR IGNORE INTO " + TrainDatabaseConstants.TICKET_BG_TABLE_NAME
                + " (id, player_uuid, player_name, font_color, upload_time, usage_count, item_name, file_path, shared, deleted)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int count = 0;
        try (Connection oldConn = oldDs.getConnection();
             PreparedStatement read = oldConn.prepareStatement(select);
             ResultSet rs = read.executeQuery();
             Connection newConn = newDs.getConnection();
             PreparedStatement write = newConn.prepareStatement(insert)) {
            while (rs.next()) {
                try {
                    write.setInt(1, rs.getInt("id"));
                    write.setString(2, rs.getString("player_uuid"));
                    write.setString(3, rs.getString("player_name"));
                    write.setString(4, rs.getString("font_color"));
                    write.setString(5, rs.getString("upload_time"));
                    write.setInt(6, rs.getInt("usage_count"));
                    write.setString(7, rs.getString("item_name"));
                    write.setString(8, rs.getString("file_path"));
                    write.setBoolean(9, rs.getBoolean("shared"));
                    write.setBoolean(10, rs.getBoolean("deleted"));
                    count += write.executeUpdate();
                } catch (SQLException rowEx) {
                    logRow(rowEx);
                }
            }
        } catch (SQLException e) {
            logTable("ticketbg_info", e);
        }
        return count;
    }

    private int migrateTicketbgUsage(DataSource oldDs) {
        String select = "SELECT player_uuid, usage_time, bg_id FROM " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME;
        String insert = "INSERT OR IGNORE INTO " + TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME
                + " (player_uuid, usage_time, bg_id) VALUES (?, ?, ?)";
        int count = 0;
        try (Connection oldConn = oldDs.getConnection();
             PreparedStatement read = oldConn.prepareStatement(select);
             ResultSet rs = read.executeQuery();
             Connection newConn = newDs.getConnection();
             PreparedStatement write = newConn.prepareStatement(insert)) {
            while (rs.next()) {
                try {
                    write.setString(1, rs.getString("player_uuid"));
                    write.setString(2, rs.getString("usage_time"));
                    write.setInt(3, rs.getInt("bg_id"));
                    count += write.executeUpdate();
                } catch (SQLException rowEx) {
                    logRow(rowEx);
                }
            }
        } catch (SQLException e) {
            logTable("ticketbg_usage_info", e);
        }
        return count;
    }

    private void logTable(String table, SQLException e) {
        plugin.getComponentLogger().warn(net.kyori.adventure.text.Component.text(
                "迁移 " + table + " 出错：" + e, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    }

    private void logRow(SQLException e) {
        plugin.getComponentLogger().warn(net.kyori.adventure.text.Component.text(
                "迁移单行出错（已跳过）：" + e, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    }

    /**
     * 迁移结果统计。
     */
    public static class Result {
        public boolean failed = false;
        public int cardInfo = 0;
        public int ticketbgInfo = 0;
        public int ticketbgUsage = 0;
    }
}
