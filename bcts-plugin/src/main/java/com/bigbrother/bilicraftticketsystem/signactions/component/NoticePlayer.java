package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 进出站提示（notice）的解析与播放。
 * <p>
 * 提示行沿用 traincarts 风格的前缀指令：
 * <ul>
 *   <li>{@code announce:<文字>} —— 向乘客发送聊天消息（同时支持 MiniMessage 与 &amp; 颜色代码）。</li>
 *   <li>{@code sound:<音效>,<音调>,<音量>} —— 向乘客播放音效。</li>
 * </ul>
 * 文字中支持占位符 {@code {curr_station}}（当前车站名）、{@code {line_name}}（线路名）、
 * {@code {next_station}}（下一站名，按线路 bossbar 车站列表推算）。
 */
public final class NoticePlayer {
    private NoticePlayer() {
    }

    /**
     * 对一节车厢的所有玩家乘客播放提示，并可显式指定下一站名（覆盖按车站列表推算的结果）。
     * <p>
     * 转线场景下，列车在前一线终点站出站、即将转入下一线，下一站不在当前线车站列表中（如从 S1-D
     * 转入 S2-B），需由调用方显式传入 {@code nextStationOverride}。
     *
     * @param member              车厢
     * @param notices             提示行列表（announce/sound 指令）
     * @param line                列车所属线路（提供 line_name / line_color 占位符）
     * @param currStation         当前车站名
     * @param nextStationOverride 下一站名覆盖；为 null 时按线路车站列表推算
     */
    public static void play(MinecartMember<?> member, Collection<String> notices, LineInfo line,
                            String currStation, String nextStationOverride) {
        if (member.getEntity() == null) {
            return;
        }
        List<Player> passengers = member.getEntity().getPlayerPassengers();
        if (passengers.isEmpty()) {
            return;
        }
        for (String notice : notices) {
            dispatch(passengers, notice, line, currStation, nextStationOverride);
        }
    }

    /**
     * 解析并分发一条提示指令到所有乘客。
     *
     * @param passengers          乘客列表
     * @param notice              提示行
     * @param line                线路信息
     * @param currStation         当前车站名
     * @param nextStationOverride 下一站名覆盖；为 null 时按线路车站列表推算
     */
    private static void dispatch(List<Player> passengers, String notice, LineInfo line, String currStation,
                                 String nextStationOverride) {
        if (notice == null || notice.isEmpty()) {
            return;
        }
        int idx = notice.indexOf(':');
        if (idx < 0) {
            return;
        }
        String type = notice.substring(0, idx).trim().toLowerCase(Locale.ENGLISH);
        String body = notice.substring(idx + 1);

        switch (type) {
            case "announce" -> {
                // 走统一占位符解析（同时支持 MiniMessage 与 & 代码），便于以后扩展更多占位符
                List<Component> parsed = PlaceholderParser.parse(
                        List.of(body), placeholders(line, currStation, nextStationOverride));
                if (parsed.isEmpty()) {
                    return;
                }
                Component msg = parsed.getFirst();
                for (Player p : passengers) {
                    p.sendMessage(msg);
                }
            }
            case "sound" -> playSound(passengers, body);
            default -> {
                // 未知指令类型，忽略（便于以后扩展 actionbar/title 等）
            }
        }
    }

    /**
     * 解析并播放音效行 {@code <音效>,<音调>,<音量>}。
     *
     * @param passengers 乘客列表
     * @param body       音效参数串
     */
    private static void playSound(List<Player> passengers, String body) {
        String[] parts = body.split(",");
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            return;
        }
        String soundKey = parts[0].trim();
        float pitch = parts.length > 1 ? parseFloat(parts[1], 1.0f) : 1.0f;
        float volume = parts.length > 2 ? parseFloat(parts[2], 1.0f) : 1.0f;

        for (Player p : passengers) {
            // 优先按命名空间 key 播放，兼容自定义音效；失败则尝试枚举名
            try {
                p.playSound(p.getLocation(), soundKey, volume, pitch);
            } catch (Exception e) {
                Sound sound = matchSound(soundKey);
                if (sound != null) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                }
            }
        }
    }

    /**
     * 按命名空间 key 匹配音效（替代已废弃的 {@code Sound.valueOf}）。
     * <p>
     * 配置里的音效名为点分形式（如 {@code block.note_block.bell}，可带命名空间 {@code minecraft:}），
     * 转成 {@link NamespacedKey} 后走 {@link Registry#SOUNDS} 查询。
     *
     * @param key 音效名（点分形式，含下划线）
     * @return 匹配的音效，无匹配 / key 非法返回 null
     */
    private static Sound matchSound(String key) {
        NamespacedKey nsKey = NamespacedKey.fromString(key.toLowerCase(Locale.ENGLISH));
        if (nsKey == null) {
            return null;
        }
        return Registry.SOUNDS.get(nsKey);
    }

    /**
     * 构建提示文字的占位符 map（值缺失时用空串，避免 {@link PlaceholderParser} 回退为 N/A）。
     * <p>
     * 以后扩展新占位符只需往这里加键。
     *
     * @param line                线路信息
     * @param currStation         当前车站名
     * @param nextStationOverride 下一站名覆盖；为 null 时按线路车站列表推算
     * @return 占位符 map
     */
    private static Map<String, Object> placeholders(LineInfo line, String currStation, String nextStationOverride) {
        String nextStation = nextStationOverride != null ? nextStationOverride : nextStation(line, currStation);
        Map<String, Object> map = new HashMap<>();
        map.put("curr_station", currStation == null ? CommonUtils.NOT_AVAILABLE_MM : currStation);
        map.put("line_name", line == null ? CommonUtils.NOT_AVAILABLE_MM : line.getLineName());
        map.put("line_color", line == null ? "<#FFFFFF>" : "<%s>".formatted(line.getLineColor()));
        map.put("next_station", nextStation == null ? CommonUtils.NOT_AVAILABLE_MM : nextStation);
        if (line != null) {
            map.put("railway_system", line.getRailwaySystemId() != null ? RailwaySystemConfig.get(line.getRailwaySystemId()) : CommonUtils.NOT_AVAILABLE_MM);
        } else {
            map.put("railway_system", CommonUtils.NOT_AVAILABLE_MM);
        }
        return map;
    }

    /**
     * 根据线路的 bossbar 车站列表推算当前站的下一站。
     * <p>
     * 环线在末站回到首站。找不到当前站或已是终点时返回空串。
     *
     * @param line        线路信息
     * @param currStation 当前车站名
     * @return 下一站名，无则空串
     */
    private static String nextStation(LineInfo line, String currStation) {
        if (line == null || currStation == null) {
            return "";
        }
        List<String> stations = line.getBossbarStations();
        int idx = stations.indexOf(currStation);
        if (idx < 0) {
            return "";
        }
        if (idx < stations.size() - 1) {
            return stations.get(idx + 1);
        }
        // 末站：环线回到首站（首站与末站同名，取第二个）
        if (line.isRing() && stations.size() > 1) {
            return stations.get(1);
        }
        return "";
    }

    @SuppressWarnings("SameParameterValue")
    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
