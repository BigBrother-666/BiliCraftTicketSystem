package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;


@Getter
public class BCTicket {
    // Keys used in NBT
    public static final String KEY_TICKET_NAME = "ticketName";
    public static final String KEY_TICKET_DISPLAY_NAME = "ticketDisplayName";
    public static final String KEY_TICKET_CREATION_TIME = "ticketCreationTime";
    public static final String KEY_TICKET_NUMBER_OF_USES = "ticketNumberOfUses";
    public static final String KEY_TICKET_MAX_NUMBER_OF_USES = "ticketMaxNumberOfUses";
    public static final String KEY_TICKET_OWNER_UUID = "ticketOwner";
    public static final String KEY_TICKET_OWNER_NAME = "ticketOwnerName";
    public static final String KEY_TICKET_MAX_SPEED = "ticketMaxSpeed";
    public static final String KEY_TICKET_ORIGIN_PRICE = "ticketOriginPrice";
    public static final String KEY_TICKET_TAGS = "ticketTags";
    public static final String KEY_TICKET_START_PLATFORM_TAG = "startPlatformTag";
    public static final String KEY_TICKET_VERSION = "version";
    public static final String KEY_TICKET_START_STATION = "startStation";
    public static final String KEY_TICKET_END_STATION = "endStation";
    public static final String KEY_TICKET_DISTANCE = "distance";
    public static final String KEY_TICKET_BACKGROUND_IMAGE_PATH = "backgroundImagePath";

    private final PlayerOption option;
    private final TrainRoutes.PathInfo pathInfo;
    private String itemName;
    private double totalPrice;
    private ItemStack ticket;
    private final Player owner;

    public BCTicket(PlayerOption option, TrainRoutes.PathInfo pathInfo, Player owner) {
        this.option = option;
        this.pathInfo = pathInfo;
        this.owner = owner;
        this.ticket = createItem(owner);
        updateTicketInfo();
    }

    public void give() {
        this.ticket = createItem(owner);
        updateTicketInfo();
        List<Component> lore = ticket.lore();
        if (lore != null && lore.size() > 2) {
            lore.remove(lore.size() - 1);
            lore.remove(lore.size() - 1);
        }
        ItemStack newTicket = ticket.clone();
        newTicket.lore(lore);
        if (!owner.getInventory().addItem(newTicket).isEmpty()) {
            // 背包满 车票丢到地上
            owner.getWorld().dropItemNaturally(owner.getLocation(), newTicket);
        }
    }

