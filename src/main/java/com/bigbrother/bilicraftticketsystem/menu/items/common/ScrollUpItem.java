package com.bigbrother.bilicraftticketsystem.menu.items.common;

import com.bigbrother.bilicraftticketsystem.Utils;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.controlitem.ScrollItem;

public class ScrollUpItem extends ScrollItem {
    public ScrollUpItem() {
        super(-1);
    }

    @Override
    public ItemProvider getItemProvider(ScrollGui<?> gui) {
        ItemBuilder builder = new ItemBuilder(Utils.loadItemFromFile("scrollup"));
        if (!gui.canScroll(-1))
            builder.addLoreLines("已经滚动到顶部");

        return builder;
    }
}
