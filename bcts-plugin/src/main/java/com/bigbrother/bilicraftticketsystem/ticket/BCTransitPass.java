package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.addon.signactions.SignActionShowroute;
import com.bigbrother.bilicraftticketsystem.addon.signactions.component.RouteBossbar;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.menu.PlayerOption;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public abstract class BCTransitPass {
    public static final String KEY_TRANSIT_PASS_TYPE = "transitPassType";
    public static final String KEY_TRANSIT_PASS_PLUGIN = "plugin";
    public static final String KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH = "backgroundImagePath";


    protected ItemStack itemStack;
    protected TrainRoutes.PathInfo pathInfo;
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

        // 设置skip
        String[] split = MainConfig.skip.split(" ");
        if (split.length == 3) {
            trainProperties.setSkipOptions(SignSkipOptions.create(Integer.parseInt(split[1]), Integer.parseInt(split[2]), split[0]));
        }

        // 设置speed tag
        trainProperties.setSpeedLimit(maxSpeed);
        trainProperties.setTags(pathInfo.getTags().toArray(new String[0]));
        trainProperties.getHolder().onPropertiesChanged();

        // 所有车厢显示bossbar
        for (MinecartMember<?> minecartMember : group) {
            RouteBossbar bossbar = SignActionShowroute.bossbarMapping.getOrDefault(minecartMember, null);
            if ((bossbar == null || bossbar.getRouteId() != null)) {
                bossbar = new RouteBossbar(pathInfo.getStartStation().getStationName(), pathInfo.getEndStation().getStationName(), pathInfo.getTags().size());
                SignActionShowroute.bossbarMapping.put(minecartMember, bossbar);
            }
        }
    }

    /**
     * 获取路线详情lore
     *
     * @param cntPerRow 每行最多显示的车站数量，<=0不限制
     */
    public List<Component> getPathInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<MermaidGraph.Node> path = pathInfo.getPath();
        Component join = Component.text("");
        int cnt = 0;
        for (int i = 0; i < path.size(); i++) {
            MermaidGraph.Node node = path.get(i);
            if (!node.isStation()) {
                continue;
            }

            String stationName = node.getStationName();
            String railwayName = node.getRailwayName() + node.getRailwayDirection();

            if (i == path.size() - 1) {
                // 终到站
                join = join.append(Component.text(stationName, NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                break;
            }
            if (cnt == 0) {
                // 起始站
                join = join.append(Component.text(stationName, NamedTextColor.GOLD)
                        .append(Component.text("→", CommonUtils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, false));
            } else if (cntPerRow <= 0 || cnt % cntPerRow != 0) {
                // 经过站
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", CommonUtils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, true));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", CommonUtils.getRailwayColor(railwayName)))
                        .decoration(TextDecoration.ITALIC, true));
            }
            cnt += 1;
        }
        return lore;
    }

    /**
     * 获取途经铁路lore
     *
     * @param cntPerRow 每行最多显示的车站数量，<=0不限制
     */
    public List<Component> getRailwayInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<String> railways = new ArrayList<>();

        for (MermaidGraph.Node node : pathInfo.getPath()) {
            if (!node.isStation()) {
                continue;
            }

            String railwayName = node.getRailwayName() + node.getRailwayDirection();
            if (!railways.isEmpty() && !railways.get(railways.size() - 1).equals(railwayName)) {
                railways.add(railwayName);
            } else if (railways.isEmpty()) {
                railways.add(railwayName);
            }
        }

        Component stationsLore = Component.text("");
        for (int i = 0; i < railways.size(); i++) {
            if (i != 0 && cntPerRow > 0 && i % cntPerRow == 0) {
                lore.add(stationsLore);
                stationsLore = Component.text("");
            }
            if (i == railways.size() - 1) {
                stationsLore = stationsLore.append(Component.text(railways.get(i), CommonUtils.getRailwayColor(railways.get(i))));
            } else {
                stationsLore = stationsLore.append(Component.text(railways.get(i) + "→", CommonUtils.getRailwayColor(railways.get(i))));
            }
        }
        lore.add(stationsLore);

        return lore;
    }

    public List<Component> parseConfigLore(List<String> originLore, @NotNull Map<String, Object> initPlaceholders) {
        if (pathInfo != null) {
            MermaidGraph.Node start = pathInfo.getStartStation();
            MermaidGraph.Node end = pathInfo.getEndStation();

            initPlaceholders.putIfAbsent("startStationName", start.getStationName());
            initPlaceholders.putIfAbsent("startRailwayDirection", start.getRailwayDirection());
            initPlaceholders.putIfAbsent("startRailwayName", start.getRailwayName());

            initPlaceholders.putIfAbsent("endStationName", end.getStationName());
            initPlaceholders.putIfAbsent("endRailwayDirection", end.getRailwayDirection());
            initPlaceholders.putIfAbsent("endRailwayName", end.getRailwayName());

            initPlaceholders.putIfAbsent("PathInfoLore", getPathInfoLore(MainConfig.loreStationNameCntRow));
            initPlaceholders.putIfAbsent("RailwayInfoLore", getRailwayInfoLore(MainConfig.loreRailwayNameCntRow));

            initPlaceholders.putIfAbsent("distance", "%.2f".formatted(pathInfo.getDistance()));
            initPlaceholders.putIfAbsent("speed", "%.2f".formatted(this.getSpeedKph()));
            initPlaceholders.putIfAbsent("price", "%.2f".formatted(this.getPrice()));
        }
        return PlaceholderParser.parse(originLore, initPlaceholders);
    }

    public static boolean verifyPlatform(String pTag, Collection<String> trainTags) {
        if (pTag.equals(PlayerOption.NOT_AVALIABLE)) {
            return true;
        }

        MermaidGraph.Node expectStartNode = TrainRoutes.graph.getNodeFromPtag(pTag);
        if (expectStartNode != null) {
            MermaidGraph.Node trainSpawnNode = getTrainStartNode(trainTags);

            if (trainSpawnNode != null) {
                // 找到
                return expectStartNode.getTag().equals(trainSpawnNode.getTag()) &&
                        expectStartNode.getRailwayDirection().startsWith(trainSpawnNode.getRailwayDirection());
            }
        }
        return true;
    }

    public static @Nullable String getTrainStartPtag(Collection<String> trainTags) {
        MermaidGraph.Node trainSpawnNode = getTrainStartNode(trainTags);
        if (trainSpawnNode != null) {
            return trainSpawnNode.getPlatformTag();
        }
        return null;
    }

    public static @Nullable MermaidGraph.Node getTrainStartNode(Collection<String> trainTags) {
        for (String trainTag : trainTags) {
            MermaidGraph.Node trainSpawnNode = TrainRoutes.graph.getNodeFromPtag(trainTag);
            if (trainSpawnNode != null) {
                // 找到
                return trainSpawnNode;
            }
        }
        return null;
    }

    public static boolean isBCTransitPass(ItemStack itemStack) {
        return BCTicket.isBctsTicket(itemStack) || BCCard.isBctsCard(itemStack);
    }

    public static boolean isNewPRTrain(MinecartGroup group) {
        return group.getProperties().getTickets().contains(MainConfig.expressTicketName);
    }
}
