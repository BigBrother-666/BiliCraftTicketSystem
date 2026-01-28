package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.listeners.CardListeners;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
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

public class ChargeItem extends AbstractItem {
    private final double maxCharge;
    private final BCCard card;

    public ChargeItem(BCCard card) {
        this.card = card;
        this.maxCharge = MainConfig.cardConfig.get("max-signle-charge", 1000);
    }

    @Override
    public ItemProvider getItemProvider(Player viewer) {
        ItemStack itemStack = Utils.loadItemFromFile("charge");
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.lore(List.of(
                Component.text("当前余额：%.2f".formatted(card.getBalance()), NamedTextColor.DARK_AQUA))
        );
        itemStack.setItemMeta(itemMeta);
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        player.showTitle(Title.title(Component.text("请在聊天框输入充值金额", NamedTextColor.GOLD), Component.text("单次最多充值%s".formatted(maxCharge), NamedTextColor.DARK_GRAY)));
        player.sendMessage(
                BiliCraftTicketSystem.PREFIX
                        .append(Component.text("请输入充值金额，或", NamedTextColor.GREEN))
                        .append(Component.text(" [取消充值] ", NamedTextColor.RED).decoration(TextDecoration.UNDERLINED, true).clickEvent(ClickEvent.callback(this::quitInputMode)))
        );
        CardListeners.inputModePlayers.add(player.getUniqueId());
        player.closeInventory();
    }

    private void quitInputMode(Audience audience) {
        if (audience instanceof Player player && CardListeners.inputModePlayers.contains(player.getUniqueId())) {
            CardListeners.inputModePlayers.remove(player.getUniqueId());
            audience.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("已取消充值", NamedTextColor.YELLOW)));
            MenuCard.getMenu(card, player).open();
        }
    }
}
