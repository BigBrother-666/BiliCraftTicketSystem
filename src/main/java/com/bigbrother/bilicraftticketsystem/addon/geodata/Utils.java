package com.bigbrother.bilicraftticketsystem.addon.geodata;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class Utils {
    /**
     * 发送消息给玩家或控制台
     *
     * @param msg 消息
     */
    public static void sendComponentMessage(Player sender, Component msg) {
        if (sender.isOnline()) {
            sender.sendMessage(msg);
        } else {
            plugin.getServer().getConsoleSender().sendMessage(msg);
        }
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
        line = line.replace("@t", "").trim();
        if (!line.isEmpty()) {
            tags.addAll(Arrays.asList(line.split(",")));
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
     * 简化折线，去除相邻重复点与共线点（水平/垂直/45度）
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
     * 判断三点是否共线（水平、垂直、或45度）
     * 这里的x = longitude，y = latitude（实际为mc的x,z）
     */
    private static boolean isCollinear(LngLatAlt a, LngLatAlt b, LngLatAlt c) {
        double x1 = a.getLongitude(), y1 = a.getLatitude();
        double x2 = b.getLongitude(), y2 = b.getLatitude();
        double x3 = c.getLongitude(), y3 = c.getLatitude();

        // 向量差
        double dx1 = x2 - x1, dy1 = y2 - y1;
        double dx2 = x3 - x2, dy2 = y3 - y2;

        // 全水平
        if (dy1 == 0 && dy2 == 0) return true;
        // 全垂直
        if (dx1 == 0 && dx2 == 0) return true;

        return false;
    }
}
