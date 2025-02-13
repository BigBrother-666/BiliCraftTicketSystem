package com.bigbrother.bilicraftticketsystem.menu.items;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

public class WarnItem extends AbstractItem {
    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(Utils.loadItemFromFile("warn"));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(MainConfig.message.get("wiki", "https://www.yuque.com/sasanarx/bilicraft/iunb5gkspevng8gf#")));
        MenuMain.getMenu(player).close();
    }
}
