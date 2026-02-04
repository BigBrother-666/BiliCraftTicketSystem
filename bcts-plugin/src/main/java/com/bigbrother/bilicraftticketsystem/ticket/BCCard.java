package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
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
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(CommonUtils.mmStr2Component(message.get("card-missing", "<red>主手没有检测到交通卡！"))));
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
     * 计算pathInfo并更新NBT
     */
    private void calcPathAndUpdateNbt() {
        if (this.cardInfo.isStationNotEmpty()) {
            String startStation = this.cardInfo.getStartStationString();
            String endStation = this.cardInfo.getEndStationString();
            this.pathInfo = TrainRoutes.findShortestPath(startStation, endStation);
        }
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
        this.cardInfo.setStartStation(startStation);
        refreshCard();
    }

    public void setEndStation(Component endStation) {
        this.pathInfo = null;
        this.cardInfo.setEndStation(endStation);
        refreshCard();
    }

    @Override
    public boolean verify(Player usedPlayer, MinecartGroup group) {
        if (!BCTransitPass.isNewPRTrain(group)) {
            // 已经是快速车，获取快速车对应的凭证
            BCTransitPass bcTransitPass = TrainListeners.trainTicketInfo.get(group);
            TrainRoutes.PathInfo trainPathInfo = bcTransitPass.getPathInfo();
            if (trainPathInfo != null) {
                this.pathInfo = trainPathInfo;
                this.maxSpeed = bcTransitPass.maxSpeed;
                return true;
            }
            // ???
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("unknown-error", "发生未知错误！"))
            ));
            return false;
        }


        Collection<String> trainTags = group.getProperties().getTags();

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
            // 只指定了终点
            // 获取当前pTag并计算路径
            MermaidGraph.Node startNode = BCTransitPass.getTrainStartNode(trainTags);
            if (startNode != null) {
                this.pathInfo = TrainRoutes.getShortestPathFromStartNode(startNode, cardInfo.getEndStationString());
            }

            if (pathInfo == null) {
                // 没找到路线
                sendNoPathMsg(usedPlayer);
                return false;
            }
        } else {
            // 要求指定终点
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("card-no-endstation", "请先指定想前往的终点站再上车！")).decoration(TextDecoration.ITALIC, false))
            );
            // 打开指定终点界面
            return false;
        }

        // 2. 验证
        // 余额充足？
        double price = getPrice();
        if (this.cardInfo.getBalance() < price) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("card-not-enough-balance", "<red>交通卡余额不足！本次行程需要 <yellow>%.2f 银币<red>，余额 <yellow>%.2f 银币").formatted(price, this.cardInfo.getBalance())))
            );
            return false;
        }
        // 验证站台是否正确
        if (pathInfo == null || !BCTransitPass.verifyPlatform(pathInfo.getStartPlatformTag(), trainTags)) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("card-wrong-platform", "不能本站台使用交通卡，请核对交通卡的可使用站台和本站台上的信息是否一致")))
            );
            return false;
        }

        // 使用交通卡不需要比对tag
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

        usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
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
                pathInfo.getStartStation().getStationName(),
                pathInfo.getStartPlatformTag(),
                pathInfo.getEndStation().getStationName(),
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
        placeholder.put("startStationName", cardInfo.getMmStartStationName());
        placeholder.put("endStationName", cardInfo.getMmEndStationName());
        if (cardInfo.isStartStationEmpty() && !cardInfo.isEndStationEmpty()) {
            placeholder.put("usePlatform", "<dark_green>任意国铁站台");
        } else if (cardInfo.isStationNotEmpty()) {
            if (pathInfo != null) {
                MermaidGraph.Node startNode = pathInfo.getStartStation();
                placeholder.put("usePlatform", "<dark_green>[%s<dark_green>] 标有 [<blue>%s %s</blue><dark_green>] 的站台".formatted(mmStartStationName, startNode.getRailwayName(), startNode.getRailwayDirection()));
            } else {
                placeholder.put("usePlatform", "<red>暂无 %s <red>到 %s <red>的直达车".formatted(mmStartStationName, mmEndStationName));
            }
        }

        List<Component> lore = parseConfigLore(MainConfig.cardLore, placeholder);

        itemStack.editMeta(itemMeta -> itemMeta.lore(lore));
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
        return cardConfig.get("base-fare", 0.0) + pathInfo.getPrice();
    }

    @Override
    public String getTransitPassName() {
        if (cardInfo.isStartStationEmpty() && !cardInfo.isEndStationEmpty()) {
            return "%s → %s".formatted("ANY", cardInfo.getClearEndStationName());
        }
        return "%s → %s".formatted(cardInfo.getClearStartStationName(), cardInfo.getClearEndStationName());
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
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
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
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("card-charge-success", "您成功向交通卡充值了 %.2f 银币，余额 %.2f 银币").formatted(r.amount, this.cardInfo.getBalance())).decoration(TextDecoration.ITALIC, false))
            );
            if (reachMax) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                        CommonUtils.mmStr2Component(message.get("card-reach-max", "交通卡存储金额已经达到最大值")).decoration(TextDecoration.ITALIC, false))
                );
            }
            // 记录log
            Bukkit.getConsoleSender().sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("玩家 %s 成功向交通卡充值了 %.2f ".formatted(player.getName(), r.amount), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    CommonUtils.mmStr2Component(message.get("card-charge-failure", "充值失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false))
            );
        }
        return false;
    }

    private void createItem() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        mapItem.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
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
        mapItem.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
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
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
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
        return nbt != null && nbt.getValue(KEY_TRANSIT_PASS_TYPE, "").equals(PassType.CARD.getId());
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
