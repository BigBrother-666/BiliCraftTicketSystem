package com.bigbrother.bilicraftticketsystem.utils;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeoUtils {
    /**
     * 获取component的字体颜色
     */
    public static TextColor getPrimaryColor(Component component) {
        if (component instanceof TextComponent text && text.color() != null) {
            return text.color();
        }
        for (Component child : component.children()) {
            TextColor c = getPrimaryColor(child);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * 解析道岔switcher某行包含的tag
     *
     * @param line switcher控制牌的行
     * @return 这一行的所有tag，包括continue
     */
    public static List<String> parseSwitcherTags(String line) {
        List<String> tags = new ArrayList<>();
        // 必须是包含方向的switcher（道岔节点）
        if (!line.contains(":")) {
            return tags;
        }
        line = line.substring(line.indexOf(":") + 1);
        line = line.replace("t@", "").trim();
        if (!line.isEmpty()) {
            tags.addAll(Arrays.asList(line.split(";")));
        }
        return tags;
    }

    public static DirectionStatement parseDirectionStatement(String line) {
        String left_str = Direction.IMPLICIT_LEFT.aliases()[0];
        if (line == null || line.isEmpty()) {
            return new DirectionStatement("default", left_str);
        } else {
            return new DirectionStatement(line, left_str);
        }
    }

    /**
     * 解析 bcswitcher 控制牌的一行出向声明 {@code <方向>@<线路id>;[线路id]...}，返回道岔分支。
     * <p>
     * {@code @} 后可用分号分隔多个线路 id，表示该出向轨道被多条线路共用
     * （如 {@code r@pr-cw;pr-s1}）。
     *
     * @param line bcswitcher 控制牌的一行（第三或第四行）
     * @return 解析出的道岔分支；该行为空、缺少 '@' 或无有效线路 id 时返回 null
     */
    public static BcSwitcherBranch parseBcSwitcherBranch(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        int idx = trimmed.indexOf('@');
        if (idx <= 0 || idx == trimmed.length() - 1) {
            return null;
        }
        String directionStr = trimmed.substring(0, idx).trim();
        String lineIdsPart = trimmed.substring(idx + 1).trim();
        if (directionStr.isEmpty() || lineIdsPart.isEmpty()) {
            return null;
        }
        List<String> lineIds = new ArrayList<>();
        for (String part : lineIdsPart.split(";")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                lineIds.add(id);
            }
        }
        if (lineIds.isEmpty()) {
            return null;
        }
        return new BcSwitcherBranch(directionStr, lineIds);
    }

    /**
     * 简化折线，去除相邻重复点与<b>三维</b>共线点。
     * <p>
     * 只有当连续三点在三维空间（含高度）上严格共线且方向一致时才丢弃中间点，
     * 因此任何高度变化（爬坡 / 下坡）处的拐点都会被保留，不会把不同高度压平成同一值。
     */
    public static List<LngLatAlt> simplifyLineString(List<LngLatAlt> coords) {
        if (coords.size() <= 2) return coords;

        List<LngLatAlt> result = new ArrayList<>();
        result.add(coords.get(0)); // 起点

        for (int i = 1; i < coords.size() - 1; i++) {
            LngLatAlt prev = coords.get(i - 1);
            LngLatAlt curr = coords.get(i);
            LngLatAlt next = coords.get(i + 1);

            // 跳过重复点
            if (isSamePoint(prev, curr)) {
                continue;
            }

            // 如果三点共线（水平、垂直或45度），跳过中点
            if (isCollinear(prev, curr, next)) {
                continue;
            }

            // 否则保留拐点
            result.add(curr);
        }

        // 末点始终保留
        result.add(coords.get(coords.size() - 1));

        return result;
    }

    /** 判断两点是否完全相同 */
    private static boolean isSamePoint(LngLatAlt a, LngLatAlt b) {
        return Double.compare(a.getLongitude(), b.getLongitude()) == 0 &&
                Double.compare(a.getLatitude(), b.getLatitude()) == 0 &&
                Double.compare(a.getAltitude(), b.getAltitude()) == 0;
    }

    /**
     * 判断三点是否<b>三维</b>共线且方向一致。
     * <p>
     * x = longitude（mc 的 x）、y = latitude（mc 的 z）、z = altitude（mc 的 y）。
     * 用两段向量的叉积是否为零判断共线，再用点积 &gt; 0 排除原路折返（折返点必须保留）。
     */
    private static boolean isCollinear(LngLatAlt a, LngLatAlt b, LngLatAlt c) {
        // 第一段向量 a->b
        double ux = b.getLongitude() - a.getLongitude();
        double uy = b.getLatitude() - a.getLatitude();
        double uz = b.getAltitude() - a.getAltitude();
        // 第二段向量 b->c
        double vx = c.getLongitude() - b.getLongitude();
        double vy = c.getLatitude() - b.getLatitude();
        double vz = c.getAltitude() - b.getAltitude();

        // 叉积 u × v，全为 0 才三维共线
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        if (cx != 0 || cy != 0 || cz != 0) {
            return false;
        }
        // 共线但需方向一致（点积 > 0），否则是折返拐点，必须保留
        return ux * vx + uy * vy + uz * vz > 0;
    }

    public static boolean isRail(Material type) {
        return type == Material.RAIL
                || type == Material.POWERED_RAIL
                || type == Material.DETECTOR_RAIL
                || type == Material.ACTIVATOR_RAIL;
    }

    public static String formatLoc(Location loc) {
        return String.format("(%d, %d, %d - %s)",
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                loc.getWorld().getName()
        );
    }

    public static String formatVector(Vector vector) {
        return String.format("(%d, %d, %d)",
                vector.getBlockX(),
                vector.getBlockY(),
                vector.getBlockZ()
        );
    }
}
