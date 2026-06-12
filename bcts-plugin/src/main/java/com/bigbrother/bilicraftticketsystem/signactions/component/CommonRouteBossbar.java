package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 普通车 bossbar：列车沿当前线路每站停，bossbar 显示「…上一站 → 当前站 → 下一站…」滚动站名带。
 * <p>
 * 站序取自列车当前所属线路（lineId）的 {@link LineInfo#getBossbarStations()}，环线（首尾同名）
 * 保持满进度。滚动窗口的颜色 / 显示个数来自 {@link MainConfig} 的全局 bossbar 样式配置
 * （未过站颜色未配置时回退到该线路的标志色）。到站标题模板取自该线路的
 * {@link LineInfo#getBossbarArrivalNotice()}（占位符 {@code {curr_station}}），留空则显示滚动站名带。
 * 由 {@link SignActionPlatform} 在进出站时驱动。
 */
public class CommonRouteBossbar extends RouteBossbarBase {
    /**
     * 该 bossbar 当前绑定的线路 id；列车换乘到别的线路时据此判断需重建。
     */
    @Getter
    private final String lineId;
    private final List<String> stations;
    private final boolean ring;
    private int nextStationIdx;

    private final TextColor passedColor;
    private final TextColor notPassedColor;
    private final int passedNum;
    private final int notPassedNum;
    private final String arrivalNotice;
    private final String lineName;

    /**
     * @param line 列车当前所属线路信息
     */
    public CommonRouteBossbar(LineInfo line) {
        super(line.getBossbarStations().isEmpty() ? null
                : BossBar.bossBar(Component.text(""), 0f, parseBarColor(line.getBossbarColor()), BossBar.Overlay.PROGRESS));
        this.lineId = line.getId();
        this.stations = new ArrayList<>(line.getBossbarStations());
        this.ring = line.isRing();
        this.nextStationIdx = 0;
        this.passedColor = parseTextColor(MainConfig.bossbarPassedColor, NamedTextColor.GRAY);
        // 未过站颜色未配置时回退到该线路的标志色
        this.notPassedColor = parseTextColor(MainConfig.bossbarNotPassedColor,
                parseTextColor(line.getLineColor(), NamedTextColor.WHITE));
        this.passedNum = MainConfig.bossbarPassedNum;
        this.notPassedNum = MainConfig.bossbarNotPassedNum;
        this.arrivalNotice = line.getBossbarArrivalNotice();
        this.lineName = line.getLineName();
    }

    private static BossBar.Color parseBarColor(String name) {
        try {
            return BossBar.Color.valueOf(name == null ? "WHITE" : name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }

    /**
     * 解析颜色字符串：支持 {@code #RRGGBB} 十六进制与 legacy &-code（如 {@code &7}），失败用回退色。
     */
    private static TextColor parseTextColor(String s, TextColor fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        s = s.trim();
        if (s.startsWith("#")) {
            TextColor color = TextColor.fromHexString(s);
            return color == null ? fallback : color;
        }
        TextColor color = LegacyComponentSerializer.legacyAmpersand().deserialize(s + "x").color();
        return color == null ? fallback : color;
    }

    @Override
    public void onArrive(String currStation) {
        if (bossBar == null || stations.isEmpty()) {
            return;
        }
        int idx = stations.indexOf(currStation);
        if (idx < 0) {
            return;
        }
        this.nextStationIdx = idx;
        if (ring) {
            bossBar.progress(1.0f);
        } else {
            bossBar.progress(Math.min((float) (nextStationIdx + 1) / stations.size(), 1.0f));
        }

        if (arrivalNotice == null || arrivalNotice.isEmpty()) {
            bossBar.name(buildScrollTitle());
        } else {
            // 走统一占位符解析（同时支持 MiniMessage 与 & 代码），便于以后扩展更多占位符
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("curr_station", stations.get(nextStationIdx));
            placeholders.put("line_name", lineName);
            List<Component> parsed = PlaceholderParser.parse(List.of(arrivalNotice), placeholders);
            bossBar.name(parsed.isEmpty() ? buildScrollTitle() : parsed.get(0));
        }
    }

    @Override
    public void onLeave() {
        if (bossBar == null || stations.isEmpty()) {
            return;
        }
        nextStationIdx += 1;
        bossBar.name(buildScrollTitle());
        if (ring) {
            bossBar.progress(1.0f);
        } else {
            bossBar.progress(Math.min((float) nextStationIdx / stations.size(), 1.0f));
        }
    }

    /**
     * 构造滚动站名带标题：当前站前 {@code passedNum} 个已过站、后 {@code notPassedNum} 个未过站，
     * 已过 / 未过站不同颜色，被截断的一侧用 {@code ...} 省略。环线按去重后的唯一站序环绕，
     * 两端恒显省略号。
     */
    private Component buildScrollTitle() {
        return scrollTitle(stations, ring, nextStationIdx, passedColor, notPassedColor, passedNum, notPassedNum);
    }

    /**
     * 滚动站名带标题的纯逻辑（无副作用，便于单测）。
     *
     * @param stations       站名序列（环线首尾同名）
     * @param ring           是否环线
     * @param nextStationIdx 当前/下一站下标
     * @param passedColor    已过站颜色
     * @param notPassedColor 未过站颜色
     * @param passedNum      当前站前显示的已过站个数
     * @param notPassedNum   当前站起显示的未过站个数
     * @return 标题 Component
     */
    public static Component scrollTitle(List<String> stations, boolean ring, int nextStationIdx,
                                        TextColor passedColor, TextColor notPassedColor,
                                        int passedNum, int notPassedNum) {
        int size = stations.size();
        // 每个 token: [站名下标, 是否已过(1/0)]
        List<int[]> tokens = new ArrayList<>();
        boolean lead;
        boolean trail;

        if (ring) {
            int unique = size - 1;
            if (unique <= 0) {
                return Component.text(stations.get(0), notPassedColor);
            }
            int cur = ((nextStationIdx % unique) + unique) % unique;
            for (int k = cur - passedNum; k <= cur + notPassedNum - 1; k++) {
                int idx = ((k % unique) + unique) % unique;
                tokens.add(new int[]{idx, k < cur ? 1 : 0});
            }
            lead = true;
            trail = true;
        } else {
            int cur = nextStationIdx;
            int start = Math.max(cur - passedNum, 0);
            int end = Math.min(cur + notPassedNum, size);
            for (int i = start; i < end; i++) {
                tokens.add(new int[]{i, i < cur ? 1 : 0});
            }
            lead = start > 0;
            trail = end < size;
        }

        Component result = Component.empty();
        boolean first = true;
        if (lead) {
            result = result.append(Component.text("...", passedColor));
            first = false;
        }
        for (int[] token : tokens) {
            TextColor color = token[1] == 1 ? passedColor : notPassedColor;
            if (!first) {
                result = result.append(Component.text(" → ", color));
            }
            result = result.append(Component.text(stations.get(token[0]), color));
            first = false;
        }
        if (trail) {
            result = result.append(Component.text(" → ...", notPassedColor));
        }
        return result;
    }
}
