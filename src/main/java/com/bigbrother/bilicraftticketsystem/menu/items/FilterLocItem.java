package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.MenuFilter;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.ArrayList;
import java.util.List;

@Getter
public class FilterLocItem extends AbstractItem {
    private final ItemStack flagItem;

    @Setter
    private boolean selected;

    public FilterLocItem(ItemStack flagItem) {
        this.flagItem = flagItem;
        this.selected = false;
    }

    @Override
    public ItemProvider getItemProvider() {
        if (selected) {
            ItemStack selectedItem = Utils.loadItemFromFile("selected");
            ItemMeta itemMeta = selectedItem.getItemMeta();
            itemMeta.displayName(flagItem.getItemMeta().displayName());
            List<Component> lore = flagItem.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text("已选择", NamedTextColor.GREEN));
            itemMeta.lore(lore);
            selectedItem.setItemMeta(itemMeta);
            return new ItemBuilder(selectedItem);
        }
        return new ItemBuilder(flagItem);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (inventoryClickEvent.getCurrentItem() == null) {
            return;
        }

        if (clickType.isLeftClick() && !selected) {
            // 左键，加入筛选
            this.selected = true;
            MenuFilter.getMenu(player).getFilterStations().add(Utils.component2Str(flagItem.displayName()));
            notifyWindows();
        } else if (clickType.isRightClick() && selected) {
            //右键取消
            this.selected = false;
            MenuFilter.getMenu(player).getFilterStations().remove(Utils.component2Str(flagItem.displayName()));
            notifyWindows();
        }
    }
}
