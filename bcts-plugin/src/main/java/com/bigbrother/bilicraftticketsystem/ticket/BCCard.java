package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.cardConfig;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

@Getter
public class BCCard extends BCTransitPass {
    public static final String KEY_CARD_UUID = "cardUniqueID";
    public static final String KEY_CARD_INIT_FLAG = "cardInitFlag";

    private final BCCardInfo cardInfo;

    /**
     * 创建新的的交通卡
     */
    public BCCard() {
        this.maxSpeed = 4.0;
        createItem();
        this.itemStack.editMeta(itemMeta -> itemMeta.displayName(CommonUtils.mmStr2Component(MainConfig.cardConfig.get("name", "<gold>帕拉伦国有铁路交通卡"))));
        this.cardInfo = new BCCardInfo(CommonItemStack.of(itemStack).getCustomData().getValue(KEY_CARD_UUID, UUID.randomUUID().toString()));
        refreshLore();
        // pdc验证字段
        initPdc();
    }

    private BCCard(ItemStack card) {
        this.itemStack = card;
        this.cardInfo = BCCardInfo.load(CommonItemStack.of(itemStack).getCustomData().getValue(KEY_CARD_UUID, ""));
        if (cardInfo != null) {
            this.maxSpeed = this.cardInfo.getSpeed();
        } else {
            this.maxSpeed = 4.0;
        }
    }

    private BCCard(String uuid) {
        this.cardInfo = BCCardInfo.load(uuid);
        if (cardInfo != null) {
            createItemFromCardInfo();
            this.maxSpeed = this.cardInfo.getSpeed();
        } else {
            this.maxSpeed = 4.0;
        }
    }

    @Nullable
    public static BCCard fromItemStack(ItemStack card) {
        if (CommonItemStack.of(card).getCustomData().getValue(KEY_CARD_INIT_FLAG, false)) {
            BCTicketDisplay mapDisplay = getMapDisplay(card);
            BCCard bcCard = new BCCard();
            if (mapDisplay != null) {
                mapDisplay.setMapItem(bcCard.getItemStack());
                return bcCard;
            } else {
                return null;
            }
        }
        if (!isBctsCard(card)) {
            return null;
        }
        return new BCCard(card);
    }

