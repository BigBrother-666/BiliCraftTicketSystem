package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.geodata.GeoUtils;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.List;

/**
 * 铁路遍历任务：遍历所有已登记的线路起点，按线路分文件产出 geojson。
 * <p>
 * 流程（Phase 3 重做方案）：
 * <ol>
 *   <li>从数据库读出每条线路登记的起点（{@link GeoNodeLoc}，按 lineId）。</li>
 *   <li>对每条线路跑一次 {@link LineWalk}：矿车带该 lineId tag 连续行走，platform 记车站节点并
 *       核对车站名顺序，含 default 的 bcswitcher 额外遍历正线，按配置车站走完终止。</li>
 *   <li>按文件分组保存：每条线路一个 {@code <lineId>.geojson}（含其正线），联络线
 *       {@code contact.geojson} 单独保存。</li>
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
     * 单条线路最多经过的节点数（兜底）。
     */
    private static final int MAX_NODES_PER_LINE = 2000;

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
                log.message("开始遍历 " + starts.size() + " 条线路...", NamedTextColor.DARK_AQUA);

                TraversalCollector collector = new TraversalCollector();
                for (GeoNodeLoc start : starts) {
                    Block startRail = resolveStartRail(start.getStartLocation());
                    if (startRail == null) {
                        log.warn("线路 " + start.getLineId() + " 起点坐标处没有铁轨，跳过");
                        continue;
                    }
                    new LineWalk(start.getLineId(), startRail, start.getStartDirection(),
                            collector, log, MAX_NODES_PER_LINE, MAX_EDGES_PER_WALK).walk();
                }

                // 主线全部走完后，统一遍历途中收集到的联络线种子，存入 contact.geojson
                walkContactSeeds(collector, log);

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
     * 遍历主线途中收集到的联络线种子，坐标存入 contact 文件分组（{@code contact.geojson}）。
     * <p>
     * 每个种子用一节带 contact tag 的矿车从来源道岔出发——经该道岔时被导向 contact 分支，
     * 沿联络线走到下一个节点为止。联络线无配置车站，靠轨道结束/节点上限自然终止。
     *
     * @param collector 结果收集器
     * @param log       日志
     */
    private void walkContactSeeds(TraversalCollector collector, GeoTraversalLogger log) {
        List<TraversalCollector.ContactSeed> seeds = collector.getContactSeeds();
        if (seeds.isEmpty()) {
            return;
        }
        log.message("遍历 " + seeds.size() + " 条联络线...", NamedTextColor.DARK_AQUA);
        // 用下标遍历并每轮重读 size()：联络线遍历途中遇到仍含 contact 分支的道岔会往 seeds 追加新种子
        // （见 LineWalk），需一并处理。addContactSeed 按道岔节点 id 去重，故不会无限增长。
        for (int i = 0; i < seeds.size(); i++) {
            TraversalCollector.ContactSeed seed = seeds.get(i);
            new LineWalk(LineInfo.CONTACT_ID, seed.getStartRail(), seed.getStartDirection(),
                    collector, log, MAX_NODES_PER_LINE, MAX_EDGES_PER_WALK)
                    .withInitialPrevNode(seed.getSourceNodeId())
                    .walk();
        }
        if (seeds.size() > 0) {
            log.message("联络线遍历完成，共 " + seeds.size() + " 条", NamedTextColor.DARK_AQUA);
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

    /**
     * 单点调试遍历：从给定铁轨与方向，按指定 lineId 走一条线，写单个文件。
     * 保留作游戏内调试用，不影响 {@link #runAll} 的正式流程。
     *
     * @param startRail      起点铁轨
     * @param startDirection 起点方向
     * @param lineId         线路 id
     */
    public void runSingle(Block startRail, Vector startDirection, String lineId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            GeoTraversalLogger log = new GeoTraversalLogger(plugin, sender);
            try {
                log.message("开始单线调试遍历：" + lineId, NamedTextColor.DARK_AQUA);
                TraversalCollector collector = new TraversalCollector();
                new LineWalk(lineId, startRail, startDirection, collector, log,
                        MAX_NODES_PER_LINE, MAX_EDGES_PER_WALK).walk();
                int files = saveAll(collector, log);
                log.message("调试遍历完成：%d 节点，%d 区间，%d 文件".formatted(
                        collector.totalNodes(), collector.totalEdges(), files), NamedTextColor.GREEN);
            } catch (Exception e) {
                log.error("调试遍历失败", e);
                log.message("调试遍历失败：" + e, NamedTextColor.RED);
            } finally {
                log.close();
            }
        });
    }
}
