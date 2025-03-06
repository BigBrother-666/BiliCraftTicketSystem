package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.controlitem.PageItem;

import java.util.List;

public class NextpageItem extends PageItem {
    public NextpageItem() {
        super(true);
    }

    @Override
    public ItemProvider getItemProvider(PagedGui<?> gui) {
        ItemBuilder builder = new ItemBuilder(Utils.loadItemFromFile("nextpage"));
        if (gui.getPageAmount() > 0) {
            builder.setLore(List.of(new AdventureComponentWrapper(Component.text("当前页：%s / 总页数：%s".formatted(gui.getCurrentPage() + 1, gui.getPageAmount()), NamedTextColor.GOLD))));
        }
        return builder;
    }
}
