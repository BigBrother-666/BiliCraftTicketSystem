package com.bigbrother.bilicraftticketsystem.menu.station;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 购票 UI 的车站数据来源：把新 geojson 路由图（{@link GeoRouteEngine#getGraph()}）的车站抽象成
 * UI 可直接消费的 {@link StationEntry} + 物品图标，隔离菜单层对图内部类（GeoNode 等）的依赖。
 * <p>
 * 取代旧的 {@code menu_location.yml} {@code content:} 手填车站列表：车站列表实时来自图，
 * 新增站点自动出现；图标统一（{@code menuitems.yml} 的 {@code station} 项，缺失回退 RAIL），
 * lore 由车站经过的线路（lineIds → {@link LineConfig}）自动生成。
 */
public class StationProvider {
    /**
     * {@code menuitems.yml} 中车站图标的物品 key；缺失时 {@link CommonUtils#loadItemFromFile} 回退 RAIL。
     */
    private static final String STATION_ITEM_KEY = "station";

    private StationProvider() {
    }

    /**
     * UI 消费的车站条目：站名 + 经过该站的所有线路 id（聚合同名各站台节点）。
     *
     * @param name    车站名（== geojson 真实站名，可直接喂 {@link GeoRouteEngine#findByStation}）
     * @param lineIds 经过该站的线路 id
     */
    public record StationEntry(String name, Set<String> lineIds) {
    }

    /**
     * 列出所有车站，按站名排序。同名多站台节点的 lineIds 聚合。
     *
     * @return 车站条目列表
     */
    public static List<StationEntry> listStations() {
        List<StationEntry> result = new ArrayList<>();
        List<String> names = new ArrayList<>(GeoRouteEngine.getGraph().allStationNames());
        names.sort(String::compareTo);
        for (String name : names) {
            Set<String> lineIds = new LinkedHashSet<>();
            for (GeoNode node : GeoRouteEngine.getGraph().stationNodes(name)) {
                lineIds.addAll(node.getLineIds());
            }
            result.add(new StationEntry(name, lineIds));
        }
        return result;
    }

    /**
     * 为车站构建菜单图标：统一图标 + 站名（按首个营运线路标志色上色）+ 线路 lore。
     *
     * @param entry 车站条目
     * @return 图标物品
     */
    public static ItemStack buildIcon(StationEntry entry) {
        ItemStack item = CommonUtils.loadItemFromFile(STATION_ITEM_KEY);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(entry.name(), stationColor(entry))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String lineId : entry.lineIds()) {
            LineInfo line = LineConfig.get(lineId);
            if (line == null) {
                continue;
            }
            lore.add(Component.text("● " + line.getLineName(),
                            colorOf(lineId))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 车站显示名 Component：纯站名 + 按首个营运线路标志色上色、关闭斜体。
     * <p>
     * 用于「最近车站」等处直接设站名，保证与菜单图标显示名一致；
     * {@link CommonUtils#component2Str} 仍能还原为纯站名喂寻路。
     *
     * @param stationName 站名
     * @return 显示名 Component
     */
    public static Component stationNameComponent(String stationName) {
        Set<String> lineIds = new LinkedHashSet<>();
        for (GeoNode node : GeoRouteEngine.getGraph().stationNodes(stationName)) {
            lineIds.addAll(node.getLineIds());
        }
        return Component.text(stationName, stationColor(new StationEntry(stationName, lineIds)))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 站名显示色：取车站第一个营运线路的标志色；无营运线路用白色。
     */
    private static TextColor stationColor(StationEntry entry) {
        for (String lineId : entry.lineIds()) {
            LineInfo line = LineConfig.get(lineId);
            if (line != null) {
                return colorOf(lineId);
            }
        }
        return NamedTextColor.WHITE;
    }

    private static TextColor colorOf(String lineId) {
        TextColor color = TextColor.fromHexString(LineConfig.getColor(lineId));
        return color == null ? NamedTextColor.WHITE : color;
    }
}
