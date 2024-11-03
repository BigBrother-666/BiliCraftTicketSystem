package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


@AllArgsConstructor
@Getter
public class BCTicket {
    // Keys used in NBT
    public static final String KEY_TICKET_NAME = "ticketName";
    public static final String KEY_TICKET_CREATION_TIME = "ticketCreationTime";
    public static final String KEY_TICKET_NUMBER_OF_USES = "ticketNumberOfUses";
    public static final String KEY_TICKET_MAX_NUMBER_OF_USES = "ticketMaxNumberOfUses";
    public static final String KEY_TICKET_OWNER_UUID = "ticketOwner";
    public static final String KEY_TICKET_OWNER_NAME = "ticketOwnerName";
    public static final String KEY_TICKET_MAX_SPEED = "ticketMaxSpeed";
    public static final String KEY_TICKET_ORIGIN_PRICE = "ticketOriginPrice";
    public static final String KEY_TICKET_ITEM_NAME = "ticketItemName";
    public static final String KEY_TICKET_TAGS = "ticketTags";
    public static final String KEY_TICKET_START_PLATFORM_TAG = "startPlatformTag";

    private PlayerOption option;
    private TrainRoutes.PathInfo pathInfo;
    private String itemName;
    private double totalPrice;

    public static BCTicket createTicket(PlayerOption option, TrainRoutes.PathInfo info) {
        String name = option.getUses() == 1 ? "%s->%s 单次票".formatted(info.getStart(), info.getEnd()) : "%s->%s %s次票".formatted(info.getStart(), info.getEnd(), option.getUses());
        double totalPrice = info.getPrice() * option.getUses();
        for (String s : MainConfig.discount) {
            String[] split = s.split("-");
            if (option.getUses() >= Integer.parseInt(split[0]) && option.getUses() <= Integer.parseInt(split[1])) {
                totalPrice = info.getPrice() * option.getUses() * Double.parseDouble(split[2]);
                break;
            }
        }

        return new BCTicket(option, info, name, totalPrice);
    }

    public ItemStack getItem(Player player) {
        ItemStack item = createItem(player);
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("起始站 ---> 终到站：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("%s ---直达---> %s".formatted(pathInfo.getStart(), pathInfo.getEnd()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可使用的站台：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(pathInfo.getStartPlatform(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("路线详情（只停起始站和终到站）：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        List<String> path = pathInfo.getPath();
        Component join = Component.text("");
        for (int i = 0; i < path.size(); i++) {
            if (i == path.size() - 1) {
                join = join.append(Component.text(path.get(i), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                continue;
            }
            if (i % 7 != 0) {
                join = join.append(Component.text(path.get(i) + "→", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else if (i == 0) {
                join = join.append(Component.text(path.get(i) + "→", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(path.get(i) + "→", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text("共%.2fkm".formatted(pathInfo.getDistance()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("==========================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("售价：%.2f银币       左键点击购买".formatted(this.totalPrice), NamedTextColor.DARK_PURPLE));
        itemMeta.lore(lore);
        itemMeta.displayName(Component.text(itemName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        item.setItemMeta(itemMeta);
        return item;
    }

    public void giveTo(Player player) {
        ItemStack item = getItem(player);
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = itemMeta.lore();
        if (lore != null && lore.size() > 2) {
            lore.remove(lore.size() - 1);
            lore.remove(lore.size() - 1);
        }
        itemMeta.lore(lore);
        item.setItemMeta(itemMeta);
        if (!player.getInventory().addItem(item).isEmpty()) {
            // 背包满 车票丢到地上
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    public void updateProperties(PlayerOption option) {
        this.option = option;

        // 重新计算票价
        double totalPrice = this.pathInfo.getPrice() * option.getUses();
        for (String s : MainConfig.discount) {
            String[] split = s.split("-");
            if (option.getUses() >= Integer.parseInt(split[0]) && option.getUses() <= Integer.parseInt(split[1])) {
                totalPrice = this.pathInfo.getPrice() * option.getUses() * Double.parseDouble(split[2]);
                break;
            }
        }
        this.totalPrice = totalPrice;

        // 更新车票名
        this.itemName = option.getUses() == 1 ?
                "%s->%s 单次票".formatted(this.pathInfo.getStart(), this.pathInfo.getEnd()) :
                "%s->%s %s次票".formatted(this.pathInfo.getStart(), this.pathInfo.getEnd(), option.getUses());
    }

    private ItemStack createItem(Player owner) {
        return CommonItemStack.of(MapDisplay.createMapItem(BCTicketDisplay.class))
                .updateCustomData(tag -> {
                    tag.putValue("plugin", "TrainCarts");
                    tag.putValue(KEY_TICKET_NAME, MainConfig.expressTicketName);
                    tag.putValue(KEY_TICKET_CREATION_TIME, System.currentTimeMillis());
                    tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
                    tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, option.getUses());
                    tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
                    tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
                    tag.putValue(KEY_TICKET_MAX_SPEED, option.getSpeed());
                    tag.putValue(KEY_TICKET_ORIGIN_PRICE, this.pathInfo.getPrice());
                    tag.putValue(KEY_TICKET_ITEM_NAME, this.itemName);
                    tag.putValue(KEY_TICKET_TAGS, String.join(",", this.pathInfo.getTags()));
                    tag.putValue(KEY_TICKET_START_PLATFORM_TAG, pathInfo.getStartPlatformTag());
                })
                .setCustomNameMessage(MainConfig.expressTicketName)
                .toBukkit();
    }

    public static CommonItemStack deepCopy(Player owner, CommonItemStack origin) {
        CommonTagCompound customData = origin.getCustomData();

        CommonItemStack commonItemStack = CommonItemStack.of(MapDisplay.createMapItem(BCTicketDisplay.class))
                .updateCustomData(tag -> {
                    tag.putValue("plugin", "TrainCarts");
                    tag.putValue(KEY_TICKET_NAME, MainConfig.expressTicketName);
                    tag.putValue(KEY_TICKET_CREATION_TIME, System.currentTimeMillis());
                    tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
                    tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, 1);
                    tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
                    tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
                    tag.putValue(KEY_TICKET_MAX_SPEED, customData.getValue(KEY_TICKET_MAX_SPEED, 2));
                    tag.putValue(KEY_TICKET_ORIGIN_PRICE, customData.getValue(KEY_TICKET_ORIGIN_PRICE, 0.0));
                    tag.putValue(KEY_TICKET_ITEM_NAME, customData.getValue(KEY_TICKET_ITEM_NAME, "Unknown"));
                    tag.putValue(KEY_TICKET_TAGS, customData.getValue(KEY_TICKET_TAGS, ""));
                    tag.putValue(KEY_TICKET_START_PLATFORM_TAG, customData.getValue(KEY_TICKET_START_PLATFORM_TAG, ""));
                })
                .setCustomNameMessage(MainConfig.expressTicketName);
        for (ChatText lore : origin.getLores()) {
            commonItemStack.addLore(lore);
        }
        commonItemStack.setCustomName(origin.getDisplayName());
        return commonItemStack;
    }
}
