package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;


public class BCTicket extends BCTransitPass {
    // Keys used in NBT
    public static final String KEY_TICKET_NAME = "ticketName";
    public static final String KEY_TICKET_CREATION_TIME = "ticketCreationTime";
    public static final String KEY_TICKET_EXPIRATION_TIME = "ticketExpirationTime";
    public static final String KEY_TICKET_NUMBER_OF_USES = "ticketNumberOfUses";
    public static final String KEY_TICKET_MAX_NUMBER_OF_USES = "ticketMaxNumberOfUses";
    public static final String KEY_TICKET_OWNER_UUID = "ticketOwner";
    public static final String KEY_TICKET_OWNER_NAME = "ticketOwnerName";
    public static final String KEY_TICKET_ORIGIN_PRICE = "ticketOriginPrice";
    public static final String KEY_TRANSIT_PASS_TAGS = "ticketTags";

    private final Player owner;
    private int maxUses;


    public BCTicket(PlayerOption option, TrainRoutes.PathInfo pathInfo, Player owner) {
        this.owner = owner;
        this.pathInfo = pathInfo;
        this.maxUses = option.getUses();
        this.maxSpeed = option.getSpeed();
        this.commonItemStack = createItem();
        refreshTicketMeta(true);
    }

    public BCTicket(int maxUses, double maxSpeed, TrainRoutes.PathInfo pathInfo, Player owner) {
        this.owner = owner;
        this.pathInfo = pathInfo;
        this.maxUses = maxUses;
        this.maxSpeed = maxSpeed;
        this.commonItemStack = createItem();
        refreshTicketMeta(true);
    }

