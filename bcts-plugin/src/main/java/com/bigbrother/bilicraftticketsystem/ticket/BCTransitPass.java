package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcStartNodeProperty;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BossbarManager;
import com.bigbrother.bilicraftticketsystem.signactions.component.ExpressRouteBossbar;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbarBase;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.pricePerKm;

@Getter
public abstract class BCTransitPass {
    public static final String KEY_TRANSIT_PASS_TYPE = "transitPassType";
    public static final String KEY_TRANSIT_PASS_PLUGIN = "plugin";
    public static final String KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH = "backgroundImagePath";


    protected ItemStack itemStack;
    /**
     * 本次行程的路径（geojson 路由引擎产出）。
     */
    protected GeoRoutePath pathInfo;
    protected double maxSpeed;

    /**
     * 验证坐车凭证是否可以对这趟列车使用
     *
     * @param usedPlayer 使用坐车凭证的玩家
     * @param group      将要应用坐车凭证的列车
     * @return 坐车凭证是否可以对这趟列车使用
     */
    public abstract boolean verify(Player usedPlayer, MinecartGroup group);

    /**
     * 使用车票，对车票/玩家的属性进行更改
     *
     * @param usedPlayer 使用的玩家
     */
    public abstract void useTransitPass(Player usedPlayer);

    /**
     * 获取坐车凭证对应的单次票，用于快速购买
     *
     * @param usedPlayer 单次票应用的玩家
     * @return 单次票
     */
    public abstract BCTicket getNewSingleTicket(Player usedPlayer);

    /**
     * 获取玩家实际需要支付的金额
     *
     * @return 实际金额
     */
    public abstract double getPrice();

    protected void initPdc() {
        ItemMeta itemMeta = this.itemStack.getItemMeta();
        if (itemMeta != null && !itemMeta.getPersistentDataContainer().has(GuardListeners.KEY_TRANSIT_PASS)) {
            itemStack.editMeta(meta -> meta.getPersistentDataContainer().set(GuardListeners.KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN, true));
        }
    }

    // 返回 起始站 → 终到站 字符串
    public abstract String getTransitPassName();

    public double getSpeedKph() {
        return CommonUtils.mpt2Kph(maxSpeed);
    }

    /**
     * 给矿车设置属性，初始化bossbar
     */
    public void applyTo(Player usedPlayer, MinecartGroup group) {
        TrainProperties trainProperties = group.getProperties();
        trainProperties.clearTickets();

        // 设置speed
        trainProperties.setSpeedLimit(maxSpeed);
        // 写入导航节点步骤序列（bcswitcher 据当前步骤 lineId 选向，platform 仅推进进度）
        BcRouteNavigator.setRoute(group, pathInfo.routeSteps());
        trainProperties.getHolder().onPropertiesChanged();

        // 所有车厢显示直达车bossbar
        for (MinecartMember<?> minecartMember : group) {
            RouteBossbarBase bossbar = BossbarManager.get(minecartMember);
            if (!(bossbar instanceof ExpressRouteBossbar)) {
                ExpressRouteBossbar express = new ExpressRouteBossbar(
                        pathInfo.getStartStationName(), pathInfo.getEndStationName());
                BossbarManager.put(minecartMember, express);
                express.refreshProgress(0, pathInfo.routeSteps().size());
            }
        }
    }

