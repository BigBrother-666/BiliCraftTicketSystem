package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteGraph;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteTrack;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 普通车 bossbar：列车沿当前线路每站停，bossbar 显示「…上一站 → 当前站 → 下一站…」滚动站名带。
 * <p>
 * 站序取自列车当前所属线路（lineId）的 {@link LineInfo#getBossbarStations()}，环线（首尾同名）
 * 保持满进度。滚动窗口的颜色 / 显示个数来自 {@link MainConfig} 的全局 bossbar 样式配置
 * （未过站颜色未配置时回退到该线路的标志色）。到站标题模板取自该线路的
 * {@link LineInfo#getBossbarArrivalNotice()}（占位符 {@code {curr_station}}），留空则显示滚动站名带。
 * 由 {@link SignActionPlatform} 在进出站时驱动。
 * <p>
 * 进度条（非环线）按<b>实时位置</b>刷新：首次使用时沿本线把相邻车站的站台节点在路由图中连成整线轨迹
 * （{@link GeoRouteTrack#ofLineStations}），定时任务（{@link #tick}）把车头投影到「上一站 → 下一站」区间的
 * 轨道折线上，进度 = 沿线距离 / 全线距离。轨迹构建失败（车站缺站台节点 / 图中无路 / 缺几何）时退化为按车站计数。
 * 环线恒为满进度。
 */
public class CommonRouteBossbar extends RouteBossbarBase {
    /**
     * 该 bossbar 当前绑定的线路 id；列车换乘到别的线路时据此判断需重建。
     */
    @Getter
    private final String lineId;
    private final List<String> stations;
    private final boolean ring;
    private int currStationIdx;

    private final TextColor passedColor;
    private final TextColor notPassedColor;
    private final int passedNum;
    private final int notPassedNum;
    private final String arrivalNotice;
    private final String lineName;

    /**
     * 整线轨迹缓存（lineId → 轨迹，失败存 Optional.empty）：bossbar 按车厢绑定，同一列车多节车厢 /
     * 同线多列车共享，避免重复寻路。路由图整体替换（reload / 重新遍历）后清空。
     */
    private static final Map<String, Optional<GeoRouteTrack.StationTrack>> TRACK_CACHE = new HashMap<>();
    private static GeoRouteGraph cachedGraph;

    /**
     * 整线轨迹（懒构建，{@link #trackBuilt} 标记已尝试过，失败为 null 走计数退化）。
     */
    private GeoRouteTrack.StationTrack track;
    private boolean trackBuilt;
    private final GeoRouteTrack.Cursor cursor = new GeoRouteTrack.Cursor();
    /**
     * 已显示过的最大沿线距离：投影噪声 / 列车短暂回溯时进度条不倒退。
     */
    private double maxAlong;

    /**
     * @param line 列车当前所属线路信息
     */
    public CommonRouteBossbar(LineInfo line) {
        super(line.getBossbarStations().isEmpty() ? null
                : BossBar.bossBar(Component.text(""), 0f, parseBarColor(line.getBossbarColor()), BossBar.Overlay.PROGRESS));
        this.lineId = line.getId();
        this.stations = new ArrayList<>(line.getBossbarStations());
        this.ring = line.isRing();
        this.currStationIdx = 0;
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

    @Override
    public boolean needsRebuild(LineInfo currentLine) {
        // 换乘到别的线路：当前 bossbar 绑定的 lineId 与列车新线路不同则需重建
        return currentLine != null && !currentLine.getId().equals(lineId);
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
    public void onArrive(MinecartGroup group, String currStation) {
        if (bossBar == null || stations.isEmpty()) {
            return;
        }
        int idx = stations.indexOf(currStation);
        if (idx < 0) {
            return;
        }
        this.currStationIdx = idx;
        if (ring) {
            bossBar.progress(1.0f);
        } else if (ensureTrack() != null) {
            // 到站：进度对齐到本站沿线距离
            maxAlong = Math.max(maxAlong, track.stationDistance(idx));
            applyAlong();
        } else {
            bossBar.progress(Math.min((float) (currStationIdx + 1) / stations.size(), 1.0f));
        }

        if (arrivalNotice == null || arrivalNotice.isEmpty()) {
            bossBar.name(buildScrollTitle(true));
        } else {
            LineInfo lineInfo = LineConfig.get(lineId);
            // 走统一占位符解析（同时支持 MiniMessage 与 & 代码），便于以后扩展更多占位符
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("curr_station", stations.get(currStationIdx));
            placeholders.put("next_station", currStationIdx + 1 < stations.size() ? stations.get(currStationIdx + 1) : CommonUtils.NOT_AVAILABLE_MM);
            placeholders.put("line_name", lineName);
            placeholders.put("line_color", lineInfo == null ? "<#FFFFFF>" : "<%s>".formatted(lineInfo.getLineColor()));
            RailwaySystemInfo railwaySystemInfo = lineInfo == null ? null : RailwaySystemConfig.get(lineInfo.getRailwaySystemId());
            placeholders.put("railway_system", railwaySystemInfo != null ? railwaySystemInfo.getName() : CommonUtils.NOT_AVAILABLE_MM);
            List<Component> parsed = PlaceholderParser.parse(List.of(arrivalNotice), placeholders);
            bossBar.name(parsed.isEmpty() ? buildScrollTitle(true) : parsed.getFirst());
        }
    }

    @Override
    public void onLeave(MinecartGroup group) {
        if (bossBar == null || stations.isEmpty()) {
            return;
        }
        currStationIdx += 1;
        bossBar.name(buildScrollTitle(false));
        if (ring) {
            bossBar.progress(1.0f);
        } else if (ensureTrack() == null) {
            bossBar.progress(Math.min((float) currStationIdx / stations.size(), 1.0f));
        }
    }

    /**
     * 转线后定位：把 bossbar 定位到「正驶向 {@code entryStation}」的状态——该站显示为下一站，
     * 之前的站标记为已过。用于普通车转入本线（可能跳过本线靠前的车站，如从别线终点直接接入中间站），
     * 列车尚未物理到达该站，故不发到站提示、只刷新显示。
     * <p>
     * {@code entryStation} 为空或不在本线车站列表时，回退定位到首站（下标 0）。
     *
     * @param entryStation 转线后的进入站名（下一站）
     */
    public void approach(String entryStation) {
        if (bossBar == null || stations.isEmpty()) {
            return;
        }
        int idx = entryStation == null ? -1 : stations.indexOf(entryStation);
        this.currStationIdx = Math.max(idx, 0);
        bossBar.name(buildScrollTitle(false));
        if (ring) {
            bossBar.progress(1.0f);
        } else if (ensureTrack() != null) {
            // 转入本线：进入站之前的车站视为已过，进度从上一站起算
            maxAlong = currStationIdx > 0 ? track.stationDistance(currStationIdx - 1) : 0;
            applyAlong();
        } else {
            bossBar.progress(Math.min((float) (currStationIdx) / stations.size(), 1.0f));
        }
    }

    @Override
    public void tick(MinecartGroup group, Location head) {
        if (bossBar == null || ring || stations.size() < 2 || head == null || head.getWorld() == null) {
            return;
        }
        if (ensureTrack() == null || !head.getWorld().getName().equals(track.track().getWorld())) {
            return;
        }
        // 列车位于「上一站 → 当前/下一站」区间：出站后 currStationIdx 已指向下一站，到站时指向本站
        int last = stations.size() - 1;
        int fromNode = track.stationNodeIndex()[Math.max(0, Math.min(currStationIdx - 1, last))];
        int toNode = track.stationNodeIndex()[Math.max(0, Math.min(currStationIdx, last))];
        // 区间内的跳为 [fromNode, toNode-1]，前后各放宽一跳容忍控制牌触发时机偏差
        double along = track.track().locate(fromNode - 1, toNode, head.getX(), head.getY(), head.getZ(), cursor);
        if (Double.isNaN(along)) {
            return;
        }
        maxAlong = Math.max(maxAlong, along);
        applyAlong();
    }

    /**
     * 懒构建整线轨迹（仅非环线），只尝试一次。
     *
     * @return 轨迹；不可用返回 null
     */
    private GeoRouteTrack.StationTrack ensureTrack() {
        if (!trackBuilt) {
            trackBuilt = true;
            if (!ring) {
                track = cachedTrack(lineId, stations);
            }
        }
        return track;
    }

    private static GeoRouteTrack.StationTrack cachedTrack(String lineId, List<String> stations) {
        GeoRouteGraph graph = GeoRouteEngine.getGraph();
        if (graph != cachedGraph) {
            TRACK_CACHE.clear();
            cachedGraph = graph;
        }
        return TRACK_CACHE.computeIfAbsent(lineId, id -> {
            GeoRouteTrack.StationTrack built = GeoRouteTrack.ofLineStations(graph, stations, id);
            return Optional.ofNullable(built != null && built.track().totalDistance() > 0 ? built : null);
        }).orElse(null);
    }

    private void applyAlong() {
        bossBar.progress((float) Math.min(Math.max(maxAlong / track.track().totalDistance(), 0), 1));
    }

    /**
     * 构造滚动站名带标题：当前站前 {@code passedNum} 个已过站、后 {@code notPassedNum} 个未过站，
     * 已过 / 未过站不同颜色，被截断的一侧用 {@code ...} 省略。环线按去重后的唯一站序环绕，
     * 两端恒显省略号。
     */
    private Component buildScrollTitle(boolean arrived) {
        return scrollTitle(stations, ring, currStationIdx, passedColor, notPassedColor, passedNum, notPassedNum, arrived);
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
     * @param arrived        是否已到达当前站（到站时通向当前站的箭头用已过色，站名仍为未过色）
     * @return 标题 Component
     */
    public static Component scrollTitle(List<String> stations, boolean ring, int nextStationIdx,
                                        TextColor passedColor, TextColor notPassedColor,
                                        int passedNum, int notPassedNum, boolean arrived) {
        int size = stations.size();
        // 每个 token: [站名下标, 是否已过(1/0), 是否当前站(1/0)]
        List<int[]> tokens = new ArrayList<>();
        boolean lead;
        boolean trail;

        if (ring) {
            int unique = size - 1;
            if (unique <= 0) {
                return Component.text(stations.getFirst(), notPassedColor);
            }
            int cur = ((nextStationIdx % unique) + unique) % unique;
            for (int k = cur - passedNum; k <= cur + notPassedNum - 1; k++) {
                int idx = ((k % unique) + unique) % unique;
                tokens.add(new int[]{idx, k < cur ? 1 : 0, k == cur ? 1 : 0});
            }
            lead = true;
            trail = true;
        } else {
            //noinspection UnnecessaryLocalVariable
            int cur = nextStationIdx;
            // 固定显示 displayNum 个车站：开始时窗口锁定在 [0, displayNum) 不滑动，
            // 直到当前站越过前 passedNum 个已过站后才开始滑动（始终保持 passedNum 个已过站在前）；
            // 滑到尾部 [size-displayNum, size) 后不再滑动、也不减少车站数，改为窗口内已过站越来越多。
            int displayNum = Math.min(passedNum + notPassedNum, size);
            int start = Math.max(0, Math.min(cur - passedNum, size - displayNum));
            int end = start + displayNum;
            for (int i = start; i < end; i++) {
                tokens.add(new int[]{i, i < cur ? 1 : 0, i == cur ? 1 : 0});
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
                // 已到站时，通向当前站的箭头视为已走过，用已过色（站名本身仍为未过色）
                TextColor arrowColor = (arrived && token[2] == 1) ? passedColor : color;
                result = result.append(Component.text(" → ", arrowColor));
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
