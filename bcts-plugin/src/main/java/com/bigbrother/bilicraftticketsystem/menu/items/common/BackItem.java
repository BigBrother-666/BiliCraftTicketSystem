package com.bigbrother.bilicraftticketsystem.menu.items.common;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.function.Supplier;

/**
 * 通用「返回上一级」按钮：点击后执行传入的返回动作（通常是打开上一级界面）。
 * 图标取 {@code menuitems.yml} 的 {@code back} 项。
 * <p>
 * 返回动作用 {@link Supplier} 提供而非直接传 {@link Runnable}，以支持缓存型界面
 * （如 {@link com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation}）在每次打开时
 * 更新返回目标：按钮只在构造时创建一次，点击时才现取最新的返回动作。
 */
public class BackItem extends AbstractItem {
    private final Supplier<Runnable> backSupplier;

    /**
     * 返回目标固定不变时使用（如非缓存、每次新建的界面）。
     */
    public BackItem(Runnable back) {
        this(() -> back);
    }

    /**
     * 返回目标可能随每次打开变化时使用（如缓存复用的界面），点击时才求值。
     */
    public BackItem(Supplier<Runnable> backSupplier) {
        this.backSupplier = backSupplier;
    }

    @Override
    public ItemProvider getItemProvider(Player player) {
        return new ItemBuilder(CommonUtils.loadItemFromFile("back"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        Runnable back = backSupplier == null ? null : backSupplier.get();
        if (back != null) {
            back.run();
        }
    }
}