    /**
     * 引用itemstack
     */
    private BCTicket(ItemStack itemStack) {
        this.commonItemStack = CommonItemStack.of(itemStack);

        CommonTagCompound nbt = commonItemStack.getCustomData();
        this.owner = Bukkit.getPlayer(nbt.getValue(KEY_TICKET_OWNER_UUID, ""));
        this.maxUses = nbt.getValue(KEY_TICKET_MAX_NUMBER_OF_USES, 1);
        this.maxSpeed = nbt.getValue(KEY_TRANSIT_PASS_MAX_SPEED, 4.0);

        this.pathInfo = TrainRoutes.getPathInfo(
                nbt.getValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, ""),
                new ArrayList<>(List.of(nbt.getValue(BCTicket.KEY_TRANSIT_PASS_TAGS, "").split(","))),
                nbt.getValue(KEY_TRANSIT_PASS_END_STATION, "")
        );
        if (pathInfo == null) {
            this.update();
        }
        commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts"));
        if (pathInfo != null) {
            refreshTicketMeta(false);
        }
    }

    @Nullable
    public static BCTicket fromItemStack(ItemStack ticket) {
        if (!isBctsTicket(ticket)) {
            return null;
        }
        return new BCTicket(ticket);
    }

    @Nullable
    public static BCTicket fromHeldItem(Player player) {
        BCTicketDisplay display = MapDisplay.getHeldDisplay(player, BCTicketDisplay.class);
        if (display == null) {
            return null;
        }
        return new BCTicket(display.getMapItem());
    }

    public void purchase() {
        EconomyResponse r = plugin.getEcon().withdrawPlayer(owner, this.getPrice());
        String ticketName = getTicketName(true);

        if (r.transactionSuccess()) {
            owner.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-buy-success", "您成功花费 %.2f 购买了 %s").formatted(r.amount, ticketName)).decoration(TextDecoration.ITALIC, false)
            ));
            this.give();
            // 记录log
            Bukkit.getConsoleSender().sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("玩家 %s 成功花费 %.2f 购买了 %s".formatted(owner.getName(), r.amount, ticketName), NamedTextColor.GREEN)));
            // 写入数据库
            plugin.getTrainDatabaseManager().getTransitPassService().addTicketInfo(owner.getName(), owner.getUniqueId().toString(), r.amount, commonItemStack.getCustomData());
        } else {
            owner.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-buy-failure", "车票购买失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false)
            ));
        }
    }

    public void give() {
        commonItemStack.updateCustomData(this::updateNbt);
        ItemStack itemStack = commonItemStack.toBukkit();
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

    @Override
    public BCTicket getNewSingleTicket(Player player) {
        return new BCTicket(1, maxSpeed, pathInfo, player);
    }

    /**
     * 车票使用次数+1
     */
    public void useTicket() {
        useTransitPass(owner);
    }

    @Override
    public void useTransitPass(Player usedPlayer) {
        commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TICKET_NUMBER_OF_USES, tag.getValue(KEY_TICKET_NUMBER_OF_USES, 0) + 1));
        // 检查是否达到最大次数
        CommonTagCompound nbt = commonItemStack.getCustomData();
        if (maxUses > 0 && maxUses <= nbt.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0)) {
            commonItemStack.setAmount(commonItemStack.getAmount() - 1);
        }
        HumanHand.setItemInMainHand(usedPlayer, commonItemStack.toBukkit());

        usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                Utils.mmStr2Component(message.get("ticket-used", "成功使用一张（次）%s 车票").formatted(this.getTransitPassName()))
        ));

        plugin.getTrainDatabaseManager().getTransitPassService().addTransitPassUsage(
                usedPlayer.getUniqueId().toString(),
                usedPlayer.getName(),
                getPrice(),
                PassType.TICKET.getId(),
                nbt
        );
    }

    @Override
    public boolean verify(Player usedPlayer, MinecartGroup group) {
        // 其他玩家的车票
        if (!isTicketOwner(usedPlayer)) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-owner-conflict", "不能使用其他玩家的车票"))
            ));
            return false;
        }

        // 过期的车票
        if (isTicketExpired()) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-expired", "车票已过期"))
            ));
            return false;
        }

        // 旧版本车票
        if (commonItemStack.getCustomData().getValue(KEY_TRANSIT_PASS_PLUGIN, "").equals("TrainCarts")) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-old", "旧版车票已禁用"))
            ));
            return false;
        }

        Collection<String> trainTags = group.getProperties().getTags();
        Set<String> ticketTags = pathInfo.getTags();

        // 验证站台是否正确
        if (!BCTransitPass.verifyPlatform(commonItemStack.getCustomData().getValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, ""), trainTags)) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("ticket-wrong-platform", "此车票不能在本站台使用，请核对车票的可使用站台和本站台上的信息是否一致"))
            ));
            return false;
        }

        // 列车为初始车（不需要比对tag） 或 主手车票tag和列车tag一致，可以上车
        return BCTransitPass.isNewPRTrain(group) || ticketTags.size() == trainTags.size() && trainTags.containsAll(ticketTags);
    }

    @Override
    public void applyTo(Player usedPlayer, MinecartGroup group) {
        if (BCTransitPass.isNewPRTrain(group)) {
            // 新车需要应用配置
            super.applyTo(usedPlayer, group);
        }
    }

    /**
     * 刷新车票的Lore和物品名，计算价格
     */
    public void refreshTicketMeta(PlayerOption playerOption) {
        this.maxSpeed = playerOption.getSpeed();
        this.maxUses = playerOption.getUses();
        refreshTicketMeta(true);
    }

    public void refreshTicketMeta(boolean addPrice) {
        if (isTicketExpired()) {
            return;
        }
        // 更新lore
        ItemStack itemStack = commonItemStack.toBukkit();
        ItemMeta itemMeta = itemStack.getItemMeta();
        // pdc验证字段
        itemMeta.getPersistentDataContainer().set(KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN, true);
        Map<String, Object> placeholder = new HashMap<>();
        placeholder.put("maxUses", maxUses);
        placeholder.put("ownerName", owner.getName());
        List<Component> lore = parseConfigLore(MainConfig.ticketLore, placeholder);
        if (addPrice) {
            lore.add(Component.text("===============================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("售价：%.2f银币       左键点击购买".formatted(this.getPrice()), NamedTextColor.DARK_PURPLE));
        }
        itemMeta.lore(lore);
        itemMeta.displayName(Component.text(getTicketName(false), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        itemStack.setItemMeta(itemMeta);
    }

    private String getTicketName(boolean trim) {
        if (trim) {
            if (maxUses == 1) {
                return "%s → %s 单次票".formatted(pathInfo.getStartStation().getClearStationName(), pathInfo.getEndStation().getClearStationName());
            } else {
                return "%s → %s %d次票".formatted(pathInfo.getStartStation().getClearStationName(), pathInfo.getEndStation().getClearStationName(), maxUses);
            }
        } else {
            if (maxUses == 1) {
                return "%s → %s 单次票".formatted(pathInfo.getStartStation().getStationName(), pathInfo.getEndStation().getStationName());
            } else {
                return "%s → %s %d次票".formatted(pathInfo.getStartStation().getStationName(), pathInfo.getEndStation().getStationName(), maxUses);
            }
        }
    }

    /**
     * 创建新的车票item
     */
    private CommonItemStack createItem() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        mapItem.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
        mapItem.getItemMeta().displayName(Utils.mmStr2Component(MainConfig.cardConfig.get("name", "<gold>帕拉伦国有铁路交通卡")));
        return CommonItemStack.of(mapItem)
                .updateCustomData(this::updateNbt);
    }

    private void updateNbt(CommonTagCompound tag) {
        tag.putValue(KEY_TRANSIT_PASS_TYPE, PassType.TICKET.getId());
        tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts");
        tag.putValue(KEY_TICKET_NAME, MainConfig.expressTicketName);
        tag.putValue(KEY_TICKET_CREATION_TIME, System.currentTimeMillis());
        tag.putValue(KEY_TICKET_NUMBER_OF_USES, 0);
        tag.putValue(KEY_TICKET_MAX_NUMBER_OF_USES, maxUses);
        tag.putUUID(KEY_TICKET_OWNER_UUID, owner.getUniqueId());
        tag.putValue(KEY_TICKET_OWNER_NAME, owner.getName());
        tag.putValue(KEY_TRANSIT_PASS_MAX_SPEED, maxSpeed);
        tag.putValue(KEY_TICKET_ORIGIN_PRICE, this.pathInfo.getPrice());
        tag.putValue(KEY_TRANSIT_PASS_TAGS, String.join(",", this.pathInfo.getTags()));
        tag.putValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, pathInfo.getStartPlatformTag());
        tag.putValue(KEY_TRANSIT_PASS_START_STATION, pathInfo.getStartStation().getStationName());
        tag.putValue(KEY_TRANSIT_PASS_END_STATION, pathInfo.getEndStation().getStationName());
        tag.putValue(KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
    }

    @Override
    public double getPrice() {
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

    /**
     * 如果铁路有变化，更新车票信息
     */
    public void update() {
        CommonTagCompound nbt = commonItemStack.getCustomData();
        List<String> tags = List.of(nbt.getValue(KEY_TRANSIT_PASS_TAGS, "").split(","));
        String startStation = nbt.getValue(KEY_TRANSIT_PASS_START_STATION, String.class, TrainRoutes.graph.getStationNameFromTag(tags.get(0)));
        String endStation = nbt.getValue(KEY_TRANSIT_PASS_END_STATION, String.class, TrainRoutes.graph.getStationNameFromTag(tags.get(tags.size() - 1)));
        String startPlatformTag = nbt.getValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, String.class, "");
        List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(startStation, endStation);
        if (!pathInfoList.isEmpty()) {
            boolean updated = false;
            for (TrainRoutes.PathInfo path : pathInfoList) {
                // 新车票包含所有旧车票的tag 则认为是同一路线的车票
                if (path.getTags().containsAll(tags)) {
                    if (!startStation.equals(endStation) && !startPlatformTag.equals(path.getStartPlatformTag())) {
                        // 线路延长，xxx方向改变
                        commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, path.getStartPlatformTag()));
                        startPlatformTag = path.getStartPlatformTag();
                    }

                    if (path.getTags().size() != tags.size() && startPlatformTag.equals(path.getStartPlatformTag())) {
                        // 新增车站
                        commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_TAGS, String.join(",", path.getTags())));
                    }

                    commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts"));
                    commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_TYPE, PassType.TICKET.getId()));
                    this.pathInfo = path;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                // 保底
                this.pathInfo = pathInfoList.get(0);
                this.commonItemStack.updateCustomData(this::updateNbt);
            }
        } else {
            // 标记为过期车票
            commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TICKET_EXPIRATION_TIME, 0));
        }
    }

    public static boolean isBctsTicket(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        CommonTagCompound nbt = CommonItemStack.of(itemStack).getCustomData();
        return nbt != null && (nbt.getValue(KEY_TRANSIT_PASS_TYPE, "").equals(PassType.TICKET.getId()) || nbt.getValue(KEY_TICKET_NAME, "").equals(MainConfig.expressTicketName));
    }

    public boolean isTicketOwner(Player player) {
        CommonTagCompound nbt = commonItemStack.getCustomData();
        return !nbt.containsKey(KEY_TICKET_OWNER_UUID) || nbt.getUUID(KEY_TICKET_OWNER_UUID).equals(player.getUniqueId());
    }

    public boolean isTicketExpired() {
        if (commonItemStack == null) {
            return true;
        } else {
            CommonTagCompound nbt = commonItemStack.getCustomData();
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
