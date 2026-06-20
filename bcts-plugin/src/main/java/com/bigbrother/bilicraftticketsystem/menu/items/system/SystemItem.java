package com.bigbrother.bilicraftticketsystem.menu.items.system;

import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.List;
import java.util.function.Consumer;

/**
 * 铁路系统选择界面（{@link com.bigbrother.bilicraftticketsystem.menu.impl.MenuSystem}）中的一个系统图标。
 * <p>
 * 图标统一使用 {@code menuitems.yml} 的 {@code system} 项（缺失时 {@link CommonUtils#loadItemFromFile} 回退 RAIL），
 * 显示名为系统名。点击后回调 {@code onSelect}，把选中的系统 id 交给调用方（购票 / 交通卡）打开过滤后的车站列表。
 */
public class SystemItem extends AbstractItem {
    /**
     * {@code menuitems.yml} 中铁路系统图标的物品 key；缺失时回退 RAIL。
     */
    private static final String SYSTEM_ITEM_KEY = "system";

    private final RailwaySystemInfo system;
    private final Consumer<String> onSelect;

    /**
     * @param system   铁路系统信息
     * @param onSelect 点击回调，入参为系统 id
     */
    public SystemItem(RailwaySystemInfo system, Consumer<String> onSelect) {
        this.system = system;
        this.onSelect = onSelect;
    }

    @Override
    public ItemProvider getItemProvider(Player player) {
        ItemStack item = CommonUtils.loadItemFromFile(SYSTEM_ITEM_KEY);
        List<String> lines = LineConfig.getLineIdsOfSystem(system.getId());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(system.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("线路数量：" + lines.size(), NamedTextColor.DARK_AQUA),
                Component.text("车站数量：" + StationProvider.listStationsOfSystem(system.getId()).size(), NamedTextColor.DARK_AQUA),
                Component.text("", NamedTextColor.DARK_AQUA),
                Component.text("每公里价格：%.2f".formatted(system.getPricePerKm()), NamedTextColor.DARK_PURPLE)

        ));
        item.setItemMeta(meta);
        return new ItemBuilder(item);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        onSelect.accept(system.getId());
    }
}
