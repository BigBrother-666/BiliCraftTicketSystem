package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import lombok.Getter;
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
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.cardConfig;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

@Getter
public class BCCard extends BCTransitPass {
    public static final String KEY_CARD_BALANCE = "cardBalance";
    public static final String KEY_CARD_START_STATION_COMPONENT = "startStationComponent";
    public static final String KEY_CARD_END_STATION_COMPONENT = "endStationComponent";
    public static final String KEY_CARD_UUID = "cardUniqueID";

    private double balance;
    private final PlayerOption stationInfo;

    /**
     * 创建0余额的交通卡
     */
    public BCCard() {
        this.commonItemStack = createItem();
        this.balance = 0.0;
        this.maxSpeed = 4.0;
        this.stationInfo = new PlayerOption();
        refreshCard(true);
        // pdc验证字段
        commonItemStack.toBukkit().getItemMeta().getPersistentDataContainer().set(KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN, true);
    }

    private BCCard(ItemStack card) {
        this.commonItemStack = CommonItemStack.of(card);
        this.balance = this.commonItemStack.getCustomData().getValue(KEY_CARD_BALANCE, 0.0);
        this.maxSpeed = this.commonItemStack.getCustomData().getValue(KEY_TRANSIT_PASS_MAX_SPEED, 4.0);
        CommonTagCompound nbt = this.commonItemStack.getCustomData();
        this.stationInfo = new PlayerOption(
                nbt.getValue(KEY_CARD_START_STATION_COMPONENT, PlayerOption.EMPTY_STATION_COMPONENT),
                nbt.getValue(KEY_CARD_END_STATION_COMPONENT, PlayerOption.EMPTY_STATION_COMPONENT)
        );

        refreshCard(true);
    }

    @Nullable
    public static BCCard fromItemStack(ItemStack card) {
        if (!isBctsCard(card)) {
            return null;
        }
        return new BCCard(card);
    }

    @Nullable
    public static BCCard fromHeldItem(Player player) {
        BCTicketDisplay display = MapDisplay.getHeldDisplay(player, BCTicketDisplay.class);
        if (display == null || !isBctsCard(display.getMapItem())) {
            return null;
        }
        display.update();
        return new BCCard(display.getMapItem());
    }

    public static @Nullable BCCard findCardFromInventory(Player player) {
        for (ItemStack itemStack : player.getInventory()) {
            if (isBctsCard(itemStack)) {
                return fromItemStack(itemStack);
            }
        }
        return null;
    }

    public void setCommonItemStack(CommonItemStack itemStack) {
        this.commonItemStack = itemStack;
    }

    /**
     * 计算pathInfo并更新NBT
     */
    private void calcPathAndUpdateNbt() {
        if (this.stationInfo.isStationNotEmpty()) {
            String startStation = this.stationInfo.getStartStationString();
            String endStation = this.stationInfo.getEndStationString();
            this.pathInfo = TrainRoutes.findShortestPath(startStation, endStation);
        }
        this.commonItemStack.updateCustomData(this::updateNbt);
    }

    public void setBalance(double balance) {
        this.balance = balance;
        this.commonItemStack.updateCustomData(tag -> tag.putValue(KEY_CARD_BALANCE, this.balance));
    }

    public void addBalance(double balance) {
        this.balance += balance;
        this.commonItemStack.updateCustomData(tag -> tag.putValue(KEY_CARD_BALANCE, this.balance));
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        this.commonItemStack.updateCustomData(tag -> tag.putValue(KEY_TRANSIT_PASS_MAX_SPEED, this.maxSpeed));
    }

    public void setStartStation(Component startStation) {
        this.stationInfo.setStartStation(startStation);
        this.commonItemStack.updateCustomData((tag) -> {
            tag.putValue(KEY_TRANSIT_PASS_START_STATION, this.stationInfo.getStartStationString());
            tag.putValue(KEY_CARD_START_STATION_COMPONENT, this.stationInfo.getMmStartStationName());
        });
    }

    public void setEndStation(Component endStation) {
        this.stationInfo.setEndStation(endStation);
        this.commonItemStack.updateCustomData((tag) -> {
            tag.putValue(KEY_TRANSIT_PASS_END_STATION, this.stationInfo.getEndStationString());
            tag.putValue(KEY_CARD_END_STATION_COMPONENT, this.stationInfo.getMmEndStationName());
        });
    }

