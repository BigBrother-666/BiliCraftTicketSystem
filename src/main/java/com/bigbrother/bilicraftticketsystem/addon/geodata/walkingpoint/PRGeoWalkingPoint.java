package com.bigbrother.bilicraftticketsystem.addon.geodata.walkingpoint;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.geojson.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static com.bigbrother.bilicraftticketsystem.addon.geodata.Utils.getPrimaryColor;

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
    private final Logger logger;

    // 初始化信息
    @Nullable
    private final Block endRail;
    private TrackWalkingPoint trackWalkingPoint;
    private final Player sender;
    private MinecartMember<?> member;

    // 统计信息
    private int coordCnt;
    private List<LngLatAlt> coodrinates;
    private final ObjectMapper mapper;
    private FeatureCollection collection;
    private LinkedHashSet<LngLatAlt> lineOutCoords;

    public PRGeoWalkingPoint(@Nullable Block endRail, Block startRail, Vector startDirection, Player sender, BiliCraftTicketSystem plugin) {
        this.endRail = endRail;
        this.sender = sender;
        this.plugin = plugin;
        this.mapper = new ObjectMapper();
        this.logger = plugin.getGeoTaskLogger();

        resetFeatureCollection();
        initMember(startRail, startDirection);
        resetWalkingPoint(startRail, startDirection);
    }

    public PRGeoWalkingPoint(Block startRail, Vector startDirection, Player sender, BiliCraftTicketSystem plugin) {
        this(null, startRail, startDirection, sender, plugin);
    }

    public PRGeoWalkingPoint(Player sender, BiliCraftTicketSystem plugin) {
        this(sender.getLocation().getBlock(), sender.getLocation().getDirection(), sender, plugin);
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
                        tags.addAll(Utils.parseSwitcherTags(line));
                    }
                    // 找到需要的控制牌
                    if (tags.contains(nextTag) || nextTag == null) {
                        sendMessageAndLog(Component.text("检测到包含 %s 的道岔switcher控制牌".formatted(nextTag), NamedTextColor.GREEN));
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
                        sendMessageAndLog(Component.text("寻找 %s switcher途中检测到 %s bcspawn控制牌".formatted(nextTag, sign.getLine(3)), NamedTextColor.YELLOW));
                        return ErrorType.WALKING_POINT_ERROR;
                    }
                } else if (sign.getLine(1).trim().toLowerCase().startsWith("station") && coordCnt > 100) {
                    sendMessageAndLog(Component.text("寻找 %s switcher途中检测到station控制牌（终点站）".formatted(nextTag), NamedTextColor.YELLOW));
                    return ErrorType.UNEXPECTED_SIGN;
                } else if (sign.getLine(2).trim().toLowerCase().startsWith("remtag") && sign.getLine(3).trim().equals(nextTag)) {
                    sendMessageAndLog(Component.text("检测到未开通车站 %s".formatted(nextTag), NamedTextColor.GREEN));
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
     * @return 错误信息，NONE=无错误，UNEXPECTED_SIGN=遇到的第一个remtag的tag不匹配，WALKING_POINT_ERROR=有环路或铁轨断开
     */
    public ErrorType findNextRemtag(String tag) {
        addTags(tag);
        do {
            RailLookup.TrackedSign[] signs = trackWalkingPoint.state.railPiece().signs();
            if (signs == null) {
                continue;
            }
            for (RailLookup.TrackedSign sign : signs) {
                if (sign.getLine(2).trim().toLowerCase().startsWith("remtag")) {
                    if (sign.getLine(3).trim().equals(tag)) {
                        sendMessageAndLog(Component.text("检测到remtag控制牌: %s".formatted(tag), NamedTextColor.GREEN));
                        return ErrorType.NONE;
                    } else {
                        sendMessageAndLog(Component.text("检测到remtag控制牌tag不匹配（目标：%s, 实际：%s）".formatted(tag, sign.getLine(3).trim()), NamedTextColor.YELLOW));
                        nextRail();
                        return ErrorType.UNEXPECTED_SIGN;
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
                    if (sign.getLine(3).trim().equals(platformTag)) {
                        sendMessageAndLog(Component.text("检测到bcspawn控制牌: %s".formatted(platformTag), NamedTextColor.GREEN));
                        return ErrorType.NONE;
                    } else {
                        sendMessageAndLog(Component.text("检测到bcspawn控制牌站台tag不匹配（目标：%s, 实际：%s）".formatted(platformTag, sign.getLine(3).trim()), NamedTextColor.YELLOW));
                        return ErrorType.UNEXPECTED_SIGN;
                    }
                } else if (sign.getLine(1).trim().toLowerCase().startsWith("station")) {
                    // 没有正线的终到站
                    sendMessageAndLog(Component.text("检测到终到站的station", NamedTextColor.GREEN));
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
    public ErrorType findOutJunction(String tag) {
        addTags(tag);
        if (lineOutCoords == null) {
            return ErrorType.NONE;
        }
        do {
            Block railBlock = trackWalkingPoint.state.railBlock();

            LngLatAlt coord = new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY());

            if (lineOutCoords.contains(coord)) {
                // 简化line_out重叠的部分
                Feature lineOutFeature = null;
                for (Feature feature : collection.getFeatures()) {
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
                        List<Feature> features = collection.getFeatures();
                        features.remove(lineOutFeature);
                        collection.setFeatures(features);
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
            Component msg = Component.text("==== 遍历铁轨结束 ====", NamedTextColor.GREEN);
            sendMessageAndLog(msg);
            return true;
        } else if (!trackWalkingPoint.moveFull()) {
            Component msg = Component.text("遍历铁轨结束！原因：" + trackWalkingPoint.failReason.toString(), NamedTextColor.YELLOW);
            sendMessageAndLog(msg);
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
            if (sign.getLine(2).trim().toLowerCase().startsWith("addtag")) {
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

    /**
     * 增加一条线（LineString）
     */
    public void addLine(Map<String, Object> props) {
        // 添加最后一个点，防止线路断开
        if (coodrinates.size() > 1) {
            Block railBlock = trackWalkingPoint.state.railBlock();
            this.coodrinates.add(new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY()));
        } else {
            this.clearCoords();
        }

        LineString line = new LineString(coodrinates.toArray(new LngLatAlt[0]));
        Feature feature = new Feature();
        feature.setGeometry(line);
        if (props != null) {
            feature.setProperties(props);
        }
        collection.getFeatures().add(feature);
    }

    /**
     * 向FeatureCollection增加一个点
     *
     * @param coord 点坐标
     * @param props 点的自定义properties
     */
    public void addPoint2FeatureCollection(LngLatAlt coord, Map<String, Object> props) {
        Point point = new Point(coord);
        Feature feature = new Feature();
        feature.setGeometry(point);
        if (props != null) {
            feature.setProperties(props);
        }
        collection.getFeatures().add(feature);
    }

    public void addPoint2FeatureCollection(WalkingPointNode node) {
        Map<String, Object> props = new HashMap<>();
        props.put("tag", node.getTag());
        props.put("station_name", node.getStationName());
        props.put("railway_name", node.getRailwayName());
        props.put("railway_direction", node.getRailwayDirection());
        this.addPoint2FeatureCollection(new LngLatAlt(node.location.getBlockX(), node.location.getBlockZ(), node.location.getBlockY()), props);
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
        props.put("line_color", MainConfig.railwayColor.get(start.getRailwayName(), "#a9a9a9"));
        props.put("distance", Math.max(coordCnt, 0));

        if (lineType.equals(LineType.LINE_OUT)) {
            // 暂存坐标
            lineOutCoords = new LinkedHashSet<>(coodrinates);
        }

        // 只有第二段正线需要end节点信息
        if (end != null && lineType.equals(LineType.MAIN_LINE_SECOND)) {
            props.put("end_tag", end.getTag());
            props.put("end_station_name", end.getStationName());
            props.put("end_railway_direction", end.getRailwayDirection());
            props.put("end_railway_name", end.getRailwayName());
        }

        this.addLine(props);
        this.clearCoords();
    }

    /**
     * 保存为 GeoJSON 文件
     */
    public void saveGeojsonFile(String fileName) {
        File geodataDir = plugin.getGeodataDir();
        if (!geodataDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            geodataDir.mkdir();
        }
        File file = new File(geodataDir, fileName);

        // 保存之前优化折线坐标
        Feature node = null;
        List<String> childTags = new ArrayList<>();
        for (Feature feature : collection.getFeatures()) {
            if (feature.getGeometry() instanceof LineString lineString) {
                feature.setGeometry(new LineString(Utils.simplifyLineString(lineString.getCoordinates()).toArray(new LngLatAlt[0])));
                LineType lineType = LineType.fromType((String) feature.getProperties().getOrDefault("line_type", "none"));
                if (lineType.equals(LineType.MAIN_LINE_SECOND)) {
                    childTags.add((String) feature.getProperties().get("end_tag"));
                }
            } else if (feature.getGeometry() instanceof Point) {
                node = feature;
            }
        }

        // 添加子结点属性，方便前端构建图结构
        if (node != null) {
            node.getProperties().put("child_tags", childTags);
        }

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, collection);
        } catch (IOException e) {
            sendMessageAndLog(Component.text("保存geojson时失败: " + e.getMessage(), NamedTextColor.RED));
            return;
        }
        sendMessageAndLog(Component.text("保存geojson: " + file.getPath(), NamedTextColor.GREEN));
    }

    public void resetFeatureCollection() {
        this.collection = new FeatureCollection();
    }

    /**
     * 销毁遍历用的矿车
     */
    private void destroyMember() {
        if (member == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            member.getGroup().destroy();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> member.getGroup().destroy());
        }
    }

    /**
     * 销毁这个GeoWalkingPoint
     */
    public void destroy() {
        destroyMember();
        for (Handler handler : logger.getHandlers()) {
            handler.close();
        }
    }

    /**
     * 发送消息给玩家并写入日志文件
     *
     * @param msg 消息
     */
    private void sendMessageAndLog(Component msg) {
        if (sender.isOnline()) {
            sender.sendMessage(msg);
        }
        TextColor color = getPrimaryColor(msg);
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        if (color == NamedTextColor.GREEN) {
            logger.info(plain);
        } else if (color == NamedTextColor.YELLOW) {
            logger.warning(plain);
        } else if (color == NamedTextColor.RED) {
            logger.severe(plain);
        } else {
            logger.info(plain); // 默认
        }
    }
}

