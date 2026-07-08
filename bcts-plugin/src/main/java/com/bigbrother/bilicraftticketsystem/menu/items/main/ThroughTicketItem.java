package com.bigbrother.bilicraftticketsystem.menu.items.main;

import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.ticket.ThroughTicket;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

/**
 * 购票菜单里的联程票卡片：与普通车票并列展示，点击购买则一次性扣总价、交付两段独立车票。
 */
@Getter
public class ThroughTicketItem extends AbstractItem {
    private final ThroughTicket throughTicket;

    public ThroughTicketItem(ThroughTicket throughTicket) {
        this.throughTicket = throughTicket;
    }

    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(throughTicket.getItemStack());
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        if (!clickType.isCreativeAction()) {
            throughTicket.purchase();
        } else {
            throughTicket.give();
        }
        MenuMain.getMenu(player).close();
    }

    public void updateLore(PlayerOption playerOption) {
        throughTicket.refreshMeta(playerOption);
    }
}
