package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.AnvilWindow;

import java.util.function.Consumer;

/**
 * 车站铁砧搜索界面：购票主界面 / 交通卡界面起点/终点按钮 shift+左键打开。
 * <p>
 * 用 {@link AnvilWindow} 让玩家在重命名框输入关键字，点击输出槽确认后回调 {@code onConfirm}（带输入文本），
 * 由调用方用 {@link com.bigbrother.bilicraftticketsystem.menu.station.StationProvider#searchStations} 过滤并打开车站列表。
 * 空输入表示展示全部车站。每次打开都新建（非缓存）。
 */
public class MenuStationSearch extends Menu {
    /**
     * 重命名框实时输入的最新文本（由 AnvilWindow 的 renameHandler 写入）。
     */
    private String renameText = "";

    private MenuStationSearch(Player player, Consumer<String> onConfirm) {
        // 输入槽（slot 0）：提示物品，物品名即重命名框初始文本
        ItemBuilder inputItem = new ItemBuilder(CommonUtils.loadItemFromFile("search"))
                .setDisplayName(new AdventureComponentWrapper(
                        Component.text("输入要搜索的车站名", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));

        // 输出槽（slot 2）：点击确认搜索
        AbstractItem confirmItem = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(CommonUtils.loadItemFromFile("search"))
                        .setDisplayName(new AdventureComponentWrapper(
                                Component.text("点击搜索：", NamedTextColor.GOLD)
                                        .append(Component.text(renameText.isBlank() ? "（全部车站）" : renameText, NamedTextColor.AQUA))
                                        .decoration(TextDecoration.ITALIC, false)));
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player clicker, @NotNull InventoryClickEvent event) {
                close();
                onConfirm.accept(renameText);
            }
        };

        Gui gui = Gui.normal()
                .setStructure("a . b")
                .addIngredient('a', inputItem)
                .addIngredient('.', new SimpleItem(new ItemBuilder(org.bukkit.Material.AIR)))
                .addIngredient('b', confirmItem)
                .build();

        this.window = AnvilWindow.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(
                        Component.text("搜索车站", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)))
                .addRenameHandler(text -> {
                    renameText = text == null ? "" : text;
                    confirmItem.notifyWindows();
                })
                .setGui(gui)
                .build();
    }

    /**
     * 打开车站铁砧搜索界面。
     *
     * @param player    玩家
     * @param onConfirm 确认回调（入参为输入的关键字，空表示全部）
     */
    public static void open(Player player, Consumer<String> onConfirm) {
        new MenuStationSearch(player, onConfirm).open();
    }
}
