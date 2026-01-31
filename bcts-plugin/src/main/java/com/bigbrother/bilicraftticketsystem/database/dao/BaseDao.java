package com.bigbrother.bilicraftticketsystem.database.dao;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class BaseDao {
    protected final DataSource dataSource;
    protected final BiliCraftTicketSystem plugin;

    protected BaseDao(BiliCraftTicketSystem plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }

    protected void logSQLException(SQLException e) {
        plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
    }

    protected void updatePlayerNameByUuid(String playerUuid, String newName, String tableName) {
        String sql = "UPDATE " + tableName + " SET player_name = ? WHERE player_uuid = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, playerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logSQLException(e);
        }
    }
}
