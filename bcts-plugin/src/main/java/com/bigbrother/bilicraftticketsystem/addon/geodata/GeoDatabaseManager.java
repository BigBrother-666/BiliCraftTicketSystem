package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.addon.geodata.entity.GeoManualLine;
import com.bigbrother.bilicraftticketsystem.addon.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoWalkingPoint;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.sql.*;

public class GeoDatabaseManager {
    private final static String geoNodeLocTableName = "geo_node_loc";
    private final static String geoManualLineTableName = "geo_manual_line";

    private final BiliCraftTicketSystem plugin;
    private final HikariDataSource ds;

    public GeoDatabaseManager(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
        this.ds = plugin.getTrainDatabaseManager().getDs();

        createTable();
    }

    private void createTable() {
        try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
            String sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                platform_tag VARCHAR(32) UNIQUE,
                                start_loc VARCHAR(255),
                                start_direction VARCHAR(255)
                    );
                    """.formatted(geoNodeLocTableName);
            statement.execute(sql);
            sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                platform_tag VARCHAR(32),
                                line_type VARCHAR(16),
                                start_loc VARCHAR(255),
                                start_direction VARCHAR(255),
                                end_loc VARCHAR(255),
                        UNIQUE(platform_tag, line_type)
                    );
                    """.formatted(geoManualLineTableName);
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
    }

    /**
     * 添加节点的开始铁轨位置和方向，如果节点数据已存在则更新
     *
     * @param platformTag 节点的站台tag
     * @param loc         节点开始铁轨坐标
     * @param direction   节点开始方向
     */
    public void upsertGeoNodeLoc(String platformTag, Location loc, Vector direction) {
        String sql = """
                INSERT OR REPLACE INTO %s (id, platform_tag, start_loc, start_direction)
                VALUES (
                    (SELECT id FROM %s WHERE platform_tag = ?),
                    ?, ?, ?
                );
                """.formatted(geoNodeLocTableName, geoNodeLocTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            ps.setString(2, platformTag);
            ps.setString(3, serializeLocation(loc));
            ps.setString(4, serializeVector(direction));
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
    }

    /**
     * 根据节点站台tag获取节点的开始铁轨信息
     *
     * @param platformTag 节点站台tag
     * @return 开始铁轨信息
     */
    public GeoNodeLoc getGeoNodeLoc(String platformTag) {
        String sql = """
                SELECT platform_tag, start_loc, start_direction
                FROM %s WHERE platform_tag = ?
                """.formatted(geoNodeLocTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String startLoc = rs.getString("start_loc");
                String startDir = rs.getString("start_direction");

                return new GeoNodeLoc(
                        rs.getString("platform_tag"),
                        deserializeLocation(startLoc),
                        deserializeVector(startDir)
                );
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }

        return null;
    }

    /**
     * 添加手动指定的线路，如果线路数据已存在则更新
     *
     * @param platformTag    节点站台tag
     * @param lineType       线路类型
     * @param startLoc       起始铁轨坐标
     * @param startDirection 起始铁轨方向
     * @param endLoc         终止铁轨坐标
     */
    public void upsertGeoManualLine(
            String platformTag,
            PRGeoWalkingPoint.LineType lineType,
            Location startLoc,
            Vector startDirection,
            Location endLoc
    ) {
        String sql = """
                INSERT OR REPLACE INTO %s (id, platform_tag, line_type, start_loc, start_direction, end_loc)
                VALUES (
                    (SELECT id FROM %s WHERE platform_tag = ? AND line_type = ?),
                    ?, ?, ?, ?, ?
                );
                """.formatted(geoManualLineTableName, geoManualLineTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            ps.setString(2, lineType.getType());

            ps.setString(3, platformTag);
            ps.setString(4, lineType.getType());
            ps.setString(5, serializeLocation(startLoc));
            ps.setString(6, serializeVector(startDirection));
            ps.setString(7, serializeLocation(endLoc));

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
    }

    /**
     * 根据节点站台tag和线路类型获取手动添加的线路信息
     *
     * @param platformTag 节点站台tag
     * @return 线路信息
     */
    public @Nullable GeoManualLine getGeoManualLine(String platformTag, PRGeoWalkingPoint.LineType lineType) {
        String sql = """
                SELECT platform_tag, line_type, start_loc, start_direction, end_loc
                FROM %s WHERE platform_tag = ? AND line_type = ?
                """.formatted(geoManualLineTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            ps.setString(2, lineType.getType());

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new GeoManualLine(
                        rs.getString("platform_tag"),
                        rs.getString("line_type"),
                        deserializeLocation(rs.getString("start_loc")),
                        deserializeVector(rs.getString("start_direction")),
                        deserializeLocation(rs.getString("end_loc"))
                );
            }

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }

        return null;
    }

    /**
     * 根据节点站台tag删除手动添加的线路
     *
     * @param platformTag 节点站台tag
     * @return 删除条数
     */
    public int deleteGeoManualLine(String platformTag) {
        String sql = "DELETE FROM %s WHERE platform_tag = ?".formatted(geoManualLineTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            return ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
        return 0;
    }

    /**
     * 根据节点站台tag和线路类型删除手动添加的线路
     *
     * @param platformTag 节点站台tag
     * @param lineType    线路类型
     * @return 删除条数
     */
    public int deleteGeoManualLine(String platformTag, PRGeoWalkingPoint.LineType lineType) {
        String sql = "DELETE FROM %s WHERE platform_tag = ? AND line_type = ?"
                .formatted(geoManualLineTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, platformTag);
            ps.setString(2, lineType.getType());
            return ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
        return 0;
    }

    // =================== 序列化 反序列化方法 ===================
    public String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ":" +
                loc.getBlockX() + ":" +
                loc.getBlockY() + ":" +
                loc.getBlockZ() + ":" +
                "0.0" + ":" + "0.0";  // yaw pitch 都是 0
    }

    public Location deserializeLocation(String str) {
        String[] parts = str.split(":");
        World world = Bukkit.getWorld(parts[0]);

        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = Float.parseFloat(parts[5]);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public String serializeVector(Vector vec) {
        Vector clone = vec.clone();
        clone.setX((int) (Math.signum(vec.getX()) * Math.round(Math.abs(vec.getX()))));
        clone.setY(0);
        clone.setZ((int) (Math.signum(vec.getZ()) * Math.round(Math.abs(vec.getZ()))));
        return clone.getX() + ":" + clone.getY() + ":" + clone.getZ();
    }

    public Vector deserializeVector(String str) {
        String[] parts = str.split(":");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        return new Vector(x, y, z);
    }
}
