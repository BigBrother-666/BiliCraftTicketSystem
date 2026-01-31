package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
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

import java.util.ArrayList;
import java.util.List;

public class SpeedItem extends AbstractItem {
    private double speed = 4;

    @Override
    public ItemProvider getItemProvider() {
        ItemStack itemStack = Utils.loadItemFromFile("speed");
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("当前选择的速度：%.1fkm/h".formatted(Utils.mpt2Kph(speed)), NamedTextColor.DARK_AQUA));
        lore.add(Component.text(""));
        lore.add(Component.text("左键+%.1fkm/h，右键-%.1fkm/h".formatted(Utils.mpt2Kph(MainConfig.speedStep), Utils.mpt2Kph(MainConfig.speedStep)), NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("最大%.1fkm/h，最小%.1fkm/h".formatted(Utils.mpt2Kph(MainConfig.maxSpeed), Utils.mpt2Kph(MainConfig.minSpeed)), NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        MenuMain menu = MenuMain.getMenu(player);
        if (clickType.isLeftClick()) {
            double targetSpeed = speed + MainConfig.speedStep;
            targetSpeed = Math.min(MainConfig.maxSpeed, targetSpeed);
            speed = targetSpeed;
            menu.getPlayerOption().setSpeed(targetSpeed);
        } else if (clickType.isRightClick()) {
            double targetSpeed = speed - MainConfig.speedStep;
            targetSpeed = Math.max(MainConfig.minSpeed, targetSpeed);
            speed = targetSpeed;
            menu.getPlayerOption().setSpeed(targetSpeed);
        }
        notifyWindows();
        menu.updateTicketInfo();
    }
}
