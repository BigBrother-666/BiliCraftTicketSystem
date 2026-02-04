package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CardCommand {
    private final BiliCraftTicketSystem plugin;

    public CardCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("给予一名玩家一张未开卡或已存在的交通卡")
    @Command("ticket card give <player> [cardUUID]")
    @Permission("bcts.ticket.getcard")
    public void give(
            CommandSender sender,
            @Argument(value = "player", description = "给予的玩家") Player player,
            @Nullable @Argument(value = "cardUUID", description = "交通卡UUID", suggestions = "cardUUID") UUID cardUUID
    ) {
        if (cardUUID == null) {
            player.getInventory().addItem(BCCard.getEmptyCard());
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("成功给予玩家%s一张未开卡的交通卡".formatted(player.getName()), NamedTextColor.GREEN)));
        } else {
            BCCard bcCard = BCCard.fromUuid(cardUUID.toString());
            if (bcCard != null) {
                bcCard.refreshCard();
                player.getInventory().addItem(bcCard.getItemStack());
            } else {
                sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("没有此uuid的交通卡！")));
            }
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("成功给予玩家%s一张交通卡（%s）".formatted(player.getName(), cardUUID), NamedTextColor.GREEN)));
        }
    }

    @CommandDescription("删除指定UUID的交通卡")
    @Command("ticket card delete <cardUUID>")
    @Permission("bcts.ticket.delcard")
    public void give(
            CommandSender sender,
            @Argument(value = "cardUUID", description = "交通卡UUID", suggestions = "cardUUID") UUID cardUUID
    ) {
        int deleted = plugin.getTrainDatabaseManager().getCardInfoService().deleteByCardUuid(cardUUID.toString());
        if (deleted <= 0) {
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("没有找到交通卡 %s".formatted(cardUUID), NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("成功删除交通卡 %s".formatted(cardUUID), NamedTextColor.GREEN)));
        }
    }
}
