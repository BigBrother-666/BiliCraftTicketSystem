package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
    public static final String KEY_TICKET_START_STATION = "startStation";
    public static final String KEY_TICKET_END_STATION = "endStation";
    public static final String KEY_TICKET_START_LINE_ID = "startLineId";
    public static final String KEY_TICKET_MAX_SPEED = "ticketMaxSpeed";
    public static final String KEY_TICKET_DISTANCE = "ticketDistance";

    private final Player owner;
    private int maxUses;


    public BCTicket(PlayerOption option, GeoRoutePath pathInfo, Player owner) {
        this.owner = owner;
        this.payerUuid = owner == null ? null : owner.getUniqueId();
        this.pathInfo = pathInfo;
        this.maxUses = option.getUses();
        this.maxSpeed = option.getSpeed();
        this.itemStack = createItem();
        refreshTicketMeta(true);
    }

    public BCTicket(int maxUses, double maxSpeed, GeoRoutePath pathInfo, Player owner) {
        this.owner = owner;
        this.payerUuid = owner == null ? null : owner.getUniqueId();
        this.pathInfo = pathInfo;
        this.maxUses = maxUses;
        this.maxSpeed = maxSpeed;
        this.itemStack = createItem();
        refreshTicketMeta(true);
    }

    /**
     * 引用itemstack
     */
    private BCTicket(ItemStack itemStack) {
        this.itemStack = itemStack;
        CommonItemStack commonItemStack = CommonItemStack.of(itemStack);

        CommonTagCompound nbt = commonItemStack.getCustomData();
        this.owner = Bukkit.getPlayer(nbt.getValue(KEY_TICKET_OWNER_UUID, ""));
        this.payerUuid = nbt.containsKey(KEY_TICKET_OWNER_UUID) ? nbt.getUUID(KEY_TICKET_OWNER_UUID) : null;
        this.maxUses = nbt.getValue(KEY_TICKET_MAX_NUMBER_OF_USES, 1);
        this.maxSpeed = nbt.getValue(KEY_TICKET_MAX_SPEED, 4.0);

        // 新模型：NBT 只存起点站名 / 终点站名 / 购买时距离，上车时按最新图重新寻路，
        // 在多条候选里挑距离与购买时最接近的一条。
        String startStation = nbt.getValue(KEY_TICKET_START_STATION, "");
        String endStation = nbt.getValue(KEY_TICKET_END_STATION, "");
        double distance = nbt.getValue(KEY_TICKET_DISTANCE, -1.0);
        if (startStation.isEmpty() || endStation.isEmpty() || distance < 0) {
            // 旧格式 NBT 车票（无新字段）直接作废
            this.pathInfo = null;
            commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TICKET_EXPIRATION_TIME, 0));
            return;
        }
        this.pathInfo = GeoRouteEngine.findClosestByDistance(startStation, endStation, distance);
        if (pathInfo == null) {
            // 找不到路线，标记为过期
            commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TICKET_EXPIRATION_TIME, 0));
        } else {
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
        EconomyResponse r = purchaseSilently();
        String ticketName = getTicketName();

        if (r.transactionSuccess()) {
            owner.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-buy-success", "您成功花费 %.2f 购买了 %s").formatted(r.amount, ticketName)).decoration(TextDecoration.ITALIC, false)
            ));
            // 记录log
            Bukkit.getConsoleSender().sendMessage(MainConfig.prefix.append(Component.text("玩家 %s 成功花费 %.2f 购买了 %s".formatted(owner.getName(), r.amount, ticketName), NamedTextColor.GREEN)));
        } else {
            owner.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-buy-failure", "车票购买失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false)
            ));
        }
    }

    /**
     * 购票核心（无玩家聊天提示），供游戏内 {@link #purchase()} 与网页在线购票共用：
     * Vault 扣款 → 成功则交付实体票({@link #give()}) + 按段所属系统分摊收入 + 写收入流水库。
     * <p>
     * 须在主线程调用（涉及 Vault / 背包 / 数据库）。调用方据返回的 {@link EconomyResponse} 决定提示 /
     * 回执（如余额不足 {@code !transactionSuccess()}）。
     *
     * @return Vault 扣款响应（含成功与否、实付金额、错误信息）
     */
    public EconomyResponse purchaseSilently() {
        EconomyResponse r = plugin.getEcon().withdrawPlayer(owner, this.getPrice());
        if (r.transactionSuccess()) {
            this.give();
            // 按段所属铁路系统分摊实付金额，实时累加各系统收入（车票在购买时一次性结算全部次数）
            RailwaySystemConfig.addIncome(allocateIncome(r.amount, 0.0));
            // 写入数据库
            plugin.getTrainDatabaseManager().getRevenueService().recordTicketPurchase(owner.getName(), owner.getUniqueId().toString(), r.amount, CommonItemStack.of(itemStack).getCustomData());
        }
        return r;
    }

    public void give() {
        CommonItemStack.of(itemStack).updateCustomData(this::updateNbt);
        List<Component> lore = itemStack.lore();
        if (lore != null && lore.size() > 2) {
            lore.removeLast();
            lore.removeLast();
        }
        ItemStack newTicket = itemStack.clone();
        newTicket.editMeta(itemMeta -> itemMeta.lore(lore));
        if (!owner.getInventory().addItem(newTicket).isEmpty()) {
            // 背包满 车票丢到地上
            owner.getWorld().dropItemNaturally(owner.getLocation(), newTicket);
        }
    }

    @Override
    public BCTicket getNewSingleTicket(Player player) {
        return new BCTicket(1, maxSpeed, pathInfo, player);
    }

    @Override
    public void useTransitPass(Player usedPlayer) {
        CommonItemStack commonItemStack = CommonItemStack.of(itemStack);

        commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TICKET_NUMBER_OF_USES, tag.getValue(KEY_TICKET_NUMBER_OF_USES, 0) + 1));
        // 检查是否达到最大次数
        CommonTagCompound nbt = commonItemStack.getCustomData();
        if (maxUses > 0 && maxUses <= nbt.getValue(BCTicket.KEY_TICKET_NUMBER_OF_USES, 0)) {
            commonItemStack.setAmount(commonItemStack.getAmount() - 1);
        }
        HumanHand.setItemInMainHand(usedPlayer, commonItemStack.toBukkit());

        usedPlayer.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("ticket-used", "成功使用一张（次）%s 车票").formatted(this.getTransitPassName()))
        ));

        // 单次行程实付价：购票总价（含次数倍数与折扣）按次均摊，再按段所属系统分摊写入明细。
        // 各次记录之和与购票时一次性计入各系统的收入一致（账目闭合）。
        double perTripPaid = maxUses > 0 ? getPrice() / maxUses : getPrice();
        plugin.getTrainDatabaseManager().getTransitPassService().addTicketUsage(
                usedPlayer.getUniqueId().toString(),
                usedPlayer.getName(),
                toPriceJson(allocateIncome(perTripPaid, 0.0), rawSegmentDistances()),
                PassType.TICKET.getId(),
                nbt,
                pathInfo.getStartNode().getId()
        );
    }

    @Override
    public boolean verify(Player usedPlayer, MinecartGroup group) {
        CommonItemStack commonItemStack = CommonItemStack.of(itemStack);

        // 其他玩家的车票
        if (!isTicketOwner(usedPlayer)) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-owner-conflict", "不能使用其他玩家的车票"))
            ));
            return false;
        }

        // 过期 / 失效的车票
        if (isTicketExpired() || pathInfo == null) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-expired", "车票已过期"))
            ));
            return false;
        }

        // 旧版本车票
        if (commonItemStack.getCustomData().getValue(KEY_TRANSIT_PASS_PLUGIN, "").equals("TrainCarts")) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-old", "旧版车票已禁用"))
            ));
            return false;
        }

        // 验证站台是否正确：列车的起点站名 + 所属营运线 lineId 须与车票路径的起点站名 + 营运线一致。
        // （发车牌与站台牌位于不同铁轨方块，无法用节点 id 对齐，故改用 站名 + lineId 比对。）
        String trainStation = BCTransitPass.getTrainStartStationName(group);
        String trainLineId = BCTransitPass.getTrainLineId(group);
        String ticketLineId = pathInfo.getStartLineId();
        if (!trainStation.equals(pathInfo.getStartStationName())
                || !trainLineId.equals(ticketLineId == null ? "" : ticketLineId)) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-wrong-platform", "此车票不能在本站台使用，请核对车票的可使用站台和本站台上的信息是否一致"))
            ));
            return false;
        }

        // 列车为初始车（首位上车者，尚未成型）不需比对路线，直接放行
        if (BCTransitPass.isNewPRTrain(group)) {
            return true;
        }
        // 已成型的直达车：要求本车票重算出的路线与列车正在行驶的导航路线<b>完全相同</b>才能上车
        // （逐节点比对 BcRouteProperty，而非仅起终点相同）
        if (!BcRouteNavigator.routeEquals(group, pathInfo.routeSteps())) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("ticket-route-mismatch",
                            "此车票的路线与本趟列车不一致，无法乘坐"))
            ));
            return false;
        }
        return true;
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
        initPdc();

        if (isTicketExpired()) {
            return;
        }
        // 更新lore
        Map<String, Object> placeholder = new HashMap<>();
        placeholder.put("max_uses", maxUses);
        placeholder.put("owner_name", owner.getName());
        List<Component> lore = parseConfigLore(MainConfig.ticketLore, placeholder);
        if (addPrice) {
            placeholder.put("distance_info_lore", getPriceInfoLore());
            lore.addAll(parseConfigLore(MainConfig.ticketPriceLore, placeholder));

        }
        itemStack.editMeta(itemMeta -> {
            itemMeta.lore(lore);
            itemMeta.displayName(Component.text(getTicketName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        });
    }

    public String getTicketName() {
        // 新模型站名已是干净站名，trim 参数保留兼容调用方
        String start = pathInfo.getStartStationName();
        String end = pathInfo.getEndStationName();
        if (maxUses == 1) {
            return "%s → %s 单次票".formatted(start, end);
        } else {
            return "%s → %s %d次票".formatted(start, end, maxUses);
        }
    }

    /**
     * 创建新的车票item
     */
    private ItemStack createItem() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        //noinspection deprecation
        mapItem.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return CommonItemStack.of(mapItem)
                .updateCustomData(this::updateNbt).toBukkit();
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
        tag.putValue(KEY_TICKET_MAX_SPEED, maxSpeed);
        tag.putValue(KEY_TICKET_ORIGIN_PRICE, calculateFare(pathInfo.getDistance()));
        // 新模型：只存起点站名 / 终点站名 / 购买时距离，上车按最新图重新寻路选最接近的一条
        tag.putValue(KEY_TICKET_START_STATION, pathInfo.getStartStationName());
        tag.putValue(KEY_TICKET_END_STATION, pathInfo.getEndStationName());
        tag.putValue(KEY_TICKET_DISTANCE, pathInfo.getDistance());
        // 起点所属营运线 id：上车时与列车 lineId 比对（同站可能有多条线路始发，据此区分）
        tag.putValue(KEY_TICKET_START_LINE_ID, pathInfo.getStartLineId() == null ? "" : pathInfo.getStartLineId());
        tag.putValue(KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
    }

    @Override
    public double getPrice() {
        double totalPrice = calculateFare(pathInfo.getDistance()) * maxUses;

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

    @Override
    public double getRideHistoryFare() {
        if (maxUses <= 0) {
            return getPrice();
        }
        return Math.round((getPrice() / maxUses) * 100.0) / 100.0;
    }

    @Override
    public String getTransitPassName() {
        if (pathInfo != null) {
            return "%s → %s".formatted(pathInfo.getStartStationName(), pathInfo.getEndStationName());
        } else {
            CommonTagCompound nbt = CommonItemStack.of(itemStack).getCustomData();
            return "%s → %s".formatted(
                    nbt.getValue(KEY_TICKET_START_STATION, "Unknown"),
                    nbt.getValue(KEY_TICKET_END_STATION, "Unknown")
            );
        }
    }

    public static boolean isBctsTicket(ItemStack itemStack) {
        return isBctsTicket(CommonItemStack.of(itemStack));
    }

    public static boolean isBctsTicket(CommonItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        CommonTagCompound nbt = itemStack.getCustomData();
        return nbt != null && (nbt.getValue(KEY_TRANSIT_PASS_TYPE, "").equals(PassType.TICKET.getId()) || nbt.getValue(KEY_TICKET_NAME, "").equals(MainConfig.expressTicketName));
    }

    public boolean isTicketOwner(Player player) {
        CommonTagCompound nbt = CommonItemStack.of(itemStack).getCustomData();
        return !nbt.containsKey(KEY_TICKET_OWNER_UUID) || nbt.getUUID(KEY_TICKET_OWNER_UUID).equals(player.getUniqueId());
    }

    public boolean isTicketExpired() {
        CommonItemStack commonItemStack = CommonItemStack.of(itemStack);
        if (itemStack == null) {
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
