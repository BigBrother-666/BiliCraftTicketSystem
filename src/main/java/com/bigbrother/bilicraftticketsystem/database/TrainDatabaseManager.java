package com.bigbrother.bilicraftticketsystem.database;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class TrainDatabaseManager {
    @Getter
    private final HikariDataSource ds;
    private final BiliCraftTicketSystem plugin;
    public static final String ticketTableName = "ticket_info";
    public static final String bcspawnTableName = "bcspawn_info";

    public TrainDatabaseManager(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "data.db");
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("TrainDatabasePool");
        this.ds = new HikariDataSource(config);

        createTable();
    }

    private void createTable() {
        try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
            String sql = """
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
                    """.formatted(ticketTableName);
            statement.execute(sql);
            sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                spawn_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                spawn_station VARCHAR(100),
                                spawn_direction VARCHAR(100),
                                spawn_railway VARCHAR(100)
                    );
                    """.formatted(bcspawnTableName);
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }
    }

    /**
     * 添加一条数据到车票信息表
     *
     * @param playerName 玩家名
     * @param uuid       玩家uuid
     * @param price      总价
     * @param ticketNbt  ticket的nbt
     */
    public void addTicketInfo(String playerName, String uuid, double price, CommonTagCompound ticketNbt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            updatePlayerNameByUuid(uuid, playerName);
            String sql = "INSERT INTO %s (`player_uuid`, `player_name`, `purchase_time`, `start_station`, `end_station`, `max_uses`, `max_speed`, `price`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)".formatted(ticketTableName);
            String[] station = {ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, "Unknown"),ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, "Unknown ")};
            try (Connection connection = ds.getConnection()) {
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, playerName);
                preparedStatement.setString(3, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                preparedStatement.setString(4, station[0]);
                preparedStatement.setString(5, station[1]);
                preparedStatement.setInt(6, ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_NUMBER_OF_USES, Integer.class, null));
                preparedStatement.setFloat(7, ticketNbt.getValue(BCTicket.KEY_TICKET_MAX_SPEED, Float.class, null));
                preparedStatement.setDouble(8, price);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, e.toString());
            }
        });
    }

    public void addBcspawnInfo(String startPlatformTag, List<String> dateTime) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String[] split = startPlatformTag.split("-");
            if (split.length < 2) {
                return;
            }
            String station = null;
            String railway = null;
            String direction = null;
            if (TrainRoutes.getStationTagMap().get(split[0]) == null) {
                return;
            }
            for (String s : TrainRoutes.getStationTagMap().get(split[0])) {
                String[] sp = s.split("-");
                if (sp.length == 3 && sp[2].startsWith(split[1])) {
                    station = sp[0];
                    railway = sp[1];
                    direction = sp[2];
                }
            }

            if (station == null || railway == null) {
                return;
            }

            for (String d : dateTime) {
                String sql = "INSERT INTO %s (`spawn_time`, `spawn_station`, `spawn_direction`, `spawn_railway`) VALUES (?, ?, ?, ?)".formatted(bcspawnTableName);
                try (Connection connection = ds.getConnection()) {
                    PreparedStatement preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setString(1, d != null ? d : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    preparedStatement.setString(2, station);
                    preparedStatement.setString(3, direction);
                    preparedStatement.setString(4, railway);
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, e.toString());
                }
            }
        });
    }

    /**
     * 添加一条数据到列车生成记录表（bcspawn）
     *
     * @param startPlatformTag 站台tag
     * @param dateTime 时间 yyyy-MM-dd HH:mm:ss
     */
    public void addBcspawnInfo(String startPlatformTag, String dateTime) {
        addBcspawnInfo(startPlatformTag, List.of(dateTime));
    }

    /**
     * 添加一条数据到列车生成记录表（bcspawn）
     *
     * @param startPlatformTag 站台tag
     */
    public void addBcspawnInfo(String startPlatformTag) {
        List<String> noDate = new ArrayList<>();
        noDate.add(null);
        addBcspawnInfo(startPlatformTag, noDate);
    }

    /**
     * 更新车票表的玩家名
     * @param playerUuid 玩家uuid
     * @param newName 新名
     */
    public void updatePlayerNameByUuid(String playerUuid, String newName) {
        String sql = "UPDATE %s SET player_name = ? WHERE player_uuid = ?".formatted(ticketTableName);

        try (Connection connection = ds.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, playerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }
    }

    /**
     * 获取n天内的每日营收
     * @param n 天数
     * @return 待输出的Component
     */
    public Component getDailyRevenue(int n) {
        Component result = str2Component("%-20s &7|&6 %-15s".formatted("&6date", "revenue&6"));
        String sql = """
                    SELECT
                        DATE(purchase_time) AS day,
                        SUM(price) AS daily_revenue
                    FROM
                        %s
                    WHERE
                        purchase_time >= DATE('now', '-%d days')
                    GROUP BY
                        day
                    ORDER BY
                        day;
                """.formatted(ticketTableName, n);

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String date = rs.getString("day");
                String revenue = "%.2f".formatted(rs.getDouble("daily_revenue"));
                TextComponent temp = str2Component("\n%-15s &7|&6 %-15s".formatted(date, revenue));
                temp = temp.hoverEvent(HoverEvent.showText(getPurchaseRecordsByDate(date)));
                result = result.append(temp);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }

        return result;
    }

    /**
     * 获取某一天的购买记录
     * @param date 日期
     * @return 待输出的Component
     */
    public Component getPurchaseRecordsByDate(String date) {
        Component result = str2Component("%-25s &7|&6 %-25s &7|&6 %-10s &7|&6 %-10s &7|&6 %-12s &7|&6 %-12s &7|&6 %-9s".formatted("&6player name", "purchase time", "start", "end", "max uses", "max speed", "price&6"));
        String sql = """
                    SELECT
                        player_name,
                        purchase_time,
                        start_station,
                        end_station,
                        max_uses,
                        max_speed,
                        price
                    FROM
                        %s
                    WHERE
                        DATE(purchase_time) = ?
                    ORDER BY
                        purchase_time;
                """.formatted(ticketTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String playerName = rs.getString("player_name");
                String purchaseTime = rs.getString("purchase_time");
                String startStation = rs.getString("start_station");
                String endStation = rs.getString("end_station");
                String maxUses = rs.getString("max_uses");
                String maxSpeed = "%.2fkm/h".formatted(rs.getDouble("max_speed") * 20 * 3.6);
                String price = "%.2f".formatted(rs.getDouble("price"));

                result = result.append(str2Component("\n%-20s &7|&6 ".formatted(playerName)))
                        .append(str2Component("%-20s &7|&6 ".formatted(purchaseTime)))
                        .append(str2Component("%-8s &7|&6 ".formatted(startStation)))
                        .append(str2Component("%-8s &7|&6 ".formatted(endStation)))
                        .append(str2Component("%-8s &7|&6 ".formatted(maxUses)))
                        .append(str2Component("%-8s &7|&6 ".formatted(maxSpeed)))
                        .append(str2Component("%-8s".formatted(price)));

            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }

        return result;
    }

    /**
     * 获取n天内的发车数
     * @param n 天数
     * @return 待输出的Component
     */
    public Component getDailySpawn(int n) {
        Component result = str2Component("%-20s &7|&6 %-15s".formatted("&6date", "spawn count&6"));
        String sql = """
                    SELECT
                        DATE(spawn_time) AS day,
                        COUNT(*) AS daily_spawn
                    FROM
                        %s
                    WHERE
                        spawn_time >= DATE('now', '-%d days')
                    GROUP BY
                        day
                    ORDER BY
                        day;
                """.formatted(bcspawnTableName, n);

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String date = rs.getString("day");
                String spawnCnt = rs.getString("daily_spawn");
                TextComponent temp = str2Component("\n%-15s &7|&6 %-15s".formatted(date, spawnCnt));
                temp = temp.hoverEvent(HoverEvent.showText(getSpawnRecordsByDate(date)));
                result = result.append(temp);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }

        return result;
    }

    /**
     * 获取某一天的发车信息
     * @param date 日期
     * @return 待输出的Component
     */
    public Component getSpawnRecordsByDate(String date) {
        Component result = str2Component("%-20s &7|&6 %-15s &7|&6 %-15s &7|&6 %-15s".formatted( "&6spawn time", "station", "railway", "direction&6"));
        String sql = """
                    SELECT
                        spawn_time,
                        spawn_station,
                        spawn_railway,
                        spawn_direction
                    FROM
                        %s
                    WHERE
                        DATE(spawn_time) = ?
                    ORDER BY
                        spawn_time;
                """.formatted(bcspawnTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String spawnTime = rs.getString("spawn_time");
                String spawnStation = rs.getString("spawn_station");
                String spawnRailway = rs.getString("spawn_railway");
                String spawnDirection = rs.getString("spawn_direction");

                result = result.append(str2Component("\n%-20s &7|&6 ".formatted(spawnTime)))
                        .append(str2Component("%-15s &7|&6 ".formatted(spawnStation)))
                        .append(str2Component("%-15s &7|&6 ".formatted(spawnRailway)))
                        .append(str2Component("%-15s".formatted(spawnDirection)));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, e.toString());
        }

        return result;
    }

    private TextComponent str2Component(String msg) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
    }
}
