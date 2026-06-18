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

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
     * 中文站名排序器：按拼音正序（{@link Locale#CHINA} 的 Collator 内置拼音排序规则），
     * 非中文字符回退到 Collator 的通用规则。无需引入额外拼音库，离线可用。
     */
    private static final Collator PINYIN = Collator.getInstance(Locale.CHINA);

    /**
     * UI 消费的车站条目：站名 + 经过该站的所有线路 id（聚合同名各站台节点）。
     *
     * @param name    车站名（== geojson 真实站名，可直接喂 {@link GeoRouteEngine#findByStation}）
     * @param lineIds 经过该站的线路 id
     */
    public record StationEntry(String name, Set<String> lineIds) {
    }

    /**
     * 把车站名列表按拼音正序排序（中文拼音，纯静态便于单测）。
     *
     * @param names 车站名列表（原列表不被修改）
     * @return 排序后的新列表
     */
    public static List<String> sortByPinyin(List<String> names) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(PINYIN);
        return sorted;
    }

    /**
     * 列出所有车站，按站名拼音正序排序。同名多站台节点的 lineIds 聚合。
     *
     * @return 车站条目列表
     */
    public static List<StationEntry> listStations() {
        return toEntries(sortByPinyin(new ArrayList<>(GeoRouteEngine.getGraph().allStationNames())));
    }

    /**
     * 列出属于指定铁路系统的车站，按站名拼音正序排序。
     * <p>
     * 判定方式：车站经过的任一线路（lineId）经 {@link LineConfig#getSystemId} 属于该系统，
     * 即视为该系统的车站。{@code systemId} 为 null 时返回全部车站。
     *
     * @param systemId 铁路系统 id
     * @return 车站条目列表
     */
    public static List<StationEntry> listStationsOfSystem(String systemId) {
        if (systemId == null) {
            return listStations();
        }
        List<String> names = new ArrayList<>();
        for (String name : GeoRouteEngine.getGraph().allStationNames()) {
            if (belongsToSystem(name, systemId)) {
                names.add(name);
            }
        }
        return toEntries(sortByPinyin(names));
    }

    /**
     * 按关键字搜索车站（站名包含关键字，忽略大小写），按站名拼音正序排序。
     * <p>
     * 关键字为空 / 空白时返回全部车站（与 {@link #listStations()} 等价）。
     *
     * @param keyword 搜索关键字
     * @return 匹配的车站条目列表
     */
    public static List<StationEntry> searchStations(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listStations();
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (String name : GeoRouteEngine.getGraph().allStationNames()) {
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) {
                names.add(name);
            }
        }
        return toEntries(sortByPinyin(names));
    }

    /**
     * 把已排序的站名列表转成 {@link StationEntry}（聚合同名各站台节点的 lineIds）。
     */
    private static List<StationEntry> toEntries(List<String> sortedNames) {
        List<StationEntry> result = new ArrayList<>();
        for (String name : sortedNames) {
            result.add(new StationEntry(name, lineIdsOf(name)));
        }
        return result;
    }

    /**
     * 聚合某车站名下所有站台节点经过的线路 id。
     */
    private static Set<String> lineIdsOf(String stationName) {
        Set<String> lineIds = new LinkedHashSet<>();
        for (GeoNode node : GeoRouteEngine.getGraph().stationNodes(stationName)) {
            lineIds.addAll(node.getLineIds());
        }
        return lineIds;
    }

    /**
     * 判断某车站是否属于指定铁路系统（任一经过线路归属该系统即算）。
     */
    private static boolean belongsToSystem(String stationName, String systemId) {
        for (String lineId : lineIdsOf(stationName)) {
            if (systemId.equals(LineConfig.getSystemId(lineId))) {
                return true;
            }
        }
        return false;
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
            lore.add(Component.text("途径此车站的铁路：", NamedTextColor.GRAY));
            lore.add(Component.text("● " + line.getLineName(), colorOf(lineId)));
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
