package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicket;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
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

@Getter
public class TicketItem extends AbstractItem {
    private final BCTicket ticket;
    private Component errMsg;

    public TicketItem(String errMsg) {
        this.ticket = null;
        this.errMsg = Component.text(errMsg, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
    }

    public TicketItem(Component errMsg) {
        this.ticket = null;
        this.errMsg = errMsg;
    }

    public TicketItem(BCTicket ticket) {
        this.ticket = ticket;
    }

    @Override
    public ItemProvider getItemProvider() {
        if (ticket == null) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta barrierMeta = barrier.getItemMeta();
            barrierMeta.lore(List.of(errMsg));
            barrierMeta.displayName(Component.text(""));
            barrier.setItemMeta(barrierMeta);
            return new ItemBuilder(barrier);
        }
        return new ItemBuilder(ticket.getCommonItemStack().toBukkit());
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (ticket == null) {
            return;
        }
        if (!clickType.isCreativeAction()) {
            ticket.purchase();
        } else {
            ticket.give();
        }
        MenuMain.getMenu(player).close();
    }

    public void updateLore(PlayerOption playerOption) {
        if (ticket != null) {
            ticket.refreshTicketMeta(playerOption);
        }
    }
}
