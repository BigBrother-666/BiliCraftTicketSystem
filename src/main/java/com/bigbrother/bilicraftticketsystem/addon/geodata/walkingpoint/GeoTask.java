package com.bigbrother.bilicraftticketsystem.addon.geodata.walkingpoint;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.MermaidGraph;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bigbrother.bilicraftticketsystem.addon.geodata.Utils.sendComponentMessage;


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
public class GeoTask {
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
    }

    private final BiliCraftTicketSystem plugin;
    private GeoWalkingPoint geoWalkingPoint;
    private Player sender;
    private BukkitTask bukkitTask;

    private final AtomicBoolean finished = new AtomicBoolean(true);

    public GeoTask(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    public void startPathFinding(String platformTag, @NotNull Player sender) {
        this.sender = sender;
        this.geoWalkingPoint = new GeoWalkingPoint(this.sender, plugin);

        // 同时只能有一个任务进行
        if (!finished.get()) {
            sendComponentMessage(this.sender, Component.text("当前已经有铁轨遍历任务在进行", NamedTextColor.YELLOW));
            return;
        }

        sendComponentMessage(sender, Component.text("开始铁轨遍历任务", NamedTextColor.DARK_AQUA));

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
            sendComponentMessage(sender, Component.text("铁轨遍历任务不存在！", NamedTextColor.YELLOW));
            return;
        }
        finished.set(true);
        sendComponentMessage(sender, Component.text("铁轨遍历任务已结束", NamedTextColor.DARK_AQUA));
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
            sendComponentMessage(sender, Component.text("任务失败，站台tag不存在", NamedTextColor.RED));
            return;
        }
        GeoWalkingPoint.ErrorType startErrorType = geoWalkingPoint.findNextSwitcher(startNode.getTag());
        if (startErrorType != GeoWalkingPoint.ErrorType.NONE && startErrorType != GeoWalkingPoint.ErrorType.UNEXPECTED_SIGN) {
            sendComponentMessage(sender, Component.text("任务失败，switcher和指定的站台tag不匹配", NamedTextColor.RED));
            return;
        }

        Queue<GeoWalkingPoint.WalkingPointNode> nodeQueue = new LinkedList<>();
        nodeQueue.add(new GeoWalkingPoint.WalkingPointNode(
                startNode,
                geoWalkingPoint.getLastDirection(),
                geoWalkingPoint.getLastLocation()
        ));

        // BFS
        while (!nodeQueue.isEmpty()) {
            GeoWalkingPoint.WalkingPointNode source = nodeQueue.poll();
            geoWalkingPoint.resetWalkingPoint(source);
            geoWalkingPoint.resetFeatureCollection();

            List<MermaidGraph.Edge> edges = TrainRoutes.graph.adjacencyList.get(source);
            if (edges == null) {
                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(source);
                geoWalkingPoint.saveGeojsonFile(
                        new File(
                                plugin.getGeodataDir(),
                                "%s_%s.geojson".formatted(
                                        source.getTag(),
                                        source.getRailwayDirection()
                                )
                        )
                );
                continue;
            }

            // ===================== 从当前位置寻找下一个remtag（正线第一部分）=====================
            // 只有开通的车站才有
            if (source.isStation()) {
                // remtag可能需要多找几次
                boolean find = false;
                for (int i = 0; i < 4; i++) {
                    GeoWalkingPoint.ErrorType errorType = geoWalkingPoint.findNextRemtag(source.getTag());
                    switch (errorType) {
                        case NONE:
                            find = true;
                            break;
                        case UNEXPECTED_SIGN:
                            // 发现不正确的remtag
                            continue;
                        case WALKING_POINT_ERROR:
                            return;
                    }
                    if (find) {
                        break;
                    }
                }
                geoWalkingPoint.addCoords2FeatureCollection(LineType.MAIN_LINE_FIRST, source, null);
            }

            // ===================== 遍历所有正线 =====================
            for (MermaidGraph.Edge edge : edges) {
                MermaidGraph.Node target = edge.getTarget();
                GeoWalkingPoint.ErrorType errorType;
                // 从当前remtag寻找下一个switcher/remtag（正线第二部分）
                if (target.isUnusedStationNode()) {
                    // 下一个节点是未开通车站（只有remtag）
                    errorType = geoWalkingPoint.findNextRemtag(target.getTag());
                } else {
                    errorType = geoWalkingPoint.findNextSwitcher(target.getTag());
                }
                switch (errorType) {
                    case NONE:
                        break;
                    case UNEXPECTED_SIGN:
                        // 发现bcspawn/station，是起点/终点站
                        break;
                    case WALKING_POINT_ERROR:
                        return;
                }
                geoWalkingPoint.addCoords2FeatureCollection(LineType.MAIN_LINE_SECOND, source, target);

                // 记录这个switcher/remtag（未开通车站）/bcspawn（起点站）/station（终点站）的坐标和方向，入队列
                nodeQueue.add(new GeoWalkingPoint.WalkingPointNode(
                        target,
                        geoWalkingPoint.getLastDirection(),
                        geoWalkingPoint.getLastLocation()
                ));
            }

            // ===================== 如果是车站节点，重置WalkingPoint，遍历进站和出站线 =====================
            if (source.isStation()) {
                geoWalkingPoint.resetWalkingPoint(source);
                GeoWalkingPoint.ErrorType errorType = geoWalkingPoint.findNextBCSpawn(source.getPlatformTag());
                if (!errorType.equals(GeoWalkingPoint.ErrorType.NONE)) {
                    sendComponentMessage(sender, Component.text("遍历铁轨异常结束！", NamedTextColor.RED));
                    return;
                }

                // 添加bcspawn车站节点信息
                geoWalkingPoint.addPoint2FeatureCollection(new GeoWalkingPoint.WalkingPointNode(source, geoWalkingPoint.getLastDirection(), geoWalkingPoint.getLastLocation()));

                if (geoWalkingPoint.getCoodrinates().size() > 1) {
                    // 非起始/终点站
                    geoWalkingPoint.addCoords2FeatureCollection(LineType.LINE_IN, source, null);
                }

                errorType = geoWalkingPoint.findNextRemtag(source.getTag());
                if (!errorType.equals(GeoWalkingPoint.ErrorType.NONE)) {
                    sendComponentMessage(sender, Component.text("遍历铁轨异常结束！", NamedTextColor.RED));
                    return;
                }
                geoWalkingPoint.addCoords2FeatureCollection(LineType.LINE_OUT, source, null);
            }

            // 保存一个完整的节点geojson文件
            geoWalkingPoint.saveGeojsonFile(
                    new File(
                            plugin.getGeodataDir(),
                            "%s_%s.geojson".formatted(
                                    source.getTag(),
                                    source.getRailwayDirection()
                            )
                    )
            );
        }
    }
}
