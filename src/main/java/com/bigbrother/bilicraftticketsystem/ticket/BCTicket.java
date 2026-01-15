package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;


@Getter
public class BCTicket {
    // Keys used in NBT
    public static final String KEY_TICKET_PLUGIN = "plugin";
    public static final String KEY_TICKET_NAME = "ticketName";
    public static final String KEY_TICKET_DISPLAY_NAME = "ticketDisplayName";
    public static final String KEY_TICKET_CREATION_TIME = "ticketCreationTime";
    public static final String KEY_TICKET_EXPIRATION_TIME = "ticketExpirationTime";
    public static final String KEY_TICKET_NUMBER_OF_USES = "ticketNumberOfUses";
    public static final String KEY_TICKET_MAX_NUMBER_OF_USES = "ticketMaxNumberOfUses";
    public static final String KEY_TICKET_OWNER_UUID = "ticketOwner";
    public static final String KEY_TICKET_OWNER_NAME = "ticketOwnerName";
    public static final String KEY_TICKET_MAX_SPEED = "ticketMaxSpeed";
    public static final String KEY_TICKET_ORIGIN_PRICE = "ticketOriginPrice";
    public static final String KEY_TICKET_TAGS = "ticketTags";
    public static final String KEY_TICKET_START_PLATFORM_TAG = "startPlatformTag";
    public static final String KEY_TICKET_START_STATION = "startStation";
    public static final String KEY_TICKET_END_STATION = "endStation";
    public static final String KEY_TICKET_DISTANCE = "distance";
    public static final String KEY_TICKET_BACKGROUND_IMAGE_PATH = "backgroundImagePath";

    private final CommonItemStack ticket;
    private final Player owner;

    private TrainRoutes.PathInfo pathInfo;
    private String ticketName;
    @Setter
    private int maxUses;
    @Setter
    private double maxSpeed;


    public BCTicket(PlayerOption option, TrainRoutes.PathInfo pathInfo, Player owner) {
        this.owner = owner;
        this.pathInfo = pathInfo;
        this.maxUses = option.getUses();
        this.maxSpeed = option.getSpeed();
        this.ticket = createItem();
        refreshTicketLore(true);
    }

    public BCTicket(int maxUses, double maxSpeed, TrainRoutes.PathInfo pathInfo, Player owner) {
        this.owner = owner;
        this.pathInfo = pathInfo;
        this.maxUses = maxUses;
        this.maxSpeed = maxSpeed;
        this.ticket = createItem();
        refreshTicketLore(true);
    }

    /**
     * 引用itemstack
     */
    private BCTicket(ItemStack itemStack, Player owner) {
        this.owner = owner;
        this.ticket = CommonItemStack.of(itemStack);

        CommonTagCompound nbt = ticket.getCustomData();
        this.maxUses = nbt.getValue(KEY_TICKET_MAX_NUMBER_OF_USES, 1);
        this.maxSpeed = nbt.getValue(KEY_TICKET_MAX_SPEED, 4.0);

        this.pathInfo = TrainRoutes.graph.getPathInfo(
                nbt.getValue(KEY_TICKET_START_PLATFORM_TAG, ""),
                new ArrayList<>(List.of(nbt.getValue(BCTicket.KEY_TICKET_TAGS, "").split(","))),
                nbt.getValue(KEY_TICKET_END_STATION, "")
        );
        if (pathInfo == null) {
            this.update();
        }
        refreshTicketLore(false);
    }

    @Nullable
    public static BCTicket fromItemStack(ItemStack ticket, Player owner) {
        if (!isBctsTicket(ticket)) {
            return null;
        }
        return new BCTicket(ticket, owner);
    }