    @Nullable
    public static BCCard fromHeldItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        BCCard card = fromItemStack(mainHand);
        if (card == null) {
            player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(message.get("card-missing", "<red>主手没有检测到交通卡！"))));
            return null;
        }
        return card;
    }

    @Nullable
    public static BCCard fromUuid(String uuid) {
        if (BCCardInfo.hasCard(uuid)) {
            return new BCCard(uuid);
        } else {
            return null;
        }
    }

    /**
     * 获取未开卡的交通卡
     */
    public static ItemStack getEmptyCard() {
        ItemStack item = CommonItemStack.of(MapDisplay.createMapItem(BCTicketDisplay.class))
                .updateCustomData(nbt -> {
                    nbt.putValue(KEY_CARD_INIT_FLAG, true);
                    nbt.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts");
                    nbt.putValue(KEY_TRANSIT_PASS_TYPE, PassType.CARD.getId());
                })
                .toBukkit();
        item.editMeta(itemMeta -> {
            itemMeta.displayName(CommonUtils.mmStr2Component(MainConfig.cardConfig.get("name", "<gold>帕拉伦国有铁路交通卡")));
            itemMeta.getPersistentDataContainer().set(GuardListeners.KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static @Nullable BCCard findCardFromInventory(Player player) {
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack != null && isBctsCard(itemStack)) {
                return fromItemStack(itemStack);
            }
        }
        return null;
    }

    /**
     * 计算pathInfo并更新NBT。
     * <p>
     * 指定了起终点时按站名寻路取最短；图未变即原路径。
     */
    private void calcPathAndUpdateNbt() {
        if (this.cardInfo.isStationNotEmpty()) {
            String startStation = this.cardInfo.getStartStationString();
            String endStation = this.cardInfo.getEndStationString();
            List<GeoRoutePath> paths = GeoRouteEngine.findByStation(startStation, endStation);
            this.pathInfo = paths.isEmpty() ? null : paths.getFirst();
        }
        // 按行程实际线路记录起/终点所属铁路系统名（无路径时清空，避免显示陈旧系统）
        this.cardInfo.setStartStationSystem(pathInfo == null ? CommonUtils.NOT_AVAILABLE : systemNameOf(pathInfo.getStartLineId()));
        this.cardInfo.setEndStationSystem(pathInfo == null ? CommonUtils.NOT_AVAILABLE : systemNameOf(pathInfo.getEndLineId()));
    }

    /**
     * 线路 id → 所属铁路系统名；无系统 / 线路无效时返回 {@link CommonUtils#NOT_AVAILABLE}。
     */
    private static String systemNameOf(String lineId) {
        RailwaySystemInfo system = RailwaySystemConfig.get(LineConfig.getSystemId(lineId));
        return system == null ? CommonUtils.NOT_AVAILABLE : system.getName();
    }

    public void addBalance(double balance) {
        this.cardInfo.addBalance(balance);
        refreshCard();
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        this.cardInfo.setSpeed(maxSpeed);
        refreshCard();
    }

    public void setStartStation(Component startStation) {
        this.pathInfo = null;
        this.cardInfo.setStartStationComponent(startStation);
        refreshCard();
    }

    public void setEndStation(Component endStation) {
        this.pathInfo = null;
        this.cardInfo.setEndStationComponent(endStation);
        refreshCard();
    }

    @Override
    public boolean verify(Player usedPlayer, MinecartGroup group) {
        if (!BCTransitPass.isNewPRTrain(group)) {
            // 已经是快速车，获取快速车对应的凭证
            BCTransitPass bcTransitPass = TrainListeners.trainTicketInfo.get(group);
            GeoRoutePath trainPathInfo = bcTransitPass == null ? null : bcTransitPass.getPathInfo();
            if (trainPathInfo != null) {
                this.pathInfo = trainPathInfo;
                this.maxSpeed = bcTransitPass.maxSpeed;
                // 余额充足？
                double price = getPrice();
                if (this.cardInfo.getBalance() < price) {
                    usedPlayer.sendMessage(MainConfig.prefix.append(
                            CommonUtils.mmStr2Component(message.get("card-not-enough-balance", "<red>交通卡余额不足！本次行程需要 <yellow>%.2f 银币<red>，余额 <yellow>%.2f 银币").formatted(price, this.cardInfo.getBalance())))
                    );
                    return false;
                }
                return true;
            }
            // ???
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("unknown-error", "发生未知错误！"))
            ));
            return false;
        }


        // 1. 计算Path
        if (cardInfo.isStationNotEmpty()) {
            // 指定了起点和终点
            // 路线在选择车站后就应该计算完成，这种情况不应该发生
            if (pathInfo == null) {
                // 再次尝试计算
                calcPathAndUpdateNbt();
            }
            if (pathInfo == null) {
                sendNoPathMsg(usedPlayer);
                return false;
            }
        } else if (!cardInfo.isEndStationEmpty()) {
            // 只指定了终点：起点站台在上车这一刻才固定——以列车当前所在线路的站台为起点
            // 用 起点站名 + 列车 lineId 唯一定位站台节点。
            String startStation = BCTransitPass.getTrainStartStationName(group);
            String lineId = BCTransitPass.getTrainLineId(group);
            if (!startStation.isEmpty()) {
                this.pathInfo = GeoRouteEngine.findFromStationNode(startStation, lineId, cardInfo.getEndStationString());
            }

            if (pathInfo == null) {
                // 没找到路线
                sendNoPathMsg(usedPlayer);
                return false;
            }
        } else {
            // 要求指定终点
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("card-no-endstation", "请先指定想前往的终点站再上车！")).decoration(TextDecoration.ITALIC, false))
            );
            // 打开指定终点界面
            return false;
        }

        // 2. 验证
        // 余额充足？
        double price = getPrice();
        if (this.cardInfo.getBalance() < price) {
            usedPlayer.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("card-not-enough-balance", "<red>交通卡余额不足！本次行程需要 <yellow>%.2f 银币<red>，余额 <yellow>%.2f 银币").formatted(price, this.cardInfo.getBalance())))
            );
            return false;
        }
        // 验证站台是否正确：指定了起点时，列车起点节点须与所选路径起点一致
        if (pathInfo == null) {
            sendNoPathMsg(usedPlayer);
            return false;
        }
        if (cardInfo.isStationNotEmpty()) {
            String trainStation = BCTransitPass.getTrainStartStationName(group);
            String trainLineId = BCTransitPass.getTrainLineId(group);
            String pathLineId = pathInfo.getStartLineId();
            if (!trainStation.equals(pathInfo.getStartStationName())
                    || !trainLineId.equals(pathLineId == null ? "" : pathLineId)) {
                usedPlayer.sendMessage(MainConfig.prefix.append(
                        CommonUtils.mmStr2Component(message.get("card-wrong-platform", "不能本站台使用交通卡，请核对交通卡的可使用站台和本站台上的信息是否一致")))
                );
                return false;
            }
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

    @Override
    public void useTransitPass(Player usedPlayer) {
        double price = getPrice();
        // 扣费
        this.addBalance(-price);

        usedPlayer.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("card-used", "刷卡成功，扣费 %.2f 银币，剩余 %.2f 银币").formatted(price, this.cardInfo.getBalance()))
        ));

        // 聊天栏展示路线详情
        usedPlayer.sendMessage(Component.text("=============================", NamedTextColor.DARK_AQUA));
        usedPlayer.sendMessage(Component.text("本次列车路线：", NamedTextColor.DARK_AQUA));
        for (Component component : this.getPathInfoLore(-1)) {
            usedPlayer.sendMessage(component);
        }
        usedPlayer.sendMessage(Component.text("%.2fkm | %.2fkm/h".formatted(pathInfo.getDistance(), getSpeedKph()), NamedTextColor.DARK_AQUA));
        usedPlayer.sendMessage(Component.text("=============================", NamedTextColor.DARK_AQUA));

        // 记录
        plugin.getTrainDatabaseManager().getTransitPassService().addCardUsage(
                usedPlayer.getUniqueId().toString(),
                usedPlayer.getName(),
                pathInfo.getStartStationName(),
                pathInfo.getStartNode().getId(),
                pathInfo.getEndStationName(),
                maxSpeed,
                price,
                PassType.CARD.getId(),
                cardInfo.getCardUuid()
        );
    }

    @Override
    public BCTicket getNewSingleTicket(Player usedPlayer) {
        return new BCTicket(1, maxSpeed, pathInfo, usedPlayer);
    }

    /**
     * 刷新交通卡
     */
    public void refreshCard() {
        calcPathAndUpdateNbt();
        refreshDisplay();
        refreshLore();
    }

    private void refreshLore() {
        // 展示当前的设置
        Map<String, Object> placeholder = new HashMap<>();
        String mmStartStationName = cardInfo.getMmStartStationName();
        String mmEndStationName = cardInfo.getMmEndStationName();
        placeholder.put("balance", "%.2f".formatted(this.cardInfo.getBalance()));
        placeholder.put("start_station_name", cardInfo.getMmStartStationName());
        placeholder.put("end_station_name", cardInfo.getMmEndStationName());
        // 合成占位符：带色站名 +（系统名）。站点为空只显示 N/A（不拼成 N/A(N/A)）；
        // 有直达路径时按行程实际线路上色与取系统，否则只显示站名本身。
        placeholder.put("start_station_full", stationFull(cardInfo.getMmStartStationName(),
                cardInfo.isStartStationEmpty(), pathInfo == null ? null : pathInfo.getStartLineId(), cardInfo.getStartStationSystem()));
        placeholder.put("end_station_full", stationFull(cardInfo.getMmEndStationName(),
                cardInfo.isEndStationEmpty(), pathInfo == null ? null : pathInfo.getEndLineId(), cardInfo.getEndStationSystem()));
        if (cardInfo.isStartStationEmpty() && !cardInfo.isEndStationEmpty()) {
            placeholder.put("use_platform", "<dark_green>任意国铁站台");
        } else if (cardInfo.isStationNotEmpty()) {
            if (pathInfo != null) {
                // 起点站 + 起点驶出段所属线路名（替代旧的 railwayName/direction）
                String startLineId = pathInfo.getStartLineId();
                RailwaySystemInfo startRailwaySystemInfo = RailwaySystemConfig.get(LineConfig.getSystemId(startLineId));
                placeholder.put("use_platform", "<gray>%s %s%s站<dark_green> 标有 %s%s <dark_green>的站台".formatted(
                        startRailwaySystemInfo != null ? startRailwaySystemInfo.getName() + "的" : "",
                        lineMiniMessageColor(startLineId),
                        mmStartStationName,
                        lineMiniMessageColor(startLineId),
                        lineName(startLineId)
                ));
            } else {
                placeholder.put("use_platform", "<red>暂无 %s <red>到 %s <red>的直达车".formatted(mmStartStationName, mmEndStationName));
            }
        }

        List<Component> lore = parseConfigLore(MainConfig.cardLore, placeholder);

        itemStack.editMeta(itemMeta -> itemMeta.lore(lore));
    }

    /**
     * 组合「车站名 +（所属铁路系统名）」展示串（MiniMessage）：{@code <线路标志色>车站名<gray>(系统名)}。
     * <p>
     * 站点未选时只返回 {@link CommonUtils#NOT_AVAILABLE_MM}（不拼成 N/A(N/A)）；
     * 有直达路径则按行程实际线路 {@code lineId} 上色；系统名取已记录的 {@code systemName}（来自行程实际线路，
     * 无系统时为 {@link CommonUtils#NOT_AVAILABLE}，此时省略括号）。
     *
     * @param mmStationName 车站名（MiniMessage）
     * @param empty         该站是否未选
     * @param lineId        行程在该端实际所属线路 id（决定颜色，可能为 null）
     * @param systemName    已记录的所属系统名
     * @return 组合展示串
     */
    private static String stationFull(String mmStationName, boolean empty, String lineId, String systemName) {
        if (empty) {
            return CommonUtils.NOT_AVAILABLE_MM;
        }
        if (systemName == null || CommonUtils.NOT_AVAILABLE.equals(systemName)) {
            return "%s%s".formatted(lineMiniMessageColor(lineId), mmStationName);
        }
        return "%s%s<gray>(%s)".formatted(lineMiniMessageColor(lineId), mmStationName, systemName);
    }

    private void refreshDisplay() {
        BCTicketDisplay mapDisplay = getMapDisplay(this.itemStack);
        if (mapDisplay != null) {
            mapDisplay.renderTicket();
        }
    }

    /**
     * 获取本次行程的价格
     */
    @Override
    public double getPrice() {
        return cardConfig.get("base-fare", 0.0) + calculateFare(pathInfo.getDistance());
    }

    @Override
    public String getTransitPassName() {
        if (cardInfo.isStartStationEmpty() && !cardInfo.isEndStationEmpty()) {
            return "%s → %s".formatted("ANY", cardInfo.getEndStationString());
        }
        return "%s → %s".formatted(cardInfo.getStartStationString(), cardInfo.getEndStationString());
    }

    /**
     * 充值
     *
     * @param chargeNum 充值金额
     * @param player    充值玩家
     * @return 是否需要继续输入
     */
    public boolean charge(double chargeNum, Player player) {
        double maxCharge = MainConfig.cardConfig.get("max-signle-charge", 1000);

        if (chargeNum > maxCharge) {
            player.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("card-charge-reach-max", "单次最多充值 %.2f 银币！请重新输入或点击取消按钮。").formatted(maxCharge)).decoration(TextDecoration.ITALIC, false))
            );
            return true;
        }

        double maxBalance = cardConfig.get("max-balance", Double.MAX_VALUE);
        boolean reachMax = false;
        if (this.cardInfo.getBalance() + chargeNum > cardConfig.get("max-balance", Double.MAX_VALUE)) {
            chargeNum = maxBalance - this.cardInfo.getBalance();
            reachMax = true;
        }

        EconomyResponse r = plugin.getEcon().withdrawPlayer(player, chargeNum);

        if (r.transactionSuccess()) {
            addBalance(r.amount);
            player.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("card-charge-success", "您成功向交通卡充值了 %.2f 银币，余额 %.2f 银币").formatted(r.amount, this.cardInfo.getBalance())).decoration(TextDecoration.ITALIC, false))
            );
            if (reachMax) {
                player.sendMessage(MainConfig.prefix.append(
                        CommonUtils.mmStr2Component(message.get("card-reach-max", "交通卡存储金额已经达到最大值")).decoration(TextDecoration.ITALIC, false))
                );
            }
            // 记录log
            Bukkit.getConsoleSender().sendMessage(MainConfig.prefix.append(Component.text("玩家 %s 成功向交通卡充值了 %.2f ".formatted(player.getName(), r.amount), NamedTextColor.GREEN)));
            // 记录充值收入
            plugin.getTrainDatabaseManager().getRevenueService().recordCardCharge(
                    player.getUniqueId().toString(), player.getName(), r.amount, this.cardInfo.getCardUuid());
        } else {
            player.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(message.get("card-charge-failure", "充值失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false))
            );
        }
        return false;
    }

    private void createItem() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        //noinspection deprecation
        mapItem.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        this.itemStack = CommonItemStack.of(mapItem)
                .updateCustomData(tag -> {
                    tag.putValue(KEY_TRANSIT_PASS_TYPE, PassType.CARD.getId());
                    tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts");
                    tag.putValue(KEY_CARD_INIT_FLAG, false);
                    tag.putValue(KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);

                    if (!tag.containsKey(KEY_CARD_UUID)) {
                        tag.putValue(KEY_CARD_UUID, UUID.randomUUID().toString());
                    }
                }).toBukkit();
    }

    private void createItemFromCardInfo() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        //noinspection deprecation
        mapItem.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        this.itemStack = CommonItemStack.of(mapItem)
                .updateCustomData(tag -> {
                    tag.putValue(KEY_TRANSIT_PASS_TYPE, PassType.CARD.getId());
                    tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts");
                    tag.putValue(KEY_CARD_INIT_FLAG, false);
                    tag.putValue(KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
                    tag.putValue(KEY_CARD_UUID, cardInfo.getCardUuid());
                })
                .toBukkit();
    }

    private void sendNoPathMsg(Player player) {
        player.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(message.get("card-no-path", "本站台没有 <i><u>%s</i> 直达 <i><u>%s</i> 的路线").formatted(cardInfo.getStartStationString(), cardInfo.getEndStationString())).decoration(TextDecoration.ITALIC, false))
        );
    }

    public static boolean isBctsCard(ItemStack itemStack) {
        return isBctsCard(CommonItemStack.of(itemStack));
    }

    public static boolean isBctsCard(CommonItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        CommonTagCompound nbt = itemStack.getCustomData();
        return nbt != null && nbt.getValue(KEY_TRANSIT_PASS_TYPE, "").equals(PassType.CARD.getId()) && BCCardInfo.hasCard(nbt.getValue(KEY_CARD_UUID, ""));
    }

    public static BCTicketDisplay getMapDisplay(ItemStack itemStack) {
        for (MapDisplay display : MapDisplay.getAllDisplays(itemStack)) {
            if (display instanceof BCTicketDisplay d) {
                return d;
            }
        }
        return null;
    }
}
