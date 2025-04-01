package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.trainDatabaseManager;

public class SharedbgItem extends BgItem {
    public SharedbgItem(TicketbgInfo ticketbgInfo) {
        super.setTicketbgInfo(ticketbgInfo);
    }

    @Override
    public ItemProvider getItemProvider(Player viewer) {
        TicketbgInfo ticketbgInfo = super.getTicketbgInfo();
        if (ticketbgInfo == null) {
            return ItemProvider.EMPTY;
        }

        ItemStack itemStack;
        if (isUseCurrTicketbg(viewer)) {
            itemStack = Utils.loadItemFromFile("usedbg");
        } else {
            itemStack = Utils.loadItemFromFile("sharedbg");
        }

        // 添加信息
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = itemMeta.lore();
        // 设置物品名
        itemMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(ticketbgInfo.getItemName()));
        // 设置lore
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.add(Component.text("========== 背景图信息 ==========", NamedTextColor.DARK_PURPLE));
        lore.add(Component.text("上传玩家：" + ticketbgInfo.getPlayerName(), NamedTextColor.GREEN));
        lore.add(Component.text("上传时间：" + ticketbgInfo.getUploadTime(), NamedTextColor.GREEN));
        lore.add(Component.text("使用人数：" + ticketbgInfo.getUsageCount(), NamedTextColor.GREEN));
        lore.add(Component.text("===============================", NamedTextColor.DARK_PURPLE));
        if (isUseCurrTicketbg(viewer)) {
            lore.add(Component.text("正在使用此背景", NamedTextColor.GREEN));
        } else {
            if (viewer.hasPermission("bcts.ticket.deletebg")) {
                lore.add(Component.text("左键使用此背景，shift+左键删除该背景", NamedTextColor.GOLD));
            } else {
                lore.add(Component.text("左键使用此背景", NamedTextColor.GOLD));
            }
        }
        itemMeta.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);

        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        ItemStack currentItem = inventoryClickEvent.getCurrentItem();
        if (currentItem == null || currentItem.getType().equals(Material.AIR) || super.isCooldown(player)) {
            return;
        }

        if (clickType.isLeftClick() && !clickType.isShiftClick()) {
            if (isUseCurrTicketbg(player)) {
                player.sendMessage(Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD).append(Component.text("当前正在使用此背景！", NamedTextColor.YELLOW)));
                return;
            }
            trainDatabaseManager.updateUsageTicketbg(this.getTicketbgInfo().getId(), player.getUniqueId().toString());
            MenuTicketbg.getTicketbgUsageMapping().put(player.getUniqueId(), this.getTicketbgInfo());
            MenuTicketbg.updateAllWindows();
            player.sendMessage(Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD).append(Component.text("设置背景图成功", NamedTextColor.GREEN)));
        } else if (clickType.isShiftClick() && player.hasPermission("bcts.ticket.deletebg")) {
            trainDatabaseManager.deleteTicketbgLogical(this.getTicketbgInfo().getId());
            player.sendMessage(Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD).append(Component.text("删除背景图成功", NamedTextColor.GREEN)));
            MenuTicketbg.updateAllWindows();
        }
    }
}
