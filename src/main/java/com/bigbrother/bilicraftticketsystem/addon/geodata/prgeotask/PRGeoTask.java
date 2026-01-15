package com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeojsonManager;
import com.bigbrother.bilicraftticketsystem.addon.geodata.entity.GeoManualLine;
import com.bigbrother.bilicraftticketsystem.addon.geodata.entity.GeoNodeLoc;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import static com.bigbrother.bilicraftticketsystem.addon.geodata.GeoUtils.getPrimaryColor;


/**
 * 旧思路（弃用）：
 * 1. 输入每条线路的每个方向的起始坐标和方向
 * 2. 遍历铁轨的同时记录联络线道岔坐标和方向，以便后续遍历
 * 还要记录showroute和正线道岔坐标，showroute作为车站坐标
 * 3. 遍历完整条主线后，遍历该线路正线道岔，记录每站正线
 * 4. 重复 1-3 步遍历完所有方向的主线
 * 5. 遍历联络线道岔
 * <p>
 * 线路坐标优化算法：
 * 每 n 格（10左右）记录一次坐标，若当前坐标和上一次记录的坐标x或z坐标相同（直线），则覆盖上次记录的点；
 * 若不同，则有可能是弯道，记录弯道开始坐标（上一个记录的坐标）的下标，直到再次遇到直线，然后对这段曲线使用一次 RDP 算法。
 */
public class PRGeoTask {
    private final BiliCraftTicketSystem plugin;
    private final PRGeoWalkingPoint geoWalkingPoint;
    private final Player sender;
    private BukkitTask bukkitTask;
    private Logger logger;
    @Setter
    private boolean lessLog;

    private final AtomicBoolean finished = new AtomicBoolean(true);

    public PRGeoTask(BiliCraftTicketSystem plugin, Player sender) {
        this.sender = sender;
        this.plugin = plugin;
        this.lessLog = false;
        this.geoWalkingPoint = new PRGeoWalkingPoint(plugin, this);
    }