    /**
     * 更新车票的lore和name，重新计算价格
     * 不更新nbt
     */
    public void updateTicketInfo() {
        // 更新价格
        totalPrice = pathInfo.getPrice() * option.getUses();
        for (String s : MainConfig.discount) {
            String[] split = s.split("-");
            if (option.getUses() >= Integer.parseInt(split[0]) && option.getUses() <= Integer.parseInt(split[1])) {
                totalPrice = pathInfo.getPrice() * option.getUses() * Double.parseDouble(split[2]);
                break;
            }
        }
        totalPrice = getDiscountPrice(owner, option.getUses(), totalPrice);

        // 更新物品名
        itemName = option.getUses() == 1 ? "%s → %s 单次票".formatted(pathInfo.getStart(), pathInfo.getEnd()) : "%s → %s %s次票".formatted(pathInfo.getStart(), pathInfo.getEnd(), option.getUses());

        // 更新lore
        ItemMeta itemMeta = ticket.getItemMeta();
        List<Component> lore = getBaseTicketInfoLore(pathInfo, option.getSpeed());
        lore.add(Component.text("===============================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("售价：%.2f银币       左键点击购买".formatted(this.totalPrice), NamedTextColor.DARK_PURPLE));
        itemMeta.lore(lore);
        itemMeta.displayName(Component.text(itemName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        ticket.setItemMeta(itemMeta);
    }

    private ItemStack createItem(Player owner) {
        return CommonItemStack.of(MapDisplay.createMapItem(BCTicketDisplay.class))
                .updateCustomData(tag -> {
                    tag.putValue("plugin", "TrainCarts");
                    tag.putValue(KEY_TICKET_NAME, MainConfig.expressTicketName);
                    tag.putValue(KEY_TICKET_CREATION_TIME, System.currentTimeMillis());
                    tag.putValue(KEY_TICKET_DISPLAY_NAME, "%s → %s".formatted(pathInfo.getStart().substring(0, pathInfo.getStart().lastIndexOf("站")), pathInfo.getEnd().substring(0, pathInfo.getEnd().lastIndexOf("站"))));
                    tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
                    tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, option.getUses());
                    tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
                    tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
                    tag.putValue(KEY_TICKET_MAX_SPEED, option.getSpeed());
                    tag.putValue(KEY_TICKET_ORIGIN_PRICE, this.pathInfo.getPrice());
                    tag.putValue(KEY_TICKET_TAGS, String.join(",", this.pathInfo.getTags()));
                    tag.putValue(KEY_TICKET_START_PLATFORM_TAG, pathInfo.getStartPlatformTag());
                    tag.putValue(KEY_TICKET_VERSION, MainConfig.expressTicketVersion);
                    tag.putValue(KEY_TICKET_START_STATION, option.getStartStationString());
                    tag.putValue(KEY_TICKET_END_STATION, option.getEndStationString());
                    tag.putValue(KEY_TICKET_DISTANCE, pathInfo.getDistance());
                    tag.putValue(KEY_TICKET_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
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
                    tag.putValue(KEY_TICKET_DISPLAY_NAME, customData.getValue(KEY_TICKET_DISPLAY_NAME, MainConfig.expressTicketName));
                    tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
                    tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, 1);
                    tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
                    tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
                    tag.putValue(KEY_TICKET_MAX_SPEED, customData.getValue(KEY_TICKET_MAX_SPEED, 2));
                    tag.putValue(KEY_TICKET_ORIGIN_PRICE, customData.getValue(KEY_TICKET_ORIGIN_PRICE, 0.0));
                    tag.putValue(KEY_TICKET_TAGS, customData.getValue(KEY_TICKET_TAGS, ""));
                    tag.putValue(KEY_TICKET_START_PLATFORM_TAG, customData.getValue(KEY_TICKET_START_PLATFORM_TAG, ""));
                    tag.putValue(KEY_TICKET_VERSION, customData.getValue(KEY_TICKET_VERSION, 1));
                    tag.putValue(KEY_TICKET_START_STATION, customData.getValue(KEY_TICKET_START_STATION, ""));
                    tag.putValue(KEY_TICKET_END_STATION, customData.getValue(KEY_TICKET_END_STATION, ""));
                    tag.putValue(KEY_TICKET_DISTANCE, customData.getValue(KEY_TICKET_DISTANCE, 0.0));
                    tag.putValue(KEY_TICKET_BACKGROUND_IMAGE_PATH, customData.getValue(KEY_TICKET_BACKGROUND_IMAGE_PATH, ""));
                })
                .setCustomNameMessage(MainConfig.expressTicketName);
        for (ChatText lore : origin.getLores()) {
            commonItemStack.addLore(lore);
        }
        commonItemStack.setCustomName(origin.getDisplayName());
        return commonItemStack;
    }

    public static double getDiscountPrice(Player player, int maxUses, double price) {
        Set<String> perms = MainConfig.permDiscount.getKeys();
        for (String perm : perms) {
            List<String> discount = MainConfig.permDiscount.getList(perm, String.class, null);
            if (!player.hasPermission(perm.replace("-", ".")) || discount == null || discount.isEmpty()) {
                continue;
            }
            for (String s : discount) {
                String[] split = s.split("-");
                if (maxUses >= Integer.parseInt(split[0]) && maxUses <= Integer.parseInt(split[1])) {
                    return price * Double.parseDouble(split[2]);
                }
            }
        }
        return price;
    }

    public static List<Component> getBaseTicketInfoLore(TrainRoutes.PathInfo pathInfo, double speed) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("起始站 ---> 终到站：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        lore.add(Component.text("%s ---直达---> %s".formatted(pathInfo.getStart(), pathInfo.getEnd()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可使用的站台：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        lore.add(Component.text(pathInfo.getStartPlatform(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        // 路线详情lore
        lore.add(Component.text("路线详情（只停起始站和终到站）：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<TrainRoutes.StationAndRailway> path = pathInfo.getPath();
        Component join = Component.text("");
        List<String> railways = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            String stationName = path.get(i).getStationName();
            String railwayName = path.get(i).getRailwayName();

            if (!railways.isEmpty() && !railways.get(railways.size() - 1).equals(railwayName)) {
                railways.add(railwayName);
            } else if (railways.isEmpty()) {
                railways.add(railwayName);
            }

            if (i == path.size() - 1) {
                // 终到站
                join = join.append(Component.text(stationName, NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                continue;
            }
            if (i % 7 != 0) {
                // 经过站
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", Utils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, true));
            } else if (i == 0) {
                // 起始站
                join = join.append(Component.text(stationName, NamedTextColor.GOLD)
                        .append(Component.text("→", Utils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", Utils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, true));
            }
        }

        // 途经铁路lore
        lore.add(Component.text("途经铁路：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        Component stationsLore = Component.text("");
        for (int i = 0; i < railways.size(); i++) {
            if (i != 0 && i % 4 == 0) {
                lore.add(stationsLore);
                stationsLore = Component.text("");
            }
            if (i == railways.size() - 1) {
                stationsLore = stationsLore.append(Component.text(railways.get(i), Utils.getRailwayColor(railways.get(i))));
            } else {
                stationsLore = stationsLore.append(Component.text(railways.get(i) + "→", Utils.getRailwayColor(railways.get(i))));
            }
        }
        lore.add(stationsLore);

        lore.add(Component.text("共 %.2f km | 最大速度 %.2f km/h".formatted(pathInfo.getDistance(), speed * 20 * 3.6), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        return lore;
    }
}
