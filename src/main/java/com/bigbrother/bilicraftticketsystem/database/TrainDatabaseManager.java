package com.bigbrother.bilicraftticketsystem.database;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.database.dao.BcspawnCoordDao;
import com.bigbrother.bilicraftticketsystem.database.dao.BcspawnRecordDao;
import com.bigbrother.bilicraftticketsystem.database.dao.TransitPassDao;
import com.bigbrother.bilicraftticketsystem.database.dao.TicketbgDao;
import com.bigbrother.bilicraftticketsystem.database.service.BcspawnService;
import com.bigbrother.bilicraftticketsystem.database.service.TransitPassService;
import com.bigbrother.bilicraftticketsystem.database.service.TicketbgService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class TrainDatabaseManager {
    @Getter
    private final HikariDataSource ds;
    private final BiliCraftTicketSystem plugin;
    private final TicketbgDao ticketbgDao;
    private final TransitPassDao transitPassDao;
    private final BcspawnCoordDao bcspawnCoordDao;
    private final BcspawnRecordDao bcspawnRecordDao;
    @Getter
    private final TicketbgService ticketbgService;
    @Getter
    private final TransitPassService transitPassService;
    @Getter
    private final BcspawnService bcspawnService;

    public TrainDatabaseManager(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "data.db");
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("TrainDatabasePool");
        this.ds = new HikariDataSource(config);

        createTables();

        this.ticketbgDao = new TicketbgDao(plugin, ds);
        this.transitPassDao = new TransitPassDao(plugin, ds);
        this.bcspawnCoordDao = new BcspawnCoordDao(plugin, ds);
        this.bcspawnRecordDao = new BcspawnRecordDao(plugin, ds);

        this.ticketbgService = new TicketbgService(plugin, ticketbgDao);
        this.transitPassService = new TransitPassService(plugin, transitPassDao);
        this.bcspawnService = new BcspawnService(plugin, bcspawnCoordDao, bcspawnRecordDao);
    }

    private void createTables() {
        try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                player_uuid VARCHAR(36),
                                player_name VARCHAR(36),
                                purchase_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                start_station VARCHAR(100),
                                end_station VARCHAR(100),
                                max_uses INTEGER,
                                max_speed REAL,
                                price REAL
                    );
                    """.formatted(TrainDatabaseConstants.TICKET_TABLE_NAME));

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                spawn_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                spawn_station VARCHAR(100),
                                spawn_direction VARCHAR(100),
                                spawn_railway VARCHAR(100)
                    );
                    """.formatted(TrainDatabaseConstants.BCSPAWN_TABLE_NAME));

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                player_uuid VARCHAR(36),
                                player_name VARCHAR(36),
                                font_color VARCHAR(10) DEFAULT '#000000',
                                upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                usage_count INTEGER,
                                item_name VARCHAR(256),
                                file_path VARCHAR(96),
                                shared BOOLEAN DEFAULT FALSE,
                                deleted BOOLEAN DEFAULT FALSE
                    );
                    """.formatted(TrainDatabaseConstants.TICKET_BG_TABLE_NAME));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_player_uuid ON %s (player_uuid);".formatted(TrainDatabaseConstants.TICKET_BG_TABLE_NAME, TrainDatabaseConstants.TICKET_BG_TABLE_NAME));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_upload_time ON %s (upload_time);".formatted(TrainDatabaseConstants.TICKET_BG_TABLE_NAME, TrainDatabaseConstants.TICKET_BG_TABLE_NAME));

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                player_uuid VARCHAR(36) UNIQUE,
                                usage_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                bg_id INTEGER
                    );
                    """.formatted(TrainDatabaseConstants.TICKET_BG_USAGE_TABLE_NAME));

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                spawn_station VARCHAR(100),
                                spawn_direction VARCHAR(100),
                                spawn_railway VARCHAR(100),
                                tag VARCHAR(20),
                                coord_x INTEGER,
                                coord_y INTEGER,
                                coord_z INTEGER,
                                world VARCHAR(50)
                    );
                    """.formatted(TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_bcspawn ON %s (spawn_station, spawn_direction, spawn_railway);".formatted(TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME, TrainDatabaseConstants.BCSPAWN_COORD_TABLE_NAME));

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                player_uuid VARCHAR(36),
                                player_name VARCHAR(36),
                                boarding_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                start_station VARCHAR(100),
                                start_platform_tag VARCHAR(100),
                                end_station VARCHAR(100),
                                max_speed REAL,
                                price REAL,
                                pass_type VARCHAR(16)
                    );
                    """.formatted(TrainDatabaseConstants.TRANSIT_PASS_USAGE_TABLE_NAME));
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    public void close() {
        ds.close();
    }
}
