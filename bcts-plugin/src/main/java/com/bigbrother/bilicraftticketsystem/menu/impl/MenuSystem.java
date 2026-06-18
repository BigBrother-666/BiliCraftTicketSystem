package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.common.NextpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.common.PrevpageItem;
import com.bigbrother.bilicraftticketsystem.menu.items.system.SystemItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 铁路系统选择界面（{@code menu_system.yml}）。
 * <p>
 * 点击购票主界面 / 交通卡界面的起点/终点按钮后，先用本界面让玩家选择一个铁路系统，
 * 选中后回调 {@code onSelect}（带系统 id）打开按该系统过滤的车站列表。
 * <p>
 * 当系统数 ≤ 1 时调用方应跳过本界面（见 {@link #openOrSkip}）。每次打开都新建（非缓存），
 * 与 {@link MenuLocationCard} 一致。
 */
public class MenuSystem extends Menu {

    private MenuSystem(Player player, Consumer<String> onSelect) {
        FileConfiguration systemConfig = MenuConfig.getSystemMenuConfig();

        PagedGui<@NotNull Item> gui = PagedGui.items()
                .setStructure(buildStructure(systemConfig, itemName -> switch (itemName) {
                    case "content" -> Markers.CONTENT_LIST_SLOT_HORIZONTAL;
                    case "nextpage" -> new NextpageItem();
                    case "prevpage" -> new PrevpageItem();
                    default -> null;
                }))
                .build();

        List<Item> items = new ArrayList<>();
        for (RailwaySystemInfo system : RailwaySystemConfig.getSystems().values()) {
            items.add(new SystemItem(system, onSelect));
        }
        gui.setContent(items);

        this.window = buildSingleWindow(player, systemConfig, gui);
    }

    /**
     * 打开系统选择界面；当系统数 ≤ 1 时跳过本界面，直接回调：
     * <ul>
     *   <li>恰好 1 个系统：用该系统 id 回调（打开按该系统过滤的车站列表）。</li>
     *   <li>0 个系统：用 {@code null} 回调（打开全部车站列表）。</li>
     * </ul>
     *
     * @param player   玩家
     * @param onSelect 选中回调（入参为系统 id，可能为 null）
     */
    public static void openOrSkip(Player player, Consumer<String> onSelect) {
        List<RailwaySystemInfo> systems = new ArrayList<>(RailwaySystemConfig.getSystems().values());
        if (systems.size() <= 1) {
            onSelect.accept(systems.isEmpty() ? null : systems.getFirst().getId());
            return;
        }
        new MenuSystem(player, onSelect).open();
    }
}