    /**
     * 获取路线详情lore（只显示车站节点，箭头按该段所属 lineId 上色）。
     *
     * @param cntPerRow 每行最多显示的车站数量，<=0不限制
     */
    public List<Component> getPathInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<GeoRoutePath.StationStep> steps = pathInfo.stationSteps();
        Component join = Component.text("");
        int cnt = 0;
        for (int i = 0; i < steps.size(); i++) {
            GeoRoutePath.StationStep step = steps.get(i);
            String stationName = step.stationName();

            if (i == steps.size() - 1) {
                // 终到站
                join = join.append(Component.text(stationName, lineColor(step.departLineId()))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                break;
            }
            if (cnt == 0) {
                // 起始站
                join = join.append(Component.text(stationName, lineColor(step.departLineId()))
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false));
            } else if (cntPerRow <= 0 || cnt % cntPerRow != 0) {
                // 经过站
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, true));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, true));
            }
            cnt += 1;
        }
        return lore;
    }

    /**
     * 获取途经线路lore（按经过顺序列出沿途 lineId 对应的线路名，相邻去重）。
     *
     * @param cntPerRow 每行最多显示的线路数量，<=0不限制
     */
    public List<Component> getRailwayInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<String> lineIds = new ArrayList<>();

        for (String lineId : pathInfo.getLineIdSequence()) {
            if (lineId == null || lineId.isEmpty() || LineInfo.isSpecialId(lineId)) {
                continue;
            }
            // 相邻去重
            if (lineIds.isEmpty() || !lineIds.get(lineIds.size() - 1).equals(lineId)) {
                lineIds.add(lineId);
            }
        }

        Component stationsLore = Component.text("");
        for (int i = 0; i < lineIds.size(); i++) {
            if (i != 0 && cntPerRow > 0 && i % cntPerRow == 0) {
                lore.add(stationsLore);
                stationsLore = Component.text("");
            }
            String lineName = lineName(lineIds.get(i));
            TextColor color = lineColor(lineIds.get(i));
            if (i == lineIds.size() - 1) {
                stationsLore = stationsLore.append(Component.text(lineName, color));
            } else {
                stationsLore = stationsLore.append(Component.text(lineName + "→", color));
            }
        }
        lore.add(stationsLore);

        return lore;
    }

    /**
     * 线路 id → 显示色（取 {@link LineConfig#getColor}，无效时回退金色）。
     */
    protected static TextColor lineColor(String lineId) {
        if (lineId == null) {
            return NamedTextColor.GOLD;
        }
        TextColor color = TextColor.fromHexString(LineConfig.getColor(lineId));
        return color == null ? NamedTextColor.GOLD : color;
    }

    /**
     * 线路 id → 线路名（取 {@link LineConfig} 的 lineName，缺省回退 lineId 本身）。
     */
    protected static String lineName(String lineId) {
        LineInfo info = LineConfig.get(lineId);
        return info == null || info.getLineName() == null ? lineId : info.getLineName();
    }

    public List<Component> parseConfigLore(List<String> originLore, @NotNull Map<String, Object> initPlaceholders) {
        if (pathInfo != null) {
            initPlaceholders.putIfAbsent("startStationName", pathInfo.getStartStationName());
            initPlaceholders.putIfAbsent("startRailwayName", lineName(pathInfo.getStartLineId()));
            initPlaceholders.putIfAbsent("endStationName", pathInfo.getEndStationName());

            initPlaceholders.putIfAbsent("PathInfoLore", getPathInfoLore(MainConfig.loreStationNameCntRow));
            initPlaceholders.putIfAbsent("RailwayInfoLore", getRailwayInfoLore(MainConfig.loreRailwayNameCntRow));

            initPlaceholders.putIfAbsent("distance", "%.2f".formatted(pathInfo.getDistance()));
            initPlaceholders.putIfAbsent("speed", "%.2f".formatted(this.getSpeedKph()));
            initPlaceholders.putIfAbsent("price", "%.2f".formatted(this.getPrice()));
        }
        return PlaceholderParser.parse(originLore, initPlaceholders);
    }

    public static boolean isBCTransitPass(ItemStack itemStack) {
        return BCTicket.isBctsTicket(itemStack) || BCCard.isBctsCard(itemStack);
    }

    public static boolean isNewPRTrain(MinecartGroup group) {
        return group.getProperties().getTickets().contains(MainConfig.expressTicketName);
    }

    /**
     * 列车上车起点车站名（新模型：bcspawn 发车时记录到列车属性，替代旧的「读 tag 解析站台」）。
     *
     * @param group 列车
     * @return 起点车站名；未记录返回空串
     */
    public static String getTrainStartStationName(MinecartGroup group) {
        return BcStartNodeProperty.read(group);
    }

    /**
     * 列车所属营运线路 id：取列车 tag 中第一个在 routes.yml 中存在且非特殊（非 default / contact）的 lineId。
     * <p>
     * bcspawn 发车时把线路 id 作为 tag 加到列车上（{@code addTags(lineId)}），故据此识别列车所属线路。
     *
     * @param group 列车
     * @return 营运线路 id；找不到返回空串
     */
    public static String getTrainLineId(MinecartGroup group) {
        if (group == null) {
            return "";
        }
        for (String tag : group.getProperties().getTags()) {
            LineInfo info = LineConfig.get(tag);
            if (info != null && !info.isSpecial()) {
                return tag;
            }
        }
        return "";
    }

    // 计算票价
    protected double calculateFare(double distance) {
        return Math.round(distance * pricePerKm * 100.0) / 100.0;
    }
}