    public void startFullPathFinding() {
        this.logger = getGeoTaskLogger();

        // 同时只能有一个任务进行
        if (!finished.get()) {
            sendMessageAndLog(Component.text("当前已经有铁轨遍历任务在进行", NamedTextColor.YELLOW), true);
            return;
        }

        finished.set(false);
        sendMessageAndLog(Component.text("开始铁轨遍历任务", NamedTextColor.DARK_AQUA), true);

        // 任务线程
        bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<PRGeoWalkingPoint.WalkingPointNode> walkingPointNodes = buildGeoNodes(TrainRoutes.graph.startNodes);
                if (walkingPointNodes == null) {
                    return;
                }

                Set<MermaidGraph.Node> visited = new HashSet<>();
                // 确保全部开始节点指定了坐标
                for (PRGeoWalkingPoint.WalkingPointNode startNodeData : walkingPointNodes) {
                    iterateMermaidGraph(startNodeData, visited);
                }
            } catch (Exception e) {
                sendMessageAndLog(Component.text("遍历铁轨时发生异常：" + e, NamedTextColor.RED), true);
            } finally {
                stopPathFindingTask();
            }
        });
    }

    /**
     * 根据mermaid图个geojson自动判断新增的铁路并更新
     * 避免进行完整遍历
     */
    public void startUpdatePathFinding() {
        this.logger = getGeoTaskLogger();

        // 同时只能有一个任务进行
        if (!finished.get()) {
            sendMessageAndLog(Component.text("当前已经有铁轨遍历任务在进行", NamedTextColor.YELLOW), true);
            return;
        }

        finished.set(false);
        sendMessageAndLog(Component.text("检查待更新的节点...", NamedTextColor.DARK_AQUA), true);

        // 任务线程
        bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GeojsonManager geojsonManager = new GeojsonManager(plugin.getGeodataDir());

                // 分别获取mermaid的节点和geojson的节点，比较得出geojson缺少的节点
                Set<MermaidGraph.Node> geojsonNodes = geojsonManager.buildNodes();
                Set<MermaidGraph.Node> mermaidNodes = TrainRoutes.graph.getAllNodes();
                mermaidNodes.removeAll(geojsonNodes);

                // 从所有缺少节点的父结点遍历
                Set<MermaidGraph.Node> updateNodes = new HashSet<>();
                for (MermaidGraph.Node node : mermaidNodes) {
                    updateNodes.add(node);
                    updateNodes.addAll(TrainRoutes.graph.getParentNodes(node));
                }
                sendMessageAndLog(Component.text("找到 %d 个待更新的节点：".formatted(updateNodes.size()), NamedTextColor.GREEN), true);
                String pTags = updateNodes.stream()
                        .map(MermaidGraph.Node::getPlatformTag)
                        .collect(Collectors.joining(", "));
                sendMessageAndLog(Component.text(pTags, NamedTextColor.GREEN), true);

                // 检查所有待更新节点的起始坐标
                List<PRGeoWalkingPoint.WalkingPointNode> walkingPointNodes = buildGeoNodes(updateNodes);
                if (walkingPointNodes == null) {
                    return;
                }

                sendMessageAndLog(Component.text("开始铁轨遍历任务", NamedTextColor.DARK_AQUA), true);
                Set<MermaidGraph.Node> visited = new HashSet<>();
                iterateMermaidGraph(new LinkedList<>(walkingPointNodes), visited);

            } catch (IOException e) {
                sendMessageAndLog(Component.text("读取geojson时发生异常：" + e, NamedTextColor.RED), true);
            } catch (Exception e) {
                sendMessageAndLog(Component.text("遍历铁轨时发生异常：" + e, NamedTextColor.RED), true);
            } finally {
                stopPathFindingTask();
            }
        });
    }

    private @Nullable List<PRGeoWalkingPoint.WalkingPointNode> buildGeoNodes(Collection<MermaidGraph.Node> nodes) {
        boolean checkResult = true;
        List<PRGeoWalkingPoint.WalkingPointNode> pointList = new ArrayList<>();

        for (MermaidGraph.Node node : nodes) {
            GeoNodeLoc geoNodeLoc = plugin.getGeoDatabaseManager().getGeoNodeLoc(node.getPlatformTag());
            if (geoNodeLoc == null) {
                checkResult = false;
                sendMessageAndLog(Component.text("开始节点 [%s] 起点未指定".formatted(node.getPlatformTag()), NamedTextColor.YELLOW), true);
            } else {
                pointList.add(new PRGeoWalkingPoint.WalkingPointNode(node, geoNodeLoc.getStartDirection(), geoNodeLoc.getStartLocation()));
            }
        }
        if (checkResult) {
            return pointList;
        } else {
            return null;
        }
    }

    public void stopPathFindingTask() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        } else {
            sendMessageAndLog(Component.text("铁轨遍历任务不存在！", NamedTextColor.YELLOW), true);
            this.closeLoggerHandler();
            return;
        }

        if (this.geoWalkingPoint != null) {
            this.geoWalkingPoint.destroyMember();
        }

        finished.set(true);
        sendMessageAndLog(Component.text("铁轨遍历任务已结束", NamedTextColor.DARK_AQUA), true);
        this.closeLoggerHandler();
    }

    /**
     * 更新geojson逻辑
     */
    private void iterateMermaidGraph(Queue<PRGeoWalkingPoint.WalkingPointNode> updateNodes, Set<MermaidGraph.Node> visited) {
        bfs(updateNodes, true, visited);
    }

    /**
     * 从某个节点的switcher开始，按照mermaid的有向图遍历铁路系统
     * <p>
     * 每个线路的每个方向的车站节点包括三段线路，进站、出站、正线
     * 进站：switcher->bcspawn
     * 出站：bcspawn->remtag
     * 正线分两段：switcher->remtag 和 remtag->下一个switcher
     * 车站节点可能包含多个正线（区间有联络线的情况）
     * <p>
     * 道岔节点、未开通的车站只有正线线路
     * <p>
     * 特殊：起点、终点、尽头式车站
     */
    private void iterateMermaidGraph(PRGeoWalkingPoint.WalkingPointNode startNode, Set<MermaidGraph.Node> visited) {
        if (startNode == null) {
            sendMessageAndLog(Component.text("任务失败，站台tag不存在", NamedTextColor.RED), true);
            return;
        }
        geoWalkingPoint.resetWalkingPoint(startNode.getLocation().getBlock(), startNode.getDirection());
        PRGeoWalkingPoint.ErrorType startErrorType = geoWalkingPoint.findNextSwitcher(null, startNode.getTag());
        if (startErrorType != PRGeoWalkingPoint.ErrorType.NONE && startErrorType != PRGeoWalkingPoint.ErrorType.UNEXPECTED_SIGN) {
            sendMessageAndLog(Component.text("任务失败，switcher和指定的站台tag不匹配", NamedTextColor.RED), true);
            writeDebugLog(PRGeoWalkingPoint.LineType.NONE, startNode, null);
            return;
        }

        Queue<PRGeoWalkingPoint.WalkingPointNode> nodeQueue = new LinkedList<>();
        nodeQueue.add(new PRGeoWalkingPoint.WalkingPointNode(
                startNode,
                geoWalkingPoint.getLastDirection(),
                geoWalkingPoint.getLastLocation()
        ));

        bfs(nodeQueue, false, visited);
    }

    /**
     * @param nodeQueue  节点队列
     * @param updateMode 是否是更新模式，更新模式：不向队列添加新节点，只处理队列中的节点
     * @param visited    已经遍历过的节点，防止环路
     */
    private void bfs(Queue<PRGeoWalkingPoint.WalkingPointNode> nodeQueue, boolean updateMode, Set<MermaidGraph.Node> visited) {
        // BFS
        while (!nodeQueue.isEmpty()) {
            PRGeoWalkingPoint.WalkingPointNode source = nodeQueue.poll();
            visited.add(source);
            geoWalkingPoint.resetWalkingPoint(source);

            // 保存节点起始坐标方向
            plugin.getGeoDatabaseManager().upsertGeoNodeLoc(source.getPlatformTag(), source.getLocation(), source.getDirection());

            List<MermaidGraph.Edge> edges = TrainRoutes.graph.getEdges(source);
            if (edges == null || edges.isEmpty()) {
                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(source);
                geoWalkingPoint.saveGeojsonFile(
                        "%s_%s.geojson".formatted(
                                source.getTag(),
                                source.getRailwayDirection()
                        )
                );
                continue;
            }

            // ===================== 遍历进站和出站线 =====================
            iterateLineInOut(source);

            // ===================== 从当前位置寻找出站道岔（正线第一部分）=====================
            iterateLineFirst(source);

            // ===================== 从出站道岔遍历所有正线第二部分 =====================
            iterateLineSecond(nodeQueue, source, visited, updateMode);

            // ==========================================================
            // 保存一个完整的节点geojson文件
            geoWalkingPoint.saveGeojsonFile(
                    "%s_%s.geojson".formatted(
                            source.getTag(),
                            source.getRailwayDirection()
                    )
            );
        }
    }

    /**
     * 遍历进站出站线路
     *
     * @param source 起始节点
     */
    private void iterateLineInOut(PRGeoWalkingPoint.WalkingPointNode source) {
        // ===================== 如果是车站节点，遍历进站和出站线 =====================
        if (source.isStation()) {
            // 查询该节点有没有自定义线路
            GeoManualLine manualInline = plugin.getGeoDatabaseManager().getGeoManualLine(source.getPlatformTag(), PRGeoWalkingPoint.LineType.LINE_IN);
            GeoManualLine manualOutline = plugin.getGeoDatabaseManager().getGeoManualLine(source.getPlatformTag(), PRGeoWalkingPoint.LineType.LINE_OUT);
            if (manualInline != null && manualOutline != null) {
                // 指定了进站线和出站线
                sendMessageAndLog(Component.text("检测到 %s 手动指定进站出站线".formatted(source.getPlatformTag()), NamedTextColor.GREEN));

                geoWalkingPoint.resetWalkingPoint(manualInline.getStartLocation().getBlock(), manualInline.getStartDirection());
                geoWalkingPoint.setEndRail(manualInline.getEndLocation().getBlock());
                geoWalkingPoint.findEndRail();
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_IN, source, null);

                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(new PRGeoWalkingPoint.WalkingPointNode(source, geoWalkingPoint.getLastDirection(), geoWalkingPoint.getLastLocation()));

                geoWalkingPoint.resetWalkingPoint(manualOutline.getStartLocation().getBlock(), manualOutline.getStartDirection());
                geoWalkingPoint.setEndRail(manualOutline.getEndLocation().getBlock());
                geoWalkingPoint.findEndRail();
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_OUT, source, null);
                return;
            }

            PRGeoWalkingPoint.ErrorType errorType = geoWalkingPoint.findNextBCSpawn(source.getPlatformTag());
            if (!errorType.equals(PRGeoWalkingPoint.ErrorType.NONE)) {
                sendMessageAndLog(Component.text("遍历铁轨异常结束！", NamedTextColor.RED), true);
                writeDebugLog(PRGeoWalkingPoint.LineType.LINE_IN, source, null);
                return;
            }

            // 添加bcspawn车站节点信息
            geoWalkingPoint.addPoint2FeatureCollection(new PRGeoWalkingPoint.WalkingPointNode(source, geoWalkingPoint.getLastDirection(), geoWalkingPoint.getLastLocation()));

            if (geoWalkingPoint.getCoodrinates().size() > 1) {
                // 非起始/终点站
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_IN, source, null);
            }

            errorType = geoWalkingPoint.findNextRemtag(source.getTag(), 4);
            if (!errorType.equals(PRGeoWalkingPoint.ErrorType.NONE)) {
                sendMessageAndLog(Component.text("遍历铁轨异常结束！", NamedTextColor.RED), true);
                writeDebugLog(PRGeoWalkingPoint.LineType.LINE_OUT, source, null);
                return;
            }
            geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_OUT, source, null);
        } else {
            // 添加switcher节点
            geoWalkingPoint.addPoint2FeatureCollection(source);
        }
    }

    /**
     * 遍历正线第一部分
     *
     * @param source 起始节点
     */
    private void iterateLineFirst(PRGeoWalkingPoint.WalkingPointNode source) {
        // ===================== 从当前位置寻找出站道岔（正线第一部分）=====================
        // 只有开通的车站才有
        if (source.isStation()) {
            // 重置WalkingPoint
            geoWalkingPoint.resetWalkingPoint(source);
            // 找出站道岔
            geoWalkingPoint.findOutJunction(source.getTag());
            geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.MAIN_LINE_FIRST, source, null);
        }
    }

    /**
     * 遍历正线第二部分
     *
     * @param nodeQueue  节点BFS队列，用于插入子结点
     * @param source     起始节点
     * @param visited    已经遍历过的节点，防止环路
     * @param updateMode 是否是更新模式，更新模式：不向队列添加新节点，只处理队列中的节点
     */
    private void iterateLineSecond(Queue<PRGeoWalkingPoint.WalkingPointNode> nodeQueue, PRGeoWalkingPoint.WalkingPointNode source, Set<MermaidGraph.Node> visited, boolean updateMode) {
        // ===================== 遍历所有正线第二部分 =====================
        Location outJunctionLocation = geoWalkingPoint.getLastLocation();
        Vector outJunctionDirection = geoWalkingPoint.getLastDirection();
        for (MermaidGraph.Edge edge : TrainRoutes.graph.getEdges(source)) {
            // 重置WalkingPoint到出站道岔或bcspawn
            if (outJunctionLocation != null && outJunctionDirection != null) {
                geoWalkingPoint.resetWalkingPoint(outJunctionLocation.getBlock(), outJunctionDirection);
            } else {
                geoWalkingPoint.resetWalkingPoint(source);
            }

            MermaidGraph.Node target = edge.getTarget();
            PRGeoWalkingPoint.ErrorType errorType;
            // 从当前switcher/bcspawn/出站道岔寻找下一个switcher/remtag（正线第二部分）
            geoWalkingPoint.addTags(source.getTag());
            errorType = geoWalkingPoint.findNextSwitcher(source.getTag(), target.getTag());
            switch (errorType) {
                case NONE:
                    break;
                case UNEXPECTED_SIGN:
                    // 发现bcspawn/station，是起点/终点站
                    break;
                case WALKING_POINT_ERROR:
                    sendMessageAndLog(Component.text("遍历铁轨异常结束！", NamedTextColor.RED), true);
                    writeDebugLog(PRGeoWalkingPoint.LineType.MAIN_LINE_SECOND, source, target);
                    return;
            }
            geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.MAIN_LINE_SECOND, source, target);

            if (!updateMode && visited.add(target)) {
                // 记录这个switcher/remtag（未开通车站）/bcspawn（起点站）/station（终点站）的坐标和方向，入队列
                nodeQueue.add(new PRGeoWalkingPoint.WalkingPointNode(
                        target,
                        geoWalkingPoint.getLastDirection(),
                        geoWalkingPoint.getLastLocation()
                ));
            }
        }
    }

    /**
     * 发送消息给玩家并写入日志文件
     *
     * @param msg 消息
     */
    public void sendMessageAndLog(Component msg) {
        sendMessageAndLog(msg, false);
    }

    public void sendMessageAndLog(Component msg, boolean ignoreLessLog) {
        if (ignoreLessLog || (!lessLog && sender.isOnline())) {
            sender.sendMessage(msg);
        }
        if (this.logger == null) {
            return;
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

    private void writeDebugLog(PRGeoWalkingPoint.LineType lineType, MermaidGraph.Node start, @Nullable MermaidGraph.Node end) {
        logger.info("结束时的变量信息：");
        logger.info("线路类型 -> " + lineType.getType());
        logger.info("开始节点 -> " + start.toString());
        logger.info("结束节点 -> " + (end != null ? end.toString() : "空"));
        logger.info("WalkingPoint坐标 -> " + geoWalkingPoint.getCoodrinates());
    }

    /**
     * 获取地理信息采集任务的logger
     * log存放在 logs/ 下
     *
     * @return logger
     */
    private Logger getGeoTaskLogger() {
        Logger logger = Logger.getLogger("bcts-" + System.currentTimeMillis());
        logger.setUseParentHandlers(false); // 不输出到控制台

        try {
            // 创建日志文件夹
            File logDir = plugin.getDataFolder().toPath().resolve("logs").toFile();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filePath = logDir.getAbsolutePath() + File.separator + timeStr + ".log";

            FileHandler fileHandler = new FileHandler(filePath, false);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);

        } catch (IOException e) {
            plugin.getLogger().severe("无法创建日志文件: " + e.getMessage());
        }

        return logger;
    }

    public void closeLoggerHandler() {
        if (this.logger == null) {
            return;
        }
        for (Handler handler : logger.getHandlers()) {
            handler.flush();
            handler.close();
        }
    }
}