    public UUID getCardUuid() {
        if (!commonItemStack.getCustomData().containsKey(KEY_CARD_UUID)) {
            commonItemStack.updateCustomData(tag -> tag.putValue(KEY_CARD_UUID, UUID.randomUUID().toString()));
        }
        return UUID.fromString(commonItemStack.getCustomData().getValue(KEY_CARD_UUID, UUID.randomUUID().toString()));
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
                    Utils.mmStr2Component(message.get("unknown-error", "发生未知错误！"))
            ));
            return false;
        }


        Collection<String> trainTags = group.getProperties().getTags();

        // 1. 计算Path
        if (stationInfo.isStationNotEmpty()) {
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
        } else if (!stationInfo.isEndStationEmpty()) {
            // 只指定了终点
            // 获取当前pTag并计算路径
            MermaidGraph.Node startNode = BCTransitPass.getTrainStartNode(trainTags);
            if (startNode != null) {
                this.pathInfo = TrainRoutes.getShortestPathFromStartNode(startNode, stationInfo.getEndStationString());
            }

            if (pathInfo == null) {
                // 没找到路线
                sendNoPathMsg(usedPlayer);
                return false;
            }
        } else {
            // 要求指定终点
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("card-no-endstation", "请先指定想前往的终点站再上车！")).decoration(TextDecoration.ITALIC, false))
            );
            // 打开指定终点界面
            return false;
        }

        // 2. 验证
        // 余额充足？
        double price = getPrice();
        if (this.balance < price) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("card-not-enough-balance", "<red>交通卡余额不足！本次行程需要 <yellow>%.2f 银币<red>，余额 <yellow>%.2f 银币").formatted(price, this.balance)))
            );
            return false;
        }
        // 验证站台是否正确
        if (!BCTransitPass.verifyPlatform(commonItemStack.getCustomData().getValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, ""), trainTags)) {
            usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("card-wrong-platform", "不能本站台使用交通卡，请核对交通卡的可使用站台和本站台上的信息是否一致")))
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
        this.refreshCard(false);

        usedPlayer.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                Utils.mmStr2Component(message.get("card-used", "刷卡成功，扣费 %.2f 银币，剩余 %.2f 银币").formatted(price, this.balance))
        ));

        // 聊天栏展示路线详情
        usedPlayer.sendMessage(Component.text("=============================", NamedTextColor.DARK_AQUA));
        usedPlayer.sendMessage(Component.text("本次列车路线：", NamedTextColor.DARK_AQUA));
        for (Component component : this.getPathInfoLore()) {
            usedPlayer.sendMessage(component);
        }
        usedPlayer.sendMessage(Component.text("%.2fkm | %.2fkm/h".formatted(pathInfo.getDistance(), getSpeedKph()), NamedTextColor.DARK_AQUA));
        usedPlayer.sendMessage(Component.text("=============================", NamedTextColor.DARK_AQUA));

        // 记录
        plugin.getTrainDatabaseManager().getTransitPassService().addTransitPassUsage(
                usedPlayer.getUniqueId().toString(),
                usedPlayer.getName(),
                pathInfo.getStartStation().getStationName(),
                pathInfo.getStartPlatformTag(),
                pathInfo.getEndStation().getStationName(),
                maxSpeed,
                price,
                PassType.CARD.getId()
        );
    }

    @Override
    public BCTicket getNewSingleTicket(Player usedPlayer) {
        return new BCTicket(1, maxSpeed, pathInfo, usedPlayer);
    }

    /**
     * 刷新交通卡lore
     */
    public void refreshCard(boolean calcPath) {
        if (calcPath) {
            calcPathAndUpdateNbt();
        }

        ItemStack itemStack = commonItemStack.toBukkit();
        ItemMeta itemMeta = itemStack.getItemMeta();

        // 展示当前的设置
        Map<String, Object> placeholder = new HashMap<>();
        String mmStartStationName = stationInfo.getMmStartStationName();
        String mmEndStationName = stationInfo.getMmEndStationName();
        placeholder.put("balance", "%.2f".formatted(balance));
        placeholder.put("startStationName", stationInfo.isStartStationEmpty() ? null : mmStartStationName);
        placeholder.put("endStationName", stationInfo.isEndStationEmpty() ? null : mmEndStationName);
        if (stationInfo.isStartStationEmpty() && !stationInfo.isEndStationEmpty()) {
            placeholder.put("usePlatform", "<dark_green>任意国铁站台");
            if (calcPath) {
                pathInfo = null;
            }
        } else if (stationInfo.isStationNotEmpty()) {
            if (pathInfo != null) {
                MermaidGraph.Node startNode = pathInfo.getStartStation();
                placeholder.put("usePlatform", "<dark_green>[%s<dark_green>] 标有 [<blue>%s %s</blue><dark_green>] 的站台".formatted(mmStartStationName, startNode.getRailwayName(), startNode.getRailwayDirection()));
            } else {
                placeholder.put("usePlatform", "<red>暂无 %s <red>到 %s <red>的直达车".formatted(mmStartStationName, mmEndStationName));
            }
        } else {
            if (calcPath) {
                pathInfo = null;
            }
        }

        List<Component> lore = parseConfigLore(MainConfig.cardLore, placeholder);

        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
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
        if (stationInfo.isStartStationEmpty() && !stationInfo.isEndStationEmpty()) {
            return "%s → %s".formatted("ANY", stationInfo.getClearEndStationName());
        }
        return "%s → %s".formatted(stationInfo.getClearStartStationName(), stationInfo.getClearEndStationName());
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
                    Utils.mmStr2Component(message.get("card-charge-reach-max", "单次最多充值 %.2f 银币！请重新输入或点击取消按钮。").formatted(maxCharge)).decoration(TextDecoration.ITALIC, false))
            );
            return true;
        }

        double maxBalance = cardConfig.get("max-balance", Double.MAX_VALUE);
        boolean reachMax = false;
        if (this.balance + chargeNum > cardConfig.get("max-balance", Double.MAX_VALUE)) {
            chargeNum = maxBalance - this.balance;
            reachMax = true;
        }

        EconomyResponse r = plugin.getEcon().withdrawPlayer(player, chargeNum);

        if (r.transactionSuccess()) {
            addBalance(r.amount);
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("card-charge-success", "您成功向交通卡充值了 %.2f 银币，余额 %.2f 银币").formatted(r.amount, balance)).decoration(TextDecoration.ITALIC, false))
            );
            if (reachMax) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                        Utils.mmStr2Component(message.get("card-reach-max", "交通卡存储金额已经达到最大值")).decoration(TextDecoration.ITALIC, false))
                );
            }
            // 记录log
            Bukkit.getConsoleSender().sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("玩家 %s 成功向交通卡充值了 %.2f ".formatted(player.getName(), r.amount), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Utils.mmStr2Component(message.get("card-charge-failure", "充值失败：%s").formatted(r.errorMessage)).decoration(TextDecoration.ITALIC, false))
            );
        }
        return false;
    }

    private CommonItemStack createItem() {
        ItemStack mapItem = MapDisplay.createMapItem(BCTicketDisplay.class);
        mapItem.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);

        return CommonItemStack.of(mapItem)
                .updateCustomData(this::updateNbt)
                .setCustomNameMessage(MainConfig.expressTicketName);
    }

    private void updateNbt(CommonTagCompound tag) {
        tag.putValue(KEY_TRANSIT_PASS_TYPE, PassType.CARD.getId());
        tag.putValue(KEY_TRANSIT_PASS_PLUGIN, "bcts");
        tag.putValue(KEY_CARD_BALANCE, balance);
        tag.putValue(KEY_TRANSIT_PASS_MAX_SPEED, maxSpeed);
        if (!tag.containsKey(KEY_CARD_UUID)) {
            tag.putValue(KEY_CARD_UUID, UUID.randomUUID().toString());
        }

        if (pathInfo != null) {
            tag.putValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, pathInfo.getStartPlatformTag());
            tag.putValue(KEY_TRANSIT_PASS_START_STATION, pathInfo.getStartStation().getStationName());
            tag.putValue(KEY_TRANSIT_PASS_END_STATION, pathInfo.getEndStation().getStationName());
            tag.putValue(KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH, MainConfig.expressTicketBgimage);
        } else {
            tag.putValue(KEY_TRANSIT_PASS_START_PLATFORM_TAG, PlayerOption.EMPTY_STATION);
        }
    }

    private void sendNoPathMsg(Player player) {
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                Utils.mmStr2Component(message.get("card-no-path", "本站台没有 <i><u>%s</i> 直达 <i><u>%s</i> 的路线").formatted(stationInfo.getStartStationString(), stationInfo.getEndStationString())).decoration(TextDecoration.ITALIC, false))
        );
    }

    public static boolean isBctsCard(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        CommonTagCompound nbt = CommonItemStack.of(itemStack).getCustomData();
        return nbt != null && nbt.getValue(KEY_TRANSIT_PASS_TYPE, "").equals(PassType.CARD.getId());
    }
}
