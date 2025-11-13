package com.bigbrother.bilicraftticketsystem.addon.geodata.walkingpoint;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;


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
    private PRGeoWalkingPoint geoWalkingPoint;
    private Player sender;
    private BukkitTask bukkitTask;

    private final AtomicBoolean finished = new AtomicBoolean(true);

    public PRGeoTask(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    public void startPathFinding(String platformTag, @NotNull Player sender) {
        this.sender = sender;
        this.geoWalkingPoint = new PRGeoWalkingPoint(this.sender, plugin);

        // 同时只能有一个任务进行
        if (!finished.get()) {
            sender.sendMessage(Component.text("当前已经有铁轨遍历任务在进行", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("开始铁轨遍历任务", NamedTextColor.DARK_AQUA));

        // 任务线程
        bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            finished.set(false);

            iterateMermaidGraph(platformTag);

            stopPathFinding();
        });
    }

    public void stopPathFinding() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        } else {
            sender.sendMessage(Component.text("铁轨遍历任务不存在！", NamedTextColor.YELLOW));
            return;
        }
        this.geoWalkingPoint.destroy();
        finished.set(true);
        sender.sendMessage(Component.text("铁轨遍历任务已结束", NamedTextColor.DARK_AQUA));
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
    private void iterateMermaidGraph(String platformTag) {
        MermaidGraph.Node startNode = TrainRoutes.graph.getBCSpawnNode(platformTag);
        if (startNode == null) {
            sender.sendMessage(Component.text("任务失败，站台tag不存在", NamedTextColor.RED));
            return;
        }
        PRGeoWalkingPoint.ErrorType startErrorType = geoWalkingPoint.findNextSwitcher(null, startNode.getTag());
        if (startErrorType != PRGeoWalkingPoint.ErrorType.NONE && startErrorType != PRGeoWalkingPoint.ErrorType.UNEXPECTED_SIGN) {
            sender.sendMessage(Component.text("任务失败，switcher和指定的站台tag不匹配", NamedTextColor.RED));
            return;
        }

        Queue<PRGeoWalkingPoint.WalkingPointNode> nodeQueue = new LinkedList<>();
        nodeQueue.add(new PRGeoWalkingPoint.WalkingPointNode(
                startNode,
                geoWalkingPoint.getLastDirection(),
                geoWalkingPoint.getLastLocation()
        ));

        // BFS
        while (!nodeQueue.isEmpty()) {
            PRGeoWalkingPoint.WalkingPointNode source = nodeQueue.poll();
            geoWalkingPoint.resetWalkingPoint(source);
            geoWalkingPoint.resetFeatureCollection();

            List<MermaidGraph.Edge> edges = TrainRoutes.graph.adjacencyList.get(source);
            if (edges == null) {
                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(source);
                geoWalkingPoint.saveGeojsonFile(
                        "%s_%s.geojson".formatted(
                                source.getTag().replace("/", ""),
                                source.getRailwayDirection().replace("/", "")
                        )
                );
                continue;
            }

            // ===================== 如果是车站节点，遍历进站和出站线 =====================
            if (source.isStation()) {
                PRGeoWalkingPoint.ErrorType errorType = geoWalkingPoint.findNextBCSpawn(source.getPlatformTag());
                if (!errorType.equals(PRGeoWalkingPoint.ErrorType.NONE)) {
                    sender.sendMessage(Component.text("遍历铁轨异常结束！", NamedTextColor.RED));
                    return;
                }

                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(new PRGeoWalkingPoint.WalkingPointNode(source, geoWalkingPoint.getLastDirection(), geoWalkingPoint.getLastLocation()));

                if (geoWalkingPoint.getCoodrinates().size() > 1) {
                    // 非起始/终点站
                    geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_IN, source, null);
                }

                errorType = geoWalkingPoint.findNextRemtag(source.getTag());
                if (!errorType.equals(PRGeoWalkingPoint.ErrorType.NONE)) {
                    sender.sendMessage(Component.text("遍历铁轨异常结束！", NamedTextColor.RED));
                    return;
                }
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.LINE_OUT, source, null);
            } else {
                // 添加switcher节点
                geoWalkingPoint.addPoint2FeatureCollection(source);
            }

            // ===================== 从当前位置寻找出站道岔（正线第一部分）=====================
            // 只有开通的车站才有
            Vector outJunctionDirection = null;
            Location outJunctionLocation = null;
            if (source.isStation()) {
                // 重置WalkingPoint
                geoWalkingPoint.resetWalkingPoint(source);
                // 找出站道岔
                geoWalkingPoint.findOutJunction(source.getTag());
                outJunctionDirection = geoWalkingPoint.getLastDirection();
                outJunctionLocation = geoWalkingPoint.getLastLocation();
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.MAIN_LINE_FIRST, source, null);
            }

            // ===================== 遍历所有正线第二部分 =====================
            for (MermaidGraph.Edge edge : edges) {
                // 重置WalkingPoint到出站道岔或bcspawn
                if (outJunctionDirection != null && outJunctionLocation != null) {
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
                        sender.sendMessage(Component.text("遍历铁轨异常结束！", NamedTextColor.RED));
                        return;
                }
                geoWalkingPoint.addCoords2FeatureCollection(PRGeoWalkingPoint.LineType.MAIN_LINE_SECOND, source, target);

                // 记录这个switcher/remtag（未开通车站）/bcspawn（起点站）/station（终点站）的坐标和方向，入队列
                nodeQueue.add(new PRGeoWalkingPoint.WalkingPointNode(
                        target,
                        geoWalkingPoint.getLastDirection(),
                        geoWalkingPoint.getLastLocation()
                ));
            }

            // ==========================================================
            // 保存一个完整的节点geojson文件
            geoWalkingPoint.saveGeojsonFile(
                    "%s_%s.geojson".formatted(
                            source.getTag().replace("/", ""),
                            source.getRailwayDirection().replace("/", "")
                    )
            );
        }
    }
}
