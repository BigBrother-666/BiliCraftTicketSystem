package com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeojsonManager;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeoUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.geojson.Feature;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Data
public class PRGeoWalkingPoint {
    /**
     * 带有方向位置信息的节点
     */
    @Getter
    public static class WalkingPointNode extends MermaidGraph.Node {
        private final Vector direction;
        private final Location location;

        public WalkingPointNode(MermaidGraph.Node node, Vector direction, Location location) {
            super(node.getStationName(), node.getRailwayName(), node.getRailwayDirection(), node.getTag());
            this.direction = direction.clone();
            this.location = location.clone();
        }
    }

    @Getter
    public enum LineType {
        MAIN_LINE_FIRST("main_line_first"),
        MAIN_LINE_SECOND("main_line_second"),
        LINE_IN("line_in"),
        LINE_OUT("line_out"),
        NONE("none");

        private final String type;

        LineType(String type) {
            this.type = type;
        }

        public static LineType fromType(String type) {
            for (LineType lt : values()) {
                if (lt.type.equalsIgnoreCase(type)) {
                    return lt;
                }
            }
            return NONE;
        }
    }

    public enum ErrorType {
        NONE,
        UNEXPECTED_SIGN,
        WALKING_POINT_ERROR
    }

    private final BiliCraftTicketSystem plugin;
    private final PRGeoTask geoTask;
    private GeojsonManager geojsonManager;
    // 初始化信息

    @Nullable
    @Setter
    private Block endRail;
    private TrackWalkingPoint trackWalkingPoint;
    private MinecartMember<?> member;


    // 统计信息
    private int coordCnt;
    private List<LngLatAlt> coodrinates;
    private LinkedHashSet<LngLatAlt> lineOutCoords;

    public PRGeoWalkingPoint(BiliCraftTicketSystem plugin, PRGeoTask geoTask) {
        this.plugin = plugin;
        this.geoTask = geoTask;
        this.geojsonManager = new GeojsonManager(plugin.getGeodataDir());
    }

    public Location getLastLocation() {
        return this.trackWalkingPoint.state.railBlock().getLocation();
    }

    public Vector getLastDirection() {
        return this.trackWalkingPoint.state.enterDirection();
    }

