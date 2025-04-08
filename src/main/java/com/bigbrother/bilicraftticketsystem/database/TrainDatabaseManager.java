package com.bigbrother.bilicraftticketsystem.database;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.entity.FullTicketbgInfo;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.menu.items.ticketbg.SortField;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrainDatabaseManager {
    @Getter
    private final HikariDataSource ds;
    private final BiliCraftTicketSystem plugin;
    public static final String ticketTableName = "ticket_info";
    public static final String bcspawnTableName = "bcspawn_info";
    public static final String ticketbgTableName = "ticketbg_info";
    public static final String ticketbgUsageTableName = "ticketbg_usage_info";

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

            sql = """
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
                    """.formatted(ticketbgTableName);
            statement.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS idx_%s_player_uuid ON %s (player_uuid);".formatted(ticketbgTableName, ticketbgTableName);
            statement.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS idx_%s_upload_time ON %s (upload_time);".formatted(ticketbgTableName, ticketbgTableName);
            statement.execute(sql);

            sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                player_uuid VARCHAR(36) UNIQUE,
                                usage_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                bg_id INTEGER
                    );
                    """.formatted(ticketbgUsageTableName);
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 获取玩家当前使用的车票背景信息
     *
     * @param uuid 玩家uuid
     * @return 当前使用的车票背景信息
     */
    @Nullable
    public TicketbgInfo getCurrTicketbgInfo(String uuid) {
        if (MenuTicketbg.getTicketbgUsageMapping().containsKey(UUID.fromString(uuid))) {
            return MenuTicketbg.getTicketbgUsageMapping().get(UUID.fromString(uuid));
        }

        String sql = "SELECT %s.id AS id, %s.player_uuid AS player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM %s,%s WHERE %s.player_uuid=? AND %s.id=%s.bg_id"
                .formatted(ticketbgTableName, ticketbgTableName, ticketbgTableName, ticketbgUsageTableName, ticketbgUsageTableName, ticketbgTableName, ticketbgUsageTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
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

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
        return null;
    }

    /**
     * 获取所有共享的车票背景
     *
     * @return 所有共享的车票背景
     */
    public List<TicketbgInfo> getAllSharedTickets(SortField sortField) {
        List<TicketbgInfo> ticketbgInfoList = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM %s WHERE shared=? AND deleted=? ORDER BY %s DESC"
                .formatted(ticketbgTableName, sortField.getField());

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, true);
            stmt.setBoolean(2, false);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ticketbgInfoList.add(new TicketbgInfo(
                        rs.getInt("id"),
                        rs.getString("player_name"),
                        rs.getString("player_uuid"),
                        rs.getString("upload_time"),
                        rs.getInt("usage_count"),
                        rs.getString("item_name"),
                        rs.getString("file_path"),
                        rs.getBoolean("shared"),
                        rs.getString("font_color")
                ));
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
        return ticketbgInfoList;
    }

    /**
     * 获取自己的车票背景
     *
     * @return 自己的车票背景
     */
    public List<TicketbgInfo> getAllSelfTickets(String uuid, SortField sortField) {
        List<TicketbgInfo> ticketbgInfoList = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, font_color FROM %s WHERE player_uuid=? AND deleted=? ORDER BY %s DESC"
                .formatted(ticketbgTableName, sortField.getField());

        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setBoolean(2, false);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ticketbgInfoList.add(new TicketbgInfo(
                        rs.getInt("id"),
                        rs.getString("player_name"),
                        rs.getString("player_uuid"),
                        rs.getString("upload_time"),
                        rs.getInt("usage_count"),
                        rs.getString("item_name"),
                        rs.getString("file_path"),
                        rs.getBoolean("shared"),
                        rs.getString("font_color")
                ));
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
        return ticketbgInfoList;
    }

    /**
     * 更新当前使用的车票背景
     *
     * @param bgId 更新后的背景id  null: 默认背景
     * @param uuid 玩家uuid
     */
    public void updateUsageTicketbg(@Nullable Integer bgId, String uuid) {
        try (Connection connection = ds.getConnection()) {
            // 更新
            String sql = "UPDATE %s SET bg_id=?, usage_time=? WHERE player_uuid=?".formatted(ticketbgUsageTableName);
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            if (bgId == null) {
                preparedStatement.setNull(1, Types.INTEGER);
            } else {
                preparedStatement.setInt(1, bgId);
            }
            preparedStatement.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            preparedStatement.setString(3, uuid);
            if (preparedStatement.executeUpdate() == 0) {
                // 没有该玩家的数据，插入
                addTicketbgUsageInfo(uuid, bgId);
            }

            // 旧背景图使用人数-1
            TicketbgInfo info = getCurrTicketbgInfo(uuid);
            if (info != null) {
                updateTicketbgUsageCount(info.getId());
            }

            // 新背景图使用人数+1
            if (bgId != null) {
                updateTicketbgUsageCount(bgId);
            }
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 删除上传的车票背景的数据库记录
     *
     * @param bgId 车票背景id
     */
    private void deleteTicketbg(int bgId) {
        String sql = "DELETE FROM %s WHERE id=?".formatted(ticketbgTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 删除上传的车票背景（逻辑删除）
     *
     * @param bgId 车票背景id
     */
    public int deleteTicketbgLogical(int bgId) {
        String sql = "UPDATE %s SET deleted=? WHERE id=?".formatted(ticketbgTableName);
        int ret = 0;
        // 逻辑删除
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setBoolean(1, true);
            preparedStatement.setInt(2, bgId);
            ret = preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        FullTicketbgInfo info = getTicketInfoById(bgId);
        // 如果使用人数为0则彻底删除
        if (info != null && info.isDeleted() && info.getUsageCount() <= 0) {
            deleteTicketbg(bgId);
            Utils.deleteTicketbg(info.getFilePath());
        }

        return ret;
    }

    /**
     * 刷新车票背景的使用人数
     *
     * @param bgId 车票背景id
     */
    public void updateTicketbgUsageCount(int bgId) {
        String sql = "UPDATE %s SET usage_count=(SELECT COUNT(*) FROM %s WHERE bg_id=?) WHERE id=?".formatted(ticketbgTableName, ticketbgUsageTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, bgId);
            preparedStatement.setInt(2, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        FullTicketbgInfo info = getTicketInfoById(bgId);
        if (info != null && info.isDeleted() && info.getUsageCount() <= 0) {
            deleteTicketbg(bgId);
            Utils.deleteTicketbg(info.getFilePath());
        }
    }


    /**
     * 根据背景id获取背景信息
     *
     * @param bgId 背景id
     * @return 背景信息
     */
    @Nullable
    private FullTicketbgInfo getTicketInfoById(int bgId) {
        String sql = "SELECT id, player_uuid, player_name, upload_time, usage_count, item_name, file_path, shared, deleted, font_color FROM %s WHERE id=?".formatted(ticketbgTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
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
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
        return null;
    }

    /**
     * 设置车票公开分享
     *
     * @param bgId   车票背景id
     * @param shared 分享状态
     */
    public void setShared(int bgId, boolean shared) {
        String sql = "UPDATE %s SET shared=? WHERE id=?".formatted(ticketbgTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setBoolean(1, shared);
            preparedStatement.setInt(2, bgId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 获取玩家上传的背景数量
     *
     * @param uuid 玩家uuid。null表示管理员
     * @return 上传的背景数量，-1表示没有查到
     */
    public int getPlayerTicketbgCount(@Nullable String uuid) {
        String sql = "SELECT COUNT(*) AS count FROM %s WHERE player_uuid=? AND deleted=?".formatted(ticketbgTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
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
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
        return -1;
    }

    /**
     * 添加一条数据到车票背景使用信息表
     *
     * @param uuid 玩家uuid
     * @param bgId 车票背景id
     */
    public void addTicketbgUsageInfo(String uuid, @Nullable Integer bgId) {
        String sql = "INSERT INTO %s (`player_uuid`, `usage_time`, `bg_id`) VALUES (?, ?, ?)".formatted(ticketbgUsageTableName);
        try (Connection connection = ds.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, uuid);
            preparedStatement.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            if (bgId == null) {
                preparedStatement.setNull(3, Types.INTEGER);
            } else {
                preparedStatement.setInt(3, bgId);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 添加一条数据到车票背景信息表
     *
     * @param playerName 玩家名
     * @param uuid       玩家uuid
     * @param itemName   背景名
     * @param filePath   文件路径
     * @param fontColor  车票字体16进制颜色
     */
    public void addTicketbgInfo(String playerName, String uuid, String itemName, String filePath, String fontColor) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            updatePlayerNameByUuid(uuid, playerName, ticketbgTableName);
            String sql = "INSERT INTO %s (`player_uuid`, `player_name`, `upload_time`, `usage_count`, `item_name`, `file_path`, `shared`, `font_color`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)".formatted(ticketbgTableName);
            try (Connection connection = ds.getConnection()) {
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                if (uuid == null) {
                    preparedStatement.setNull(1, Types.VARCHAR);
                    preparedStatement.setBoolean(7, true);
                } else {
                    preparedStatement.setString(1, uuid);
                    preparedStatement.setBoolean(7, false);
                }
                preparedStatement.setString(2, playerName);
                preparedStatement.setString(3, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                preparedStatement.setInt(4, 0);
                preparedStatement.setString(5, itemName);
                preparedStatement.setString(6, filePath);
                preparedStatement.setString(8, fontColor);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
            }
        });
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
            updatePlayerNameByUuid(uuid, playerName, ticketTableName);
            String sql = "INSERT INTO %s (`player_uuid`, `player_name`, `purchase_time`, `start_station`, `end_station`, `max_uses`, `max_speed`, `price`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)".formatted(ticketTableName);
            String[] station = {ticketNbt.getValue(BCTicket.KEY_TICKET_START_STATION, "Unknown"), ticketNbt.getValue(BCTicket.KEY_TICKET_END_STATION, "Unknown ")};
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
                plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
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
                    plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
                }
            }
        });
    }

    /**
     * 添加一条数据到列车生成记录表（bcspawn）
     *
     * @param startPlatformTag 站台tag
     * @param dateTime         时间 yyyy-MM-dd HH:mm:ss
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
     *
     * @param playerUuid 玩家uuid
     * @param newName    新名
     */
    public void updatePlayerNameByUuid(String playerUuid, String newName, String tableName) {
        if (playerUuid == null) {
            return;
        }

        String sql = "UPDATE %s SET player_name = ? WHERE player_uuid = ?".formatted(tableName);
        try (Connection connection = ds.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, playerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }
    }

    /**
     * 获取n天内的每日营收
     *
     * @param n 天数
     * @return 待输出的Component
     */
    public Component getDailyRevenue(int n) {
        Component result = Utils.str2Component("%-20s &7|&6 %-15s".formatted("&6date", "revenue&6"));
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
                Component temp = Utils.str2Component("\n%-15s &7|&6 %-15s".formatted(date, revenue));
                temp = temp.hoverEvent(HoverEvent.showText(getPurchaseRecordsByDate(date)));
                result = result.append(temp);
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        return result;
    }

    /**
     * 获取某一天的购买记录
     *
     * @param date 日期
     * @return 待输出的Component
     */
    public Component getPurchaseRecordsByDate(String date) {
        Component result = Utils.str2Component("%-25s &7|&6 %-25s &7|&6 %-10s &7|&6 %-10s &7|&6 %-12s &7|&6 %-12s &7|&6 %-9s".formatted("&6player name", "purchase time", "start", "end", "max uses", "max speed", "price&6"));
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

                result = result.append(Utils.str2Component("\n%-20s &7|&6 ".formatted(playerName)))
                        .append(Utils.str2Component("%-20s &7|&6 ".formatted(purchaseTime)))
                        .append(Utils.str2Component("%-8s &7|&6 ".formatted(startStation)))
                        .append(Utils.str2Component("%-8s &7|&6 ".formatted(endStation)))
                        .append(Utils.str2Component("%-8s &7|&6 ".formatted(maxUses)))
                        .append(Utils.str2Component("%-8s &7|&6 ".formatted(maxSpeed)))
                        .append(Utils.str2Component("%-8s".formatted(price)));

            }
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        return result;
    }

    /**
     * 获取n天内的发车数
     *
     * @param n 天数
     * @return 待输出的Component
     */
    public Component getDailySpawn(int n) {
        Component result = Utils.str2Component("%-20s &7|&6 %-15s".formatted("&6date", "spawn count&6"));
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
                Component temp = Utils.str2Component("\n%-15s &7|&6 %-15s".formatted(date, spawnCnt));
                temp = temp.hoverEvent(HoverEvent.showText(getSpawnRecordsByDate(date)));
                result = result.append(temp);
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        return result;
    }

    /**
     * 获取某一天的发车信息
     *
     * @param date 日期
     * @return 待输出的Component
     */
    public Component getSpawnRecordsByDate(String date) {
        Component result = Utils.str2Component("%-20s &7|&6 %-15s &7|&6 %-15s &7|&6 %-15s".formatted("&6spawn time", "station", "railway", "direction&6"));
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

                result = result.append(Utils.str2Component("\n%-20s &7|&6 ".formatted(spawnTime)))
                        .append(Utils.str2Component("%-15s &7|&6 ".formatted(spawnStation)))
                        .append(Utils.str2Component("%-15s &7|&6 ".formatted(spawnRailway)))
                        .append(Utils.str2Component("%-15s".formatted(spawnDirection)));
            }
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.YELLOW));
        }

        return result;
    }
}