    public void purchase() {
        EconomyResponse r = plugin.getEcon().withdrawPlayer(owner, this.getDiscountPrice());

        if (r.transactionSuccess()) {
            owner.sendMessage(MiniMessage.miniMessage().deserialize(message.get("buy-success", "您成功花费 %.2f 购买了 %s").formatted(r.amount, ticketName)).decoration(TextDecoration.ITALIC, false));
            this.give();
            // 记录log
            Bukkit.getConsoleSender().sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("玩家 %s 成功花费 %.2f 购买了 %s".formatted(owner.getName(), r.amount, ticketName), NamedTextColor.GREEN)));
            // 写入数据库
            plugin.getTrainDatabaseManager().addTicketInfo(owner.getName(), owner.getUniqueId().toString(), r.amount, ticket.getCustomData());
        } else {
            owner.sendMessage(MiniMessage.miniMessage().deserialize(message.get("buy-failure", "车票购买失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false));
        }
    }

    public void give() {
        ticket.updateCustomData(this::updateNbt);
        ItemStack itemStack = ticket.toBukkit();
        List<Component> lore = itemStack.lore();
        if (lore != null && lore.size() > 2) {
            lore.remove(lore.size() - 1);
            lore.remove(lore.size() - 1);
        }
        ItemStack newTicket = itemStack.clone();
        ItemMeta itemMeta = newTicket.getItemMeta();
        itemMeta.lore(lore);
        newTicket.setItemMeta(itemMeta);
        if (!owner.getInventory().addItem(newTicket).isEmpty()) {
            // 背包满 车票丢到地上
            owner.getWorld().dropItemNaturally(owner.getLocation(), newTicket);
        }
    }

    /**
     * 获取当前车票的单程票
     */
    public BCTicket getNewSingleTicket() {
        return new BCTicket(1, maxSpeed, pathInfo, owner);
    }

    public BCTicket getNewSingleTicket(Player player) {
        return new BCTicket(1, maxSpeed, pathInfo, player);
    }

    /**
     * 车票使用次数+1
     */
    public void useTicket() {
        useTicket(owner);
    }

    public void useTicket(Player uesdPlayer) {
        ticket.updateCustomData(tag -> tag.putValue(KEY_TICKET_NUMBER_OF_USES, tag.getValue(KEY_TICKET_NUMBER_OF_USES, 0) + 1));
        // 检查是否达到最大次数
        if (maxUses > 0 && maxUses <= ticket.getCustomData().getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0)) {
            ticket.setAmount(ticket.getAmount() - 1);
        }
        HumanHand.setItemInMainHand(uesdPlayer, ticket.toBukkit());
    }

    /**
     * 刷新车票的Lore，计算价格
     */
    public void refreshTicketLore(PlayerOption playerOption) {
        this.maxSpeed = playerOption.getSpeed();
        this.maxUses = playerOption.getUses();
        refreshTicketLore(true);
    }

    public void refreshTicketLore(boolean addPrice) {
        // 更新物品名
        if (maxUses == 1) {
            ticketName = "%s → %s 单次票".formatted(pathInfo.getStartStation().getStationName(), pathInfo.getEndStation().getStationName());
        } else {
            ticketName = "%s → %s %d次票".formatted(pathInfo.getStartStation().getStationName(), pathInfo.getEndStation().getStationName(), maxUses);
        }
        // 更新lore
        ItemStack itemStack = ticket.toBukkit();
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = getBaseTicketInfoLore();
        if (addPrice) {
            lore.add(Component.text("===============================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("售价：%.2f银币       左键点击购买".formatted(this.getDiscountPrice()), NamedTextColor.DARK_PURPLE));
        }
        itemMeta.lore(lore);
        itemMeta.displayName(Component.text(ticketName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        itemStack.setItemMeta(itemMeta);
    }

    /**
     * 获取车票item
     */
    private CommonItemStack createItem() {
        return CommonItemStack.of(MapDisplay.createMapItem(BCTicketDisplay.class))
                .updateCustomData(this::updateNbt)
                .setCustomNameMessage(MainConfig.expressTicketName);
    }

    private void updateNbt(CommonTagCompound tag) {
        tag.putValue(KEY_TICKET_PLUGIN, "bcts");
        tag.putValue(KEY_TICKET_NAME, MainConfig.expressTicketName);
        tag.putValue(KEY_TICKET_CREATION_TIME, System.currentTimeMillis());
        tag.putValue(KEY_TICKET_DISPLAY_NAME, "%s → %s".formatted(pathInfo.getStartStation().getClearStationName(), pathInfo.getEndStation().getClearStationName()));
        tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
        tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, maxUses);
        tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
        tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
        tag.putValue(KEY_TICKET_MAX_SPEED, maxSpeed);
        tag.putValue(KEY_TICKET_ORIGIN_PRICE, this.pathInfo.getPrice());
        tag.putValue(KEY_TICKET_TAGS, String.join(",", this.pathInfo.getTags()));
        tag.putValue(KEY_TICKET_START_PLATFORM_TAG, pathInfo.getStartPlatformTag());
        tag.putValue(KEY_TICKET_START_STATION, pathInfo.getStartStation().getStationName());
        tag.putValue(KEY_TICKET_END_STATION, pathInfo.getEndStation().getStationName());
        tag.putValue(KEY_TICKET_DISTANCE, pathInfo.getDistance());
        tag.putValue(KEY_TICKET_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
    }

    public double getDiscountPrice() {
        double totalPrice = pathInfo.getPrice() * maxUses;

        for (String s : MainConfig.discount) {
            String[] split = s.split("-");
            if (maxUses >= Integer.parseInt(split[0]) && maxUses <= Integer.parseInt(split[1])) {
                totalPrice *= Double.parseDouble(split[2]);
                break;
            }
        }

        Set<String> perms = MainConfig.permDiscount.getKeys();
        for (String perm : perms) {
            List<String> discount = MainConfig.permDiscount.getList(perm, String.class, null);
            if (!owner.hasPermission(perm.replace("-", ".")) || discount == null || discount.isEmpty()) {
                continue;
            }
            for (String s : discount) {
                String[] split = s.split("-");
                if (maxUses >= Integer.parseInt(split[0]) && maxUses <= Integer.parseInt(split[1])) {
                    return totalPrice * Double.parseDouble(split[2]);
                }
            }
        }
        return totalPrice;
    }

    private List<Component> getBaseTicketInfoLore() {
        List<Component> lore = new ArrayList<>();
        MermaidGraph.Node startStation = pathInfo.getStartStation();
        lore.add(Component.text("▶ 路线：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        lore.add(Component.text("%s ===直达===＞ %s".formatted(startStation.getStationName(), pathInfo.getEndStation().getStationName()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("▶ 车票使用方法：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        lore.add(Component.text("前往国铁 [%s] , 使用标有 [%s %s] 的发车装置发车, 主手拿本车票上车"
                        .formatted(startStation.getStationName(),
                                startStation.getRailwayName(),
                                startStation.getRailwayDirection()),
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        // 路线详情lore
        lore.add(Component.text("▶ 路线详情（只停起始站和终到站）：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<MermaidGraph.Node> path = pathInfo.getPath();
        Component join = Component.text("");
        List<String> railways = new ArrayList<>();
        int cnt = 0;
        for (int i = 0; i < path.size(); i++) {
            MermaidGraph.Node node = path.get(i);
            if (!node.isStation()) {
                continue;
            }

            String stationName = node.getStationName();
            String railwayName = node.getRailwayName() + node.getRailwayDirection();

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
                break;
            }
            if (cnt % 7 != 0) {
                // 经过站
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", Utils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, true));
            } else if (cnt == 0) {
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
            cnt += 1;
        }

        // 途经铁路lore
        lore.add(Component.text("▶ 途经铁路：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
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

        lore.add(Component.text("%.2f km | 最大速度 %.2f km/h".formatted(pathInfo.getDistance(), maxSpeed * 20 * 3.6), NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        return lore;
    }

    /**
     * 如果铁路有变化，更新车票信息
     */
    public void update() {
        CommonTagCompound nbt = ticket.getCustomData();
        List<String> tags = List.of(nbt.getValue(KEY_TICKET_TAGS, "").split(","));
        String startStation = nbt.getValue(KEY_TICKET_START_STATION, String.class, "");
        String endStation = nbt.getValue(KEY_TICKET_END_STATION, String.class, "");
        String startPlatformTag = nbt.getValue(KEY_TICKET_START_PLATFORM_TAG, String.class, "");
        List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(startStation, endStation);
        if (!pathInfoList.isEmpty()) {
            boolean updated = false;
            for (TrainRoutes.PathInfo path : pathInfoList) {
                // 新车票包含所有旧车票的tag 则认为是同一路线的车票
                if (path.getTags().containsAll(tags)) {
                    if (!startStation.equals(endStation) && !startPlatformTag.equals(path.getStartPlatformTag())) {
                        // 线路延长，xxx方向改变
                        ticket.updateCustomData(tag -> tag.putValue(KEY_TICKET_START_PLATFORM_TAG, path.getStartPlatformTag()));
                        startPlatformTag = path.getStartPlatformTag();
                    }

                    if (path.getTags().size() != tags.size() && startPlatformTag.equals(path.getStartPlatformTag())) {
                        // 新增车站
                        ticket.updateCustomData(tag -> tag.putValue(KEY_TICKET_TAGS, String.join(",", path.getTags())));
                    }

                    ticket.updateCustomData(tag -> tag.putValue(KEY_TICKET_PLUGIN, "bcts"));
                    this.pathInfo = path;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                // 保底
                this.pathInfo = pathInfoList.get(0);
                this.ticket.updateCustomData(this::updateNbt);
                HumanHand.setItemInMainHand(owner, ticket.toBukkit());
            }
        } else {
            // 标记为过期车票
            ticket.updateCustomData(tag -> tag.putValue(KEY_TICKET_EXPIRATION_TIME, 0));
        }
    }

    public static boolean isBctsTicket(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        CommonTagCompound nbt = CommonItemStack.of(itemStack).getCustomData();
        return nbt != null && nbt.getValue(KEY_TICKET_NAME, "").equals(MainConfig.expressTicketName);
    }

    public boolean isTicketOwner(Player player) {
        CommonTagCompound nbt = ticket.getCustomData();
        return !nbt.containsKey(KEY_TICKET_OWNER_UUID) || nbt.getUUID(KEY_TICKET_OWNER_UUID).equals(player.getUniqueId());
    }

    public boolean isTicketExpired() {
        if (ticket == null) {
            return true;
        } else {
            CommonTagCompound nbt = ticket.getCustomData();
            if (maxUses >= 0) {
                int numberOfUses = nbt.getValue(KEY_TICKET_NUMBER_OF_USES, 0);
                if (numberOfUses >= maxUses) {
                    return true;
                }
            }
            if (nbt.getValue(KEY_TICKET_EXPIRATION_TIME, -1) >= 0) {
                long timeNow = System.currentTimeMillis();
                long timeCreated = nbt.getValue(KEY_TICKET_CREATION_TIME, timeNow);
                return timeNow >= (timeCreated + nbt.getValue(KEY_TICKET_EXPIRATION_TIME, 0));
            }
            return false;
        }
    }
}