    /**
     * 销毁旧矿车，重新生成矿车到指定地点指定方向，并重置trackWalkingPoint
     *
     * @param startRail      位置
     * @param startDirection 方向
     */
    public void resetWalkingPoint(Block startRail, Vector startDirection) {
        if (this.member == null || this.member.isUnloaded()) {
            if (Bukkit.isPrimaryThread()) {
                initMember(startRail, startDirection);
            } else {
                Future<Boolean> syncMethod = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    initMember(startRail, startDirection);
                    return true;
                });
                try {
                    syncMethod.get();
                } catch (InterruptedException | ExecutionException e) {
                    geoTask.sendMessageAndLog(Component.text("初始化矿车失败！" + e, NamedTextColor.RED), true);
                }
            }
        }

        this.coodrinates = new ArrayList<>();
        this.coordCnt = 0;

        this.member.getProperties().clearTags();
        this.member.getProperties().getHolder().onPropertiesChanged();
        this.trackWalkingPoint = new TrackWalkingPoint(startRail.getLocation(), startDirection);
        this.trackWalkingPoint.setFollowPredictedPath(this.member);
        this.trackWalkingPoint.skipFirst();
    }

    private void initMember(Block startRail, Vector startDirection) {
        // 初始化矿车
        this.member = MinecartMemberStore.spawn(TrainCarts.plugin, startRail.getLocation(), EntityType.MINECART);
        this.member.getGroup().getProperties().setDefault();
        this.member.getGroup().getProperties().setKeepChunksLoaded(true);
        this.member.getGroup().getProperties().setSpeedLimit(0);
        this.member.setOrientation(Quaternion.fromLookDirection(startDirection));
        this.member.getGroup().getProperties().setTrainName("rail_geo_" + UUID.randomUUID());
    }

    /**
     * 把walkingpoint移动到指定的节点
     *
     * @param node 移动到的节点
     */
    public void resetWalkingPoint(WalkingPointNode node) {
        this.resetWalkingPoint(node.getLocation().getBlock(), node.getDirection());
    }

    /**
     * 清空记录的坐标
     */
    public void clearCoords() {
        this.coodrinates.clear();
        this.coordCnt = 0;
    }

    /**
     * 寻找下一个包含某tag的switcher控制牌
     *
     * @param currTag 当前tag，为null时表示起点
     * @param nextTag 目标tag
     * @return 错误信息，NONE=无错误，UNEXPECTED_SIGN=找到switcher之前遇到了bcspawn，WALKING_POINT_ERROR=有环路或铁轨断开
     */
    public ErrorType findNextSwitcher(String currTag, String nextTag) {
        do {
            RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
            if (signs == null) {
                continue;
            }
            for (RailLookup.TrackedSign sign : signs) {
                if (sign.getLine(1).trim().toLowerCase().startsWith("switcher")) {
                    // 获取switcher控制牌的所有tag
                    List<String> tags = new ArrayList<>();
                    for (int i = 2; i <= 3; i++) {
                        String line = sign.getLine(i);
                        tags.addAll(GeoUtils.parseSwitcherTags(line));
                    }
                    // 找到需要的控制牌
                    if (tags.contains(nextTag) || nextTag == null) {
                        geoTask.sendMessageAndLog(Component.text("检测到包含 %s 的道岔switcher控制牌".formatted(nextTag), NamedTextColor.GREEN));
                        return ErrorType.NONE;
                    }
                } else if (sign.getLine(1).trim().toLowerCase().startsWith("bcspawn")) {
                    // 非正常情况：找switcher途中发现bcspawn
                    if (sign.getLine(3).contains(nextTag)) {
                        return ErrorType.UNEXPECTED_SIGN;
                    } else if (currTag != null && sign.getLine(3).contains(currTag)) {
                        //noinspection UnnecessaryContinue
                        continue;
                    } else {
                        geoTask.sendMessageAndLog(Component.text("寻找 %s switcher途中检测到 %s bcspawn控制牌".formatted(nextTag, sign.getLine(3)), NamedTextColor.YELLOW));
                        return ErrorType.WALKING_POINT_ERROR;
                    }
                } else if (sign.getLine(1).trim().toLowerCase().startsWith("station") && coordCnt > 100) {
                    geoTask.sendMessageAndLog(Component.text("寻找 %s switcher途中检测到station控制牌（终点站）".formatted(nextTag), NamedTextColor.YELLOW));
                    return ErrorType.UNEXPECTED_SIGN;
                } else if (sign.getLine(2).trim().toLowerCase().startsWith("remtag") && sign.getLine(3).trim().equals(nextTag)) {
                    geoTask.sendMessageAndLog(Component.text("检测到未开通车站 %s".formatted(nextTag), NamedTextColor.GREEN));
                    return ErrorType.NONE;
                }
            }
        } while (!nextRail());
        return ErrorType.WALKING_POINT_ERROR;
    }

    /**
     * 寻找下一个移除某tag的remtag控制牌
     *
     * @param tag 移除的tag
     * @param retry 最大重试次数，找remtag过程中可能会遇到其他remtag
     * @return 错误信息，NONE=无错误，UNEXPECTED_SIGN=遇到的第一个remtag的tag不匹配，WALKING_POINT_ERROR=有环路或铁轨断开
     */
    public ErrorType findNextRemtag(String tag, int retry) {
        addTags(tag);
        do {
            RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
            if (signs == null) {
                continue;
            }
            for (RailLookup.TrackedSign sign : signs) {
                if (sign.getLine(2).trim().toLowerCase().startsWith("remtag")) {
                    if (sign.getLine(3).trim().equals(tag)) {
                        geoTask.sendMessageAndLog(Component.text("检测到remtag控制牌: %s".formatted(tag), NamedTextColor.GREEN));
                        return ErrorType.NONE;
                    } else {
                        geoTask.sendMessageAndLog(Component.text("检测到remtag控制牌tag不匹配（目标：%s, 实际：%s）".formatted(tag, sign.getLine(3).trim()), NamedTextColor.YELLOW));
                        retry -= 1;
                        if (retry <= 0) {
                            return ErrorType.UNEXPECTED_SIGN;
                        }
                    }
                }
            }
        } while (!nextRail());
        return ErrorType.WALKING_POINT_ERROR;
    }

    /**
     * 寻找下一个bcspawn控制牌
     *
     * @param platformTag 站台tag
     * @return 错误信息，NONE=无错误，UNEXPECTED_SIGN=遇到的第一个bcspawn的站台tag不匹配，WALKING_POINT_ERROR=有环路或铁轨断开
     */
    public ErrorType findNextBCSpawn(String platformTag) {
        do {
            RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
            if (signs == null) {
                continue;
            }
            for (RailLookup.TrackedSign sign : signs) {
                if (sign.getLine(1).trim().toLowerCase().startsWith("bcspawn")) {
                    if (MermaidGraph.Node.cmpPlatformTag(platformTag, sign.getLine(3).trim())) {
                        geoTask.sendMessageAndLog(Component.text("检测到bcspawn控制牌: %s".formatted(platformTag), NamedTextColor.GREEN));
                        return ErrorType.NONE;
                    } else {
                        geoTask.sendMessageAndLog(Component.text("检测到bcspawn控制牌站台tag不匹配（目标：%s, 实际：%s）".formatted(platformTag, sign.getLine(3).trim()), NamedTextColor.YELLOW));
                        return ErrorType.UNEXPECTED_SIGN;
                    }
                } else if (sign.getLine(1).trim().toLowerCase().startsWith("station")) {
                    // 没有正线的终到站 或 station先于bcspawn
                    geoTask.sendMessageAndLog(Component.text("在bcspawn前检测到station", NamedTextColor.GREEN));
                    return ErrorType.NONE;
                }
            }
        } while (!nextRail());
        return ErrorType.WALKING_POINT_ERROR;
    }

    /**
     * 从正线走到出站的道岔节点，必须在保存line_out后使用
     * 并简化line_out重叠的部分
     *
     * @return 固定返回成功
     */
    @SuppressWarnings("UnusedReturnValue")
    public ErrorType findOutJunction(String tag) {
        addTags(tag);
        if (lineOutCoords == null) {
            return ErrorType.NONE;
        }
        do {
            Block railBlock = trackWalkingPoint.state.railBlock();

            RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
            if (signs == null) {
                continue;
            }
            for (RailLookup.TrackedSign sign : signs) {
                if (sign.getLine(2).trim().toLowerCase().startsWith("remtag") && sign.getLine(3).trim().toLowerCase().startsWith(tag)) {
                    // 限制最大距离，正常不应该找到remtag
                    geoTask.sendMessageAndLog(Component.text("找到出站道岔前，发现 %s remtag".formatted(tag), NamedTextColor.YELLOW));
                    return ErrorType.NONE;
                }
            }

            LngLatAlt coord = new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY());

            if (lineOutCoords.contains(coord)) {
                // 简化line_out重叠的部分
                Feature lineOutFeature = null;
                for (Feature feature : geojsonManager.getCollection().getFeatures()) {
                    if (feature.getGeometry() instanceof LineString) {
                        LineType lineType = LineType.fromType((String) feature.getProperties().getOrDefault("line_type", "none"));
                        if (lineType.equals(LineType.LINE_OUT)) {
                            lineOutFeature = feature;
                        }
                    }
                }
                if (lineOutFeature != null) {
                    List<LngLatAlt> newLineOutCoords = new ArrayList<>();
                    for (LngLatAlt lineOutCoord : lineOutCoords) {
                        newLineOutCoords.add(lineOutCoord);
                        if (lineOutCoord.equals(coord)) {
                            break;
                        }
                    }

                    if (newLineOutCoords.size() < 2) {
                        // 终点站移除line_out
                        List<Feature> features = geojsonManager.getCollection().getFeatures();
                        features.remove(lineOutFeature);
                        geojsonManager.getCollection().setFeatures(features);
                    } else {
                        lineOutFeature.setGeometry(new LineString(newLineOutCoords.toArray(new LngLatAlt[0])));
                        lineOutFeature.setProperty("distance", newLineOutCoords.size());
                    }
                }
                lineOutCoords = null;
                break;
            }
        } while (!nextRail());
        return ErrorType.NONE;
    }

    /**
     * 遍历，直到找到设置的endRail
     */
    public void findEndRail() {
        if (endRail == null) {
            geoTask.sendMessageAndLog(Component.text("遍历铁轨结束！没有指定结束铁轨坐标", NamedTextColor.YELLOW), true);
            return;
        }

        //noinspection StatementWithEmptyBody
        while (!nextRail()) ;
        endRail = null;
    }

    /**
     * 向前进方向遍历1节铁轨
     *
     * @return false:没有到达设置的终点/结束，反之true
     */
    private boolean nextRail() {
        addCommonTag();
        remTag();
        Block railBlock = trackWalkingPoint.state.railBlock();
        this.coodrinates.add(new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY()));
        this.coordCnt += 1;
        if (BlockUtil.equals(railBlock, endRail)) {
            Component msg = Component.text("找到目标点 -> " + GeoUtils.formatLoc(endRail.getLocation()), NamedTextColor.DARK_AQUA);
            geoTask.sendMessageAndLog(msg);
            return true;
        } else if (!trackWalkingPoint.moveFull()) {
            Component msg = Component.text("遍历铁轨结束！原因：" + trackWalkingPoint.failReason.toString(), NamedTextColor.DARK_AQUA);
            geoTask.sendMessageAndLog(msg);
            return true;
        }
        return false;
    }

    /**
     * 添加当前铁轨的非快速车控制tag
     */
    private void addCommonTag() {
        RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
        if (signs == null) {
            return;
        }
        for (RailLookup.TrackedSign sign : signs) {
            if (sign.getHeader().isAlwaysOn() && sign.getLine(2).trim().toLowerCase().startsWith("addtag")) {
                String tag = sign.getLine(3).trim();
                if (!TrainRoutes.graph.nodeTagMap.containsKey(tag)) {
                    // 非快速车控制tag
                    this.member.getProperties().addTags(tag);
                    this.member.getProperties().getHolder().onPropertiesChanged();
                    this.trackWalkingPoint.setFollowPredictedPath(this.member);
                }
            }
        }
    }

    /**
     * 执行当前铁轨的remtag
     */
    private void remTag() {
        RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
        if (signs == null) {
            return;
        }
        for (RailLookup.TrackedSign sign : signs) {
            if (sign.getLine(2).trim().toLowerCase().startsWith("remtag")) {
                String tag = sign.getLine(3).trim();
                this.member.getProperties().removeTags(tag);
                this.member.getProperties().getHolder().onPropertiesChanged();
                this.trackWalkingPoint.setFollowPredictedPath(this.member);
            }
        }
    }

    public void addTags(String... tag) {
        this.member.getProperties().addTags(tag);
        this.member.getProperties().getHolder().onPropertiesChanged();
        this.trackWalkingPoint.setFollowPredictedPath(this.member);
    }

    public void addPoint2FeatureCollection(WalkingPointNode node) {
        Map<String, Object> props = new HashMap<>();
        props.put("platform_tag", node.getPlatformTag());
        props.put("tag", node.getTag());
        props.put("station_name", node.getStationName());
        props.put("railway_name", node.getRailwayName());
        props.put("railway_direction", node.getRailwayDirection());
        geojsonManager.addPoint(new LngLatAlt(node.location.getBlockX(), node.location.getBlockZ(), node.location.getBlockY()), props);
    }

    /**
     * 添加当前walkingpoint的坐标列表到FeatureCollection
     *
     * @param lineType 区间类型
     * @param start    区间开始节点
     * @param end      区间结束节点
     */
    public void addCoords2FeatureCollection(LineType lineType, MermaidGraph.Node start, @Nullable MermaidGraph.Node end) {
        Map<String, Object> props = new HashMap<>();
        props.put("line_type", lineType.getType());
        // wtf????
        if (start.getRailwayName().isEmpty()) {
            props.put("line_color", "#a9a9a9");
        } else {
            props.put("line_color", MainConfig.railwayColor.get(start.getRailwayName(), "#a9a9a9"));
        }

        props.put("distance", Math.max(coordCnt, 0));

        if (lineType.equals(LineType.LINE_OUT)) {
            // 暂存坐标
            lineOutCoords = new LinkedHashSet<>(coodrinates);
        }

        // 只有第二段正线需要end节点信息
        if (end != null && lineType.equals(LineType.MAIN_LINE_SECOND)) {
            props.put("end_platform_tag", end.getPlatformTag());
            props.put("end_tag", end.getTag());
            props.put("end_station_name", end.getStationName());
            props.put("end_railway_direction", end.getRailwayDirection());
            props.put("end_railway_name", end.getRailwayName());
        }

        // 添加最后一个点，防止线路断开
        if (coodrinates.size() > 1) {
            Block railBlock = trackWalkingPoint.state.railBlock();
            this.coodrinates.add(new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY()));
        } else {
            this.clearCoords();
        }
        geojsonManager.addLine(coodrinates, props);
        this.clearCoords();
    }

    /**
     * 保存为 GeoJSON 文件
     */
    public void saveGeojsonFile(String fileName) {
        // 保存之前优化折线坐标
        Feature node = null;
        List<String> childTags = new ArrayList<>();
        for (Feature feature : geojsonManager.getCollection().getFeatures()) {
            if (feature.getGeometry() instanceof LineString lineString) {
                feature.setGeometry(new LineString(GeoUtils.simplifyLineString(lineString.getCoordinates()).toArray(new LngLatAlt[0])));
                LineType lineType = LineType.fromType((String) feature.getProperties().getOrDefault("line_type", "none"));
                if (lineType.equals(LineType.MAIN_LINE_SECOND)) {
                    childTags.add((String) feature.getProperties().get("end_platform_tag"));
                }
            } else if (feature.getGeometry() instanceof Point) {
                node = feature;
            }
        }

        // 添加子结点属性，方便前端构建图结构
        if (node != null) {
            node.getProperties().put("child_platform_tags", childTags);
        }

        try {
            geojsonManager.saveGeojsonFile(fileName);
        } catch (IOException e) {
            geoTask.sendMessageAndLog(Component.text("保存geojson时失败: " + e.getMessage(), NamedTextColor.RED));
            return;
        }
        geoTask.sendMessageAndLog(Component.text("保存geojson: " + fileName, NamedTextColor.DARK_GREEN).decorate(TextDecoration.ITALIC));
    }

    /**
     * 销毁遍历用的矿车
     */
    public void destroyMember() {
        if (member == null || member.isUnloaded()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            member.getGroup().destroy();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> member.getGroup().destroy());
        }
    }
}

