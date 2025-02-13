package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.controlitem.ScrollItem;

public class ScrollDownItem extends ScrollItem {
    public ScrollDownItem() {
        super(1);
    }

    @Override
    public ItemProvider getItemProvider(ScrollGui<?> gui) {
        ItemBuilder builder = new ItemBuilder(Utils.loadItemFromFile("scrolldown"));
        if (!gui.canScroll(1))
            builder.addLoreLines("已经滚动到底部");

        return builder;
    }
}
