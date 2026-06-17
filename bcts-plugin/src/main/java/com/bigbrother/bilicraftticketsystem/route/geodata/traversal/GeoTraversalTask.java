package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 铁路遍历任务：从所有已登记起点做全图 BFS，按线路分文件产出 geojson。
 * <p>
 * 流程：
 * <ol>
 *   <li>从数据库读出所有登记起点（{@link GeoNodeLoc}，含 lineId + 坐标 + 方向）。</li>
 *   <li>建一个共享 {@link TraversalCollector} 与共享去重集合，对每个起点调用 {@link GraphWalk#walkFrom}：
 *       以 bcswitcher / platform 为节点、其间铁路为有向边做全图展开。矿车携带「当前 lineId」沿途更新
 *       （离开道岔出向时改写），决定每段边归属；一个起点即可覆盖其连通子网，后续起点撞到已访问状态即停。</li>
 *   <li>每条线路一个 {@code <lineId>.geojson}（共用轨道在各线文件中均完整）。</li>
 *   <li>遍历后按线把实际到达车站与配置 {@code bossbar-stations} 比对，报告缺失 / 多余。</li>
 * </ol>
 * 遍历在主线程执行（需读取实时轨道数据）。
 */
public class GeoTraversalTask {
    private final BiliCraftTicketSystem plugin;
    private final CommandSender sender;
    /**
     * 单段行走最多记录的铁轨格数。
     */
    private static final int MAX_EDGES_PER_WALK = 5000;
    /**
     * 整次遍历最多展开的段数（跨所有起点，兜底防环；真正防环靠共享 visited）。
     */
    private static final int MAX_TOTAL_NODES = 100000;

    /**
     * @param plugin 插件实例
     * @param sender 发起遍历者
     */
    public GeoTraversalTask(BiliCraftTicketSystem plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    /**
     * 遍历所有已登记线路起点并分文件保存。
     */
    public void runAll() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            GeoTraversalLogger log = new GeoTraversalLogger(plugin, sender);
            try {
                List<GeoNodeLoc> starts = plugin.getGeoDatabaseManager().getAllGeoNodeLoc();
                if (starts.isEmpty()) {
                    log.message("没有已登记的线路起点，请先用 /railgeo setStartPos <lineId> 登记", NamedTextColor.RED);
                    return;
                }
                log.message("开始遍历，共 " + starts.size() + " 个登记起点...", NamedTextColor.DARK_AQUA);

                // 整次遍历共享一个收集器与去重集合：一个起点即可覆盖其连通子网，
                // 后续起点撞到已访问状态立即终止（多起点用于覆盖不连通子网）。
                TraversalCollector collector = new TraversalCollector();
                Set<String> visited = new java.util.HashSet<>();
                GraphWalk walk = new GraphWalk(collector, log, visited, MAX_TOTAL_NODES, MAX_EDGES_PER_WALK);
                java.util.Set<String> startLineIds = new java.util.LinkedHashSet<>();
                for (GeoNodeLoc start : starts) {
                    Block startRail = resolveStartRail(start.getStartLocation());
                    if (startRail == null) {
                        log.warn("起点 " + start.getLineId() + " 坐标处没有铁轨，跳过");
                        continue;
                    }
                    startLineIds.add(start.getLineId());
                    log.info("从起点 " + start.getLineId() + " @ " + start.getStartLocation() + " 展开");
                    walk.walkFrom(start.getLineId(), startRail, start.getStartDirection());
                }

                // 按线校验：覆盖所有起点登记线 + 遍历中实际到达过车站的线。
                Map<String, Set<String>> byLine = walk.getVisitedStationsByLine();
                java.util.Set<String> linesToCheck = new java.util.LinkedHashSet<>(startLineIds);
                linesToCheck.addAll(byLine.keySet());
                for (String lineId : linesToCheck) {
                    validateStationOrder(lineId, byLine.getOrDefault(lineId, java.util.Collections.emptySet()), log);
                }

                // 所有区间收集完毕后，按空间交叉关系全局重算 layer（高架压平交）
                log.message("计算LineString层级...", NamedTextColor.DARK_AQUA);
                collector.assignLayers();
                int files = saveAll(collector, log);
                log.message("遍历完成：共 %d 个节点、%d 条区间，写入 %d 个文件".formatted(
                        collector.totalNodes(), collector.totalEdges(), files), NamedTextColor.GREEN);
            } catch (Exception e) {
                log.error("遍历失败", e);
                log.message("遍历失败：" + e, NamedTextColor.RED);
            } finally {
                log.close();
            }
        });
    }

    /**
     * 事后校验：把本线实际到达的车站与配置的 {@code bossbar-stations} 比对，报告缺失 / 多余。
     * <p>
     * 图遍历可能有分叉（如正线跨站 + 停靠线进站），不强求顺序严格一致，只做集合层面的覆盖检查：
     * 配置里有但没走到的（缺失，可能轨道未铺或控制牌缺声明）、走到但配置里没有的（多余，可能站名写错）。
     *
     * @param lineId  线路 id
     * @param visited 实际到达的车站名
     * @param log     日志
     */
    private void validateStationOrder(String lineId, Set<String> visited, GeoTraversalLogger log) {
        LineInfo info = LineConfig.get(lineId);
        if (info == null || info.getBossbarStations().isEmpty()) {
            return;
        }
        Set<String> expected = new java.util.LinkedHashSet<>(info.getBossbarStations());
        for (String want : expected) {
            if (!visited.contains(want)) {
                log.warn("线路 " + lineId + " 校验：配置车站 \"" + want + "\" 未在遍历中到达（轨道未铺设或道岔未声明该线？）");
            }
        }
        for (String got : visited) {
            if (!expected.contains(got)) {
                log.warn("线路 " + lineId + " 校验：到达了配置外的车站 \"" + got + "\"（站名写错或控制牌归属线路有误？）");
            }
        }
    }

    /**
     * 解析起点铁轨方块（起点坐标即铁轨方块；若该处不是铁轨再看下方一格）。
     *
     * @param loc 起点坐标
     * @return 铁轨方块，找不到返回 null
     */
    private Block resolveStartRail(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        Block block = loc.getBlock();
        if (GeoUtils.isRail(block.getType())) {
            return block;
        }
        Block below = block.getRelative(0, -1, 0);
        if (GeoUtils.isRail(below.getType())) {
            return below;
        }
        return null;
    }

    /**
     * 把收集器中的每个文件分组写成 geojson。
     *
     * @param collector 结果收集器
     * @param log       日志
     * @return 写出的文件数
     */
    private int saveAll(TraversalCollector collector, GeoTraversalLogger log) {
        File dir = plugin.getGeodataDir();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper();
        int files = 0;
        for (String fileKey : collector.fileKeys()) {
            List<RailEdge> edges = collector.edgesOf(fileKey);
            List<RailNode> nodes = collector.nodesOf(fileKey);
            FeatureCollection fc = new GeojsonBuilder().build(nodes, edges);
            File file = new File(dir, fileKey + ".geojson");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, fc);
                log.info("写入 " + file.getName() + "：" + nodes.size() + " 节点，" + edges.size() + " 区间");
                files++;
            } catch (Exception e) {
                log.error("写入文件失败：" + file.getName(), e);
            }
        }
        return files;
    }
}
