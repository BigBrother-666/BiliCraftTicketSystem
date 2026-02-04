package com.bigbrother.bilicraftticketsystem.menu.items.ticketbg;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

@Setter
public class SelfbgItem extends BgItem {
    public SelfbgItem(TicketbgInfo ticketbgInfo) {
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
            itemStack = CommonUtils.loadItemFromFile("usedbg");
        } else {
            itemStack = CommonUtils.loadItemFromFile("selfbg");
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
        lore.add(Component.text("========== 背景图信息 ==========", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("上传玩家：" + ticketbgInfo.getPlayerName(), NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("上传时间：" + ticketbgInfo.getUploadTime(), NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("使用人数：" + ticketbgInfo.getUsageCount(), NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("字体颜色：", NamedTextColor.DARK_AQUA).append(Component.text("■", TextColor.fromHexString(ticketbgInfo.getFontColor()))).decoration(TextDecoration.ITALIC, false));
        if (ticketbgInfo.isShared()) {
            lore.add(Component.text("已共享", NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("未共享", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text("===============================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        if (isUseCurrTicketbg(viewer)) {
            lore.add(Component.text("正在使用此背景", NamedTextColor.AQUA));
            lore.add(Component.text("右键共享/取消共享此背景，shift+左键删除此背景", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("左键使用此背景，右键共享/取消共享此背景，shift+左键删除此背景", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("当前正在使用此背景！", NamedTextColor.YELLOW)));
                return;
            }
            plugin.getTrainDatabaseManager().getTicketbgService().updateUsageTicketbg(this.getTicketbgInfo().getId(), player.getUniqueId().toString());
            MenuTicketbg.getTicketbgUsageMapping().put(player.getUniqueId(), this.getTicketbgInfo());
            MenuTicketbg.updateAllWindows();
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("设置背景图成功", NamedTextColor.GREEN)));
        } else if (clickType.isRightClick()) {
            plugin.getTrainDatabaseManager().getTicketbgService().setShared(this.getTicketbgInfo().getId(), !this.getTicketbgInfo().isShared());
            if (this.getTicketbgInfo().isShared()) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("取消共享背景图成功", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("共享背景图成功", NamedTextColor.GREEN)));
            }
            MenuTicketbg.updateAllWindows();
        } else if (clickType.isLeftClick()) {
            plugin.getTrainDatabaseManager().getTicketbgService().deleteTicketbgLogical(this.getTicketbgInfo().getId());
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("删除背景图成功", NamedTextColor.GREEN)));
            MenuTicketbg.updateAllWindows();
        }
    }
}
