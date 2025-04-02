package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class SortItem extends BgItem {
    private SortField sortField;

    public SortItem() {
        this.sortField = SortField.UPLOAD_TIME;
    }

    @Override
    public ItemProvider getItemProvider(Player viewer) {
        ItemStack itemStack = Utils.loadItemFromFile("sort");
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = itemMeta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        if (sortField == SortField.UPLOAD_TIME) {
            lore.add(Component.text("当前：上传时间倒序"));
        } else if (sortField == SortField.USAGE_COUNT) {
            lore.add(Component.text("当前：使用人数倒序"));
        }
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (super.isCooldown(player)) {
            return;
        }

        if (clickType.isLeftClick()) {
            this.sortField = SortField.UPLOAD_TIME;
        } else if (clickType.isRightClick()) {
            this.sortField = SortField.USAGE_COUNT;
        } else {
            return;
        }
        MenuTicketbg.getMenu(player).asyncUpdateAllTicketbg();
        notifyWindows();
    }
}
