package com.bigbrother.bilicraftticketsystem.database;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.sql.*;

public class GeoDatabaseManager {
    private final static String geoNodeLocTableName = "geo_traversal_start";

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
                                line_id VARCHAR(64) UNIQUE,
                                start_loc VARCHAR(255),
                                start_direction VARCHAR(255)
                    );
                    """.formatted(geoNodeLocTableName);
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
    }

    /**
     * 添加线路的起点铁轨位置和方向，如果该线路起点已存在则更新
     *
     * @param lineId    线路 id
     * @param loc       起点铁轨坐标
     * @param direction 起点行走方向
     */
    public void upsertGeoNodeLoc(String lineId, Location loc, Vector direction) {
        String sql = """
                INSERT OR REPLACE INTO %s (id, line_id, start_loc, start_direction)
                VALUES (
                    (SELECT id FROM %s WHERE line_id = ?),
                    ?, ?, ?
                );
                """.formatted(geoNodeLocTableName, geoNodeLocTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, lineId);
            ps.setString(2, lineId);
            ps.setString(3, serializeLocation(loc));
            ps.setString(4, serializeVector(direction));
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
    }

    /**
     * 删除某线路已登记的遍历起点。
     *
     * @param lineId 线路 id
     * @return 删除条数
     */
    public int deleteGeoNodeLoc(String lineId) {
        String sql = "DELETE FROM %s WHERE line_id = ?".formatted(geoNodeLocTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, lineId);
            return ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
        return 0;
    }

    /**
     * 获取所有已登记的线路起点。
     *
     * @return 起点列表（按登记顺序）
     */
    public java.util.List<GeoNodeLoc> getAllGeoNodeLoc() {
        java.util.List<GeoNodeLoc> result = new java.util.ArrayList<>();
        String sql = """
                SELECT line_id, start_loc, start_direction
                FROM %s ORDER BY id
                """.formatted(geoNodeLocTableName);

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new GeoNodeLoc(
                        rs.getString("line_id"),
                        deserializeLocation(rs.getString("start_loc")),
                        deserializeVector(rs.getString("start_direction"))
                ));
            }
        } catch (SQLException e) {
            plugin.getComponentLogger().warn(Component.text(e.toString(), NamedTextColor.RED));
        }
        return result;
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
