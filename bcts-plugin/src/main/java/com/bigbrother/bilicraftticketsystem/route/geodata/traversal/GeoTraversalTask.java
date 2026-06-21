package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.config.RailwayMapConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>
 * 全局约束：同一时刻只允许一个遍历任务（{@link #RUNNING}），且完成后有全局冷却
 * （{@link RailwayMapConfig#getTraversalCooldownSeconds()}）。遍历期间车票/交通卡使用被暂停
 * （见 {@code TrainListeners}，靠 {@link #isTraversalRunning()} 判断）。可用 {@link #stopWalk(CommandSender)}
 * 提前停止当前任务。持 {@link #PERM_BYPASS_COOLDOWN} 权限者可绕过冷却，且其执行不刷新冷却。
 */
public class GeoTraversalTask {
    /**
     * 绕过遍历全局冷却的权限；持此权限者发起遍历不受冷却限制，且其执行结束不刷新冷却时间。
     */
    public static final String PERM_BYPASS_COOLDOWN = "bcts.railgeo.bypasscooldown";

    /**
     * 全局单运行锁：保证同一时刻只有一个遍历任务在跑。
     */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    /**
     * 上次遍历完成的时间戳（ms），用于全局冷却判断；0 表示从未运行。
     */
    private static volatile long lastFinishTime = 0;
    /**
     * 当前正在运行的遍历驱动器，供 {@link #stopWalk(CommandSender)} 请求中止；无任务时为 null。
     */
    private static volatile GraphWalk runningWalk = null;

    private final BiliCraftTicketSystem plugin;
    private final CommandSender sender;

    /**
     * @param plugin 插件实例
     * @param sender 发起遍历者
     */
    public GeoTraversalTask(BiliCraftTicketSystem plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    /**
     * 是否有遍历任务正在进行。供车票/交通卡使用监听器在遍历期间暂停凭证使用。
     *
     * @return 正在遍历返回 true
     */
    public static boolean isTraversalRunning() {
        return RUNNING.get();
    }

    /**
     * 请求停止当前正在进行的遍历任务（中止后不写任何文件）。
     *
     * @param sender 发起停止者（用于反馈结果）
     */
    public static void stopWalk(CommandSender sender) {
        GraphWalk walk = runningWalk;
        if (!RUNNING.get() || walk == null) {
            sender.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(msg("traversal-no-running", "<red>当前没有正在进行的铁轨遍历任务"))));
            return;
        }
        walk.abort("用户 " + sender.getName() + " 手动停止了遍历");
        sender.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(msg("traversal-stop-requested", "<yellow>已请求停止当前铁轨遍历任务..."))));
    }

    /**
     * 遍历所有已登记线路起点并分文件保存。
     * <p>
     * 先做单运行与冷却校验：已有任务在跑或仍在冷却期则直接拒绝并提示，不占用单运行锁。
     * 持 {@link #PERM_BYPASS_COOLDOWN} 权限者绕过冷却，且其执行结束不刷新冷却时间。
     */
    public void runAll() {
        boolean bypassCooldown = sender != null && sender.hasPermission(PERM_BYPASS_COOLDOWN);

        // 有玩家正在进行线路/铁路系统配置向导时不遍历：配置可能改到一半，遍历结果会不一致
        if (WizardManager.hasAnyActive()) {
            sendConfigMessage(msg("traversal-wizard-active",
                    "<red>有玩家正在进行线路/铁路系统配置，请等其完成后再发起遍历"));
            return;
        }

        // 冷却校验（不抢锁）；有 bypass 权限则跳过
        int cooldownSec = RailwayMapConfig.getTraversalCooldownSeconds();
        long remainMs = lastFinishTime + cooldownSec * 1000L - System.currentTimeMillis();
        if (!bypassCooldown && cooldownSec > 0 && lastFinishTime > 0 && remainMs > 0) {
            sendConfigMessage(msg("traversal-cooling-down", "<red>铁轨遍历正在冷却中，请 %d 秒后再试")
                    .formatted((remainMs + 999) / 1000));
            return;
        }
        // 抢单运行锁：已有任务在跑则拒绝
        if (!RUNNING.compareAndSet(false, true)) {
            sendConfigMessage(msg("traversal-already-running",
                    "<red>已有一个铁轨遍历任务正在进行，请等待其完成，或使用 /railgeo stopWalk 停止"));
            return;
        }

        GeoTraversalLogger log = new GeoTraversalLogger(plugin, sender);
        GraphWalk walk = new GraphWalk(new TraversalCollector(), log, new java.util.HashSet<>(),
                RailwayMapConfig.getTraversalMaxTotalNodes(), RailwayMapConfig.getTraversalMaxEdgesPerWalk());
        runningWalk = walk;
        // 进度反馈在异步线程跑：主线程在遍历期间被阻塞，无法用主线程定时任务交错反馈。
        BukkitTask progressTask = startProgressFeedback(walk, log);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                walkAndSave(log, walk);
            } catch (Exception e) {
                log.error("遍历失败", e);
                log.message("遍历失败：" + e, NamedTextColor.RED);
            } finally {
                if (progressTask != null) {
                    progressTask.cancel();
                }
                log.close();
                // bypass 执行不刷新冷却时间
                if (!bypassCooldown) {
                    lastFinishTime = System.currentTimeMillis();
                }
                runningWalk = null;
                RUNNING.set(false);
            }
        });
    }

    /**
     * 读取 {@code messages.yml} 中的一条提示文本。
     *
     * @param key 消息键
     * @param def 缺省值
     * @return 提示文本
     */
    private static String msg(String key, String def) {
        return MainConfig.message.get(key, def);
    }

    /**
     * 启动异步进度反馈：每隔 {@code progress-interval-seconds} 秒把当前已展开段数发给发起者，
     * 让其知道遍历没有卡住。间隔 {@code <=0} 则不反馈，返回 null。
     * <p>
     * 发起者非控制台时，进度同时打到控制台后台，方便管理员观察。
     *
     * @param walk 遍历驱动器（读取已展开段数）
     * @param log  日志（同时写入日志文件）
     * @return 反馈定时任务（遍历结束须取消）；不反馈时为 null
     */
    private BukkitTask startProgressFeedback(GraphWalk walk, GeoTraversalLogger log) {
        int intervalSec = RailwayMapConfig.getTraversalProgressIntervalSeconds();
        if (intervalSec <= 0) {
            return null;
        }
        boolean alsoConsole = !(sender instanceof ConsoleCommandSender);
        long periodTicks = intervalSec * 20L;
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            String text = "遍历进行中，当前已遍历 " + walk.getProcessed() + " 个节点...";
            log.message(text, NamedTextColor.GRAY);
            if (alsoConsole) {
                plugin.getComponentLogger().info(net.kyori.adventure.text.Component.text(text, NamedTextColor.GRAY));
            }
        }, periodTicks, periodTicks);
    }

    /**
     * 执行遍历主流程：展开所有起点、校验车站、计算层级并写文件。
     * 中途若 {@link GraphWalk#isAborted()}（如达到段数上限）或校验发现配置不一致，则中止且不写文件。
     *
     * @param log  日志
     * @param walk 遍历驱动器
     */
    private void walkAndSave(GeoTraversalLogger log, GraphWalk walk) {
        List<GeoNodeLoc> starts = plugin.getGeoDatabaseManager().getAllGeoNodeLoc();
        if (starts.isEmpty()) {
            log.message("没有已登记的线路起点，请先用 /railgeo setStartPos <lineId> 登记", NamedTextColor.RED);
            return;
        }
        log.message("开始遍历，共 " + starts.size() + " 个登记起点...", NamedTextColor.DARK_AQUA);

        // 整次遍历共享一个收集器与去重集合：一个起点即可覆盖其连通子网，
        // 后续起点撞到已访问状态立即终止（多起点用于覆盖不连通子网）。
        TraversalCollector collector = walk.getCollector();
        java.util.Set<String> startLineIds = new java.util.LinkedHashSet<>();
        for (GeoNodeLoc start : starts) {
            Block startRail = resolveStartRail(start.getStartLocation());
            if (startRail == null) {
                // 起点坐标处无轨道：中止整次遍历，不写文件，反馈详细信息便于排查
                walk.abort("起点 " + start.getLineId() + " 坐标处没有铁轨（坐标 " + start.getStartLocation()
                        + "）。请检查该线路登记的起点是否仍在轨道上，如果该起点已废弃，请使用/railgeo delStartPos <lineId>删除");
                break;
            }
            startLineIds.add(start.getLineId());
            log.info("从起点 " + start.getLineId() + " @ " + start.getStartLocation() + " 展开");
            walk.walkFrom(start.getLineId(), startRail, start.getStartDirection());
            if (walk.isAborted()) {
                break;
            }
        }

        if (walk.isAborted()) {
            log.message("遍历已中止，未写入任何文件：" + walk.getAbortReason(), NamedTextColor.RED);
            return;
        }

        log.message("遍历完成，校验车站和配置是否对应...", NamedTextColor.DARK_AQUA);

        // 按线校验：覆盖所有起点登记线 + 遍历中实际到达过车站的线。
        Map<String, Set<String>> byLine = walk.getVisitedStationsByLine();
        java.util.Set<String> linesToCheck = new java.util.LinkedHashSet<>(startLineIds);
        linesToCheck.addAll(byLine.keySet());
        for (String lineId : linesToCheck) {
            if (!validateStationOrder(lineId, byLine.getOrDefault(lineId, java.util.Collections.emptySet()), walk, log)) {
                log.message("遍历已中止，未写入任何文件：" + walk.getAbortReason(), NamedTextColor.RED);
                return;
            }
        }

        // 所有区间收集完毕后，按空间交叉关系全局重算 layer（高架压平交）
        log.message("验证完成，开始计算LineString层级...", NamedTextColor.DARK_AQUA);
        collector.assignLayers();
        int files = saveAll(collector, log);
        log.message("遍历完成：共 %d 个节点、%d 条区间，写入 %d 个文件".formatted(
                collector.totalNodes(), collector.totalEdges(), files), NamedTextColor.GREEN);

        // 重载配置
        log.message("重载配置文件...", NamedTextColor.DARK_AQUA);
        plugin.loadConfig(null);
        // 重载配置
        log.message("重载配置完成", NamedTextColor.GREEN);
    }

    /**
     * 把一条配置文本（MiniMessage / &amp; 代码）发给发起者；控制台与玩家都可。
     *
     * @param mmText 配置的提示文本
     */
    private void sendConfigMessage(String mmText) {
        if (sender != null) {
            sender.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(mmText)));
        }
    }

    /**
     * 事后校验：把本线实际到达的车站与配置的 {@code bossbar-stations} 比对，报告缺失 / 多余。
     * <p>
     * 图遍历可能有分叉（如正线跨站 + 停靠线进站），不强求顺序严格一致，只做集合层面的覆盖检查：
     * 配置里有但没走到的（缺失，可能轨道未铺或控制牌缺声明）、走到但配置里没有的（多余，可能站名写错）。
     * 任一不一致都视为遍历失败：中止并不写文件，详细信息反馈给发起者。
     *
     * @param lineId  线路 id
     * @param visited 实际到达的车站名
     * @param walk    遍历驱动器（用于在校验失败时中止）
     * @param log     日志
     * @return 校验通过返回 true；发现缺失 / 多余返回 false（并已 abort）
     */
    @SuppressWarnings("unused")
    private boolean validateStationOrder(String lineId, Set<String> visited, GraphWalk walk, GeoTraversalLogger log) {
        LineInfo info = LineConfig.get(lineId);
        if (info == null || info.getBossbarStations().isEmpty()) {
            return true;
        }
        Set<String> expected = new java.util.LinkedHashSet<>(info.getBossbarStations());
        for (String want : expected) {
            if (!visited.contains(want)) {
                walk.abort("线路 " + lineId + " 校验：配置车站 \"" + want
                        + "\" 未在遍历中到达（轨道未铺设或道岔未声明该线？）");
                return false;
            }
        }
        for (String got : visited) {
            if (!expected.contains(got)) {
                walk.abort("线路 " + lineId + " 校验：到达了配置外的车站 \"" + got
                        + "\"（站名写错或控制牌归属线路有误？）");
                return false;
            }
        }
        return true;
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
