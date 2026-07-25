package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 铁路遍历任务：从所有已登记起点做全图 BFS，按线路分文件产出 geojson。
 * <p>
 * 流程：
 * <ol>
 *   <li>从数据库读出所有登记起点（{@link GeoNodeLoc}，含 lineId + 坐标 + 方向）。</li>
 *   <li>建一个共享 {@link TraversalCollector} 与共享去重集合，把每个起点 {@link GraphWalk#seed} 进共享队列，
 *       再用主线程定时任务每 tick 调 {@link GraphWalk#stepBatch} 分片展开：
 *       以 bcswitcher / platform 为节点、其间铁路为有向边做全图展开。矿车携带「当前 lineId」沿途更新
 *       （离开道岔出向时改写），决定每段边归属；一个起点即可覆盖其连通子网，后续起点撞到已访问状态即停。</li>
 *   <li>每条线路一个 {@code <lineId>.geojson}（共用轨道在各线文件中均完整）。</li>
 *   <li>遍历后按线把实际到达车站与配置 {@code bossbar-stations} 比对，报告缺失 / 多余。</li>
 * </ol>
 * 遍历在主线程<b>分片</b>执行（需读取实时轨道数据）：每 tick 只展开
 * {@link MapConfig#getTraversalEdgesPerInterval()} 段后让出主线程，避免一次性展开整张图卡死服务器、
 * 影响其它玩家。
 * <p>
 * 全局约束：同一时刻只允许一个遍历任务（{@link #RUNNING}），且完成后有全局冷却
 * （{@link MapConfig#getTraversalCooldownSeconds()}）。遍历期间车票/交通卡使用被暂停
 * （见 {@code TrainListeners}，靠 {@link #isTraversalRunning()} 判断）。可用 {@link #stopWalk(CommandSender)}
 * 提前停止当前任务。持 {@link #PERM_BYPASS_COOLDOWN} 权限者可绕过冷却，且其执行不刷新冷却。
 */
public class GeoTraversalTask {
    /**
     * 绕过遍历全局冷却的权限；持此权限者发起遍历不受冷却限制，且其执行结束不刷新冷却时间。
     */
    public static final String PERM_BYPASS_COOLDOWN = "bcts.bypass";

    /**
     * 全局单运行锁：保证同一时刻只有一个遍历任务在跑。
     */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    /**
     * 上次 {@code walkAll}（全图遍历）完成的时间戳（ms），用于其冷却判断；0 表示从未运行。
     * 与 {@link #lastWalkFinishTime} 独立计时：walkAll 完成只刷新它、不影响 walk 冷却。
     */
    private static volatile long lastFinishTime = 0;
    /**
     * 上次 {@code walk}（单线增量遍历）完成的时间戳（ms），用于其冷却判断；0 表示从未运行。
     * 与 {@link #lastFinishTime} 独立计时。
     */
    private static volatile long lastWalkFinishTime = 0;
    /**
     * 当前正在运行的遍历驱动器，供 {@link #stopWalk(CommandSender)} 请求中止；无任务时为 null。
     */
    private static volatile GraphWalk runningWalk = null;

    private final BiliCraftTicketSystem plugin;
    private final CommandSender sender;
    /**
     * 本次遍历所有登记起点的线路 id（seed 阶段填充），供展开结束后的车站校验覆盖到起点登记线。
     */
    private final Set<String> startLineIds = new LinkedHashSet<>();
    /**
     * 增量遍历的目标线路 id 集合；为空表示全图遍历（{@code walkAll}）。
     * 非空时：只 seed 这些线的起点、出向 scope 限定为 {@code 目标线集合 ∪ {contact}}、收尾只写
     * 这些线的 {@code <lineId>.geojson} 与合并后的 {@code contact.geojson}。
     */
    private final Set<String> targetLineIds;
    /**
     * 忽略的线路 id 集合（{@code walkAll --ignore} 用）：不展开 bcswitcher 中指向这些线的分支、
     * 遍历不覆盖这些线，收尾也不校验这些线的车站完整性。仅全图遍历（{@code walkAll}）有意义。
     */
    private final Set<String> ignoreLineIds;

    /**
     * @param plugin        插件实例
     * @param sender        发起遍历者
     * @param targetLineIds 增量遍历目标线路 id 集合；空 / null 表示全图遍历
     */
    public GeoTraversalTask(BiliCraftTicketSystem plugin, CommandSender sender, Set<String> targetLineIds) {
        this(plugin, sender, targetLineIds, Collections.emptySet());
    }

    /**
     * @param plugin        插件实例
     * @param sender        发起遍历者
     * @param targetLineIds 增量遍历目标线路 id 集合；空 / null 表示全图遍历
     * @param ignoreLineIds 忽略的线路 id 集合（见 {@link #ignoreLineIds}）；空 / null 表示不忽略。仅全图遍历有意义。
     */
    public GeoTraversalTask(BiliCraftTicketSystem plugin, CommandSender sender, Set<String> targetLineIds, Set<String> ignoreLineIds) {
        this.plugin = plugin;
        this.sender = sender;
        this.targetLineIds = targetLineIds == null ? Collections.emptySet() : new LinkedHashSet<>(targetLineIds);
        this.ignoreLineIds = ignoreLineIds == null ? Collections.emptySet() : new LinkedHashSet<>(ignoreLineIds);
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
                    CommonUtils.mmStr2Component(msg("traversal-no-running", "<red>当前没有正在进行的构建铁路图任务"))));
            return;
        }
        walk.abort("用户 " + sender.getName() + " 手动停止了构建铁路图任务");
        sender.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(msg("traversal-stop-requested", "<yellow>已请求停止当前构建铁路图任务..."))));
    }

    /**
     * 遍历所有已登记线路起点并分文件保存。
     * <p>
     * 先做单运行与冷却校验：已有任务在跑或仍在冷却期则直接拒绝并提示，不占用单运行锁。
     * 持 {@link #PERM_BYPASS_COOLDOWN} 权限者绕过冷却，且其执行结束不刷新冷却时间。
     */
    public void runAll() {
        start();
    }

    /**
     * 只遍历一条线路及与其直接相连的联络线，增量更新 {@code <lineId>.geojson} 与合并 {@code contact.geojson}。
     * <p>
     * 与 {@link #runAll} 共用单运行锁 / 冷却 / 向导校验 / 分片骨架，差异在：只 seed 目标线登记起点、
     * 出向 scope 限定为 {@code {targetLineId, contact}}、收尾只写两个文件（见 {@link #finalizeAndSave}）。
     * 需在构造时传入 {@code targetLineId}。
     */
    public void runLine() {
        start();
    }

    /**
     * 全图 / 单线遍历共用的启动骨架：向导校验 + 冷却校验（不抢锁）+ 抢单运行锁，随后建 walk 并启动分片遍历。
     * scope（是否单线）由 {@link #targetLineIds} 决定。
     * <p>
     * 冷却按模式独立：walk 与 walkAll 各查各的冷却值与上次完成时间（见 {@link #lastWalkFinishTime}）；
     * 但单运行锁 {@link #RUNNING} 是共用的——任一遍历进行中，另一个都不可发起。
     */
    private void start() {
        boolean bypassCooldown = sender != null && sender.hasPermission(PERM_BYPASS_COOLDOWN);
        boolean isWalk = !targetLineIds.isEmpty();

        // 有玩家正在进行线路/铁路系统配置向导时不遍历：配置可能改到一半，遍历结果会不一致
        if (WizardManager.hasAnyActive()) {
            sendConfigMessage(msg("traversal-wizard-active",
                    "<red>有玩家正在进行线路/铁路系统配置，请等其完成后再发起构建铁路图任务"));
            return;
        }

        // 冷却校验（不抢锁，按模式取各自的冷却值与上次完成时间）；有 bypass 权限则跳过
        int cooldownSec = isWalk ? MapConfig.getWalkCooldownSeconds() : MapConfig.getTraversalCooldownSeconds();
        long lastFinish = isWalk ? lastWalkFinishTime : lastFinishTime;
        long remainMs = lastFinish + cooldownSec * 1000L - System.currentTimeMillis();
        if (!bypassCooldown && cooldownSec > 0 && lastFinish > 0 && remainMs > 0) {
            sendConfigMessage(msg("traversal-cooling-down", "<red>构建铁路图任务正在冷却中，请 %d 秒后再试")
                    .formatted((remainMs + 999) / 1000));
            return;
        }
        // 抢单运行锁：已有任务在跑则拒绝（walk / walkAll 共用，互相排斥）
        if (!RUNNING.compareAndSet(false, true)) {
            sendConfigMessage(msg("traversal-already-running",
                    "<red>已有一个构建铁路图任务正在进行，请等待其完成，或使用 /railgeo stopWalk 停止"));
            return;
        }

        // 增量遍历：出向只跟进目标线集合 + 联络线；全图遍历 scope 为 null（不过滤）
        Set<String> scope = null;
        if (!targetLineIds.isEmpty()) {
            scope = new LinkedHashSet<>(targetLineIds);
            scope.add(RailwaySystemConfig.CONTACT_ID);
        }

        GeoTraversalLogger log = new GeoTraversalLogger(plugin, sender);
        GraphWalk walk = new GraphWalk(new TraversalCollector(), log, new HashSet<>(),
                MapConfig.getTraversalMaxTotalNodes(), MapConfig.getTraversalMaxEdgesPerWalk(), scope, ignoreLineIds,
                MapConfig.getTraversalCoasterSampleStep());
        runningWalk = walk;
        // 分片遍历期间主线程被一段段占用，异步线程只读 walk 的计数器汇报进度。
        BukkitTask progressTask = startProgressFeedback(walk, log);

        // 分片遍历：在主线程上每 tick 只展开有限段，处理完即让出主线程，避免一次性展开整张图卡死服务器。
        // 全程在主线程执行，TC 实时寻路 / 区块访问均线程安全（不会出现 chunk area 未及时更新的告警）。
        startSlicedTraversal(log, walk, bypassCooldown, progressTask);
    }

    /**
     * 收尾：取消进度反馈、关闭日志、刷新冷却、释放单运行锁。所有退出路径（正常结束 / 中止 / 异常）都经此。
     *
     * @param log            日志
     * @param progressTask   进度反馈任务（可为 null）
     * @param bypassCooldown 是否绕过冷却（绕过者结束不刷新冷却时间）
     */
    private void finishTraversal(GeoTraversalLogger log, BukkitTask progressTask, boolean bypassCooldown) {
        if (progressTask != null) {
            progressTask.cancel();
        }
        log.close();
        // bypass 执行不刷新冷却时间；否则只刷新本次模式对应的冷却（walk 与 walkAll 独立计时）
        if (!bypassCooldown) {
            if (targetLineIds.isEmpty()) {
                lastFinishTime = System.currentTimeMillis();
            } else {
                lastWalkFinishTime = System.currentTimeMillis();
            }
        }
        runningWalk = null;
        RUNNING.set(false);
    }

    /**
     * 在主线程把整次遍历分片到多个 tick 执行：
     * <ol>
     *   <li>先读取并 seed 所有登记起点（读起点铁轨方块，需主线程）；</li>
     *   <li>用定时任务每 {@link MapConfig#getTraversalIntervalTicks()} tick 调一次 {@link GraphWalk#stepBatch}，每批最多展开
     *       {@link MapConfig#getTraversalEdgesPerInterval()} 段，队列空（或中止）后停止；</li>
     *   <li>展开结束后做车站校验、层级计算、写文件、重载配置。</li>
     * </ol>
     * 每段的行走矿车都在单个 tick 内生成并销毁，不跨 tick 持有，避免 keep-loaded 区块区域与遍历推进
     * 抢状态导致的告警。
     *
     * @param log            日志
     * @param walk           遍历驱动器
     * @param bypassCooldown 是否绕过冷却（透传给收尾）
     * @param progressTask   进度反馈任务（透传给收尾）
     */
    private void startSlicedTraversal(GeoTraversalLogger log, GraphWalk walk, boolean bypassCooldown, BukkitTask progressTask) {
        // 起点 seed 与后续每 tick 的展开都必须在主线程。先在一个主线程任务里 seed，再启动分片定时任务。
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!seedStarts(log, walk)) {
                    // seed 阶段已中止（无起点 / 起点无轨道）或没有可展开内容
                    if (walk.isAborted()) {
                        log.message("构建铁路图任务已中止，未写入任何文件：" + walk.getAbortReason(), NamedTextColor.RED);
                    }
                    finishTraversal(log, progressTask, bypassCooldown);
                    return;
                }
            } catch (Exception e) {
                log.error("遍历起点初始化失败", e);
                log.message("构建铁路图任务失败：" + e, NamedTextColor.RED);
                finishTraversal(log, progressTask, bypassCooldown);
                return;
            }

            int intervalTicks = MapConfig.getTraversalIntervalTicks();
            int edgesPerInterval = MapConfig.getTraversalEdgesPerInterval();
            // 每 intervalTicks 展开一批；展开完毕（或中止）后取消自身并切到收尾流程。runTaskTimer 保证全程在主线程。
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (walk.hasPending()) {
                        try {
                            walk.stepBatch(edgesPerInterval);
                        } catch (Exception e) {
                            log.error("遍历展开失败", e);
                            log.message("构建铁路图任务失败：" + e, NamedTextColor.RED);
                            cancel();
                            finishTraversal(log, progressTask, bypassCooldown);
                            return;
                        }
                        // 本 tick 展开后队列可能仍有剩余，等下一 tick 继续；中止则下次进入收尾分支
                        if (walk.hasPending()) {
                            return;
                        }
                    }
                    // 队列已空或已中止：停止分片。收尾（校验 / 层级计算 / 写文件）为 CPU/磁盘密集且不触碰
                    // Bukkit API，放异步线程执行，避免 O(n²) 叠层计算卡主线程；只有最后的 loadConfig / 推送
                    // 快照回主线程。finalizeAndSave 自行负责所有退出路径上调用 finishTraversal。
                    cancel();
                    finalizeAndSave(log, walk, progressTask, bypassCooldown);
                }
            }.runTaskTimer(plugin, 1L, intervalTicks);
        });
    }

    /**
     * 读取所有登记起点并 seed 进遍历队列（读起点铁轨方块，须主线程）。
     * 任一起点坐标处无轨道则中止整次遍历。
     *
     * @param log  日志
     * @param walk 遍历驱动器
     * @return 成功 seed 了至少一个起点返回 true；无起点 / 已中止返回 false
     */
    private boolean seedStarts(GeoTraversalLogger log, GraphWalk walk) {
        List<GeoNodeLoc> starts;
        if (!targetLineIds.isEmpty()) {
            // 增量遍历：只 seed 目标线集合的登记起点；任一线未登记起点则中止
            starts = new ArrayList<>();
            for (String lineId : targetLineIds) {
                GeoNodeLoc start = plugin.getGeoDatabaseManager().getGeoNodeLoc(lineId);
                if (start == null) {
                    log.message("线路 [%s] 没有已登记的遍历起点，请先用 /railgeo setStartPos %s 登记"
                            .formatted(lineId, lineId), NamedTextColor.RED);
                    return false;
                }
                starts.add(start);
            }
            log.message("开始构建线路 [%s] 及其联络线...".formatted(String.join(", ", targetLineIds)),
                    NamedTextColor.DARK_AQUA);
        } else {
            starts = plugin.getGeoDatabaseManager().getAllGeoNodeLoc();
            if (starts.isEmpty()) {
                log.message("没有已登记的线路起点，请先用 /railgeo setStartPos <lineId> 登记", NamedTextColor.RED);
                return false;
            }
            log.message("开始构建铁路图，共 " + starts.size() + " 个登记起点...", NamedTextColor.DARK_AQUA);
        }
        for (GeoNodeLoc start : starts) {
            // --ignore 的线路不从其登记起点 seed，彻底不遍历该线
            if (ignoreLineIds.contains(start.getLineId())) {
                log.info("线路 " + start.getLineId() + " 在忽略名单中，跳过其起点");
                continue;
            }
            Block startRail = resolveStartRail(start.getStartLocation());
            if (startRail == null) {
                walk.abort("起点 " + start.getLineId() + " 坐标处没有铁轨（坐标 " + start.getStartLocation()
                        + "）。请检查该线路登记的起点是否仍在轨道上，如果该起点已废弃，请使用/railgeo delStartPos <lineId>删除");
                return false;
            }
            startLineIds.add(start.getLineId());
            log.info("从起点 " + start.getLineId() + " @ " + start.getStartLocation() + " 展开");
            walk.seed(start.getLineId(), startRail, start.getStartDirection());
        }
        return true;
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
     * 启动进度反馈：每隔 {@code progress-interval-seconds} 秒把当前进度发给发起者，
     * 让其知道遍历没有卡住。间隔 {@code <=0} 则不反馈，返回 null。
     * <p>
     * 发起者非控制台时，进度同时打到控制台后台，方便管理员观察。
     *
     * @param walk 遍历驱动器
     * @param log  日志（同时写入日志文件）
     * @return 反馈定时任务（遍历结束须取消）；不反馈时为 null
     */
    private BukkitTask startProgressFeedback(GraphWalk walk, GeoTraversalLogger log) {
        int intervalSec = MapConfig.getTraversalProgressIntervalSeconds();
        if (intervalSec <= 0) {
            return null;
        }
        long periodTicks = intervalSec * 20L;
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String text = "正在构建铁路图，当前已构建 " + walk.getCollector().totalNodes() + " 个节点，" +
                    walk.getCollector().totalEdges() + " 条边，" +
                    "%.2fkm ...".formatted(walk.getCollector().totalDistance());
            log.message(text, NamedTextColor.GRAY);
        }, periodTicks, periodTicks);
    }

    /**
     * 分片展开结束后的收尾：校验车站、计算层级、写文件、重载配置。
     * 若遍历已中止（达到段数上限 / 用户停止 / 起点异常），则只反馈中止原因，不写任何文件。
     * <p>
     * 由分片定时任务在主线程、队列排空后触发。校验 / 层级计算 / 写文件为纯计算与磁盘 I/O，
     * 不触碰 Bukkit API，放到异步线程执行（这是遍历卡服的主因）；仅最后的 {@code loadConfig} 与
     * 快照推送切回主线程。<b>本方法自行负责所有退出路径上调用 {@link #finishTraversal}</b>
     * （释放单运行锁、刷新冷却），调用方不再兜底。
     *
     * @param log            日志
     * @param walk           遍历驱动器
     * @param progressTask   进度反馈任务（透传给收尾）
     * @param bypassCooldown 是否绕过冷却（透传给收尾）
     */
    private void finalizeAndSave(GeoTraversalLogger log, GraphWalk walk, BukkitTask progressTask, boolean bypassCooldown) {
        if (walk.isAborted()) {
            log.message("构建铁路图任务已中止，未写入任何文件：" + walk.getAbortReason(), NamedTextColor.RED);
            finishTraversal(log, progressTask, bypassCooldown);
            return;
        }

        // 校验 + 层级计算 + 写文件：纯计算与磁盘 I/O，不触碰 Bukkit API，放异步线程避免卡主线程。
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int files;
            try {
                progressTask.cancel();
                log.message("构建铁路图任务已完成，校验车站和配置是否对应...", NamedTextColor.DARK_AQUA);

                TraversalCollector collector = walk.getCollector();
                // 按线校验：单线遍历只校验目标线；全图遍历覆盖所有起点登记线 + 遍历中实际到达过车站的线。
                Map<String, Set<String>> byLine = walk.getVisitedStationsByLine();
                Set<String> linesToCheck = new LinkedHashSet<>();
                if (!targetLineIds.isEmpty()) {
                    linesToCheck.addAll(targetLineIds);
                } else {
                    linesToCheck.addAll(startLineIds);
                    linesToCheck.addAll(byLine.keySet());
                }
                // --ignore 的线路不校验车站完整性（其轨道本就未遍历，缺站是预期行为）
                linesToCheck.removeAll(ignoreLineIds);
                boolean vaild = true;
                for (String lineId : linesToCheck) {
                    if (!validateStationOrder(lineId, byLine.getOrDefault(lineId, Collections.emptySet()), walk, log)) {
                        vaild = false;
                    }
                }

                if (!vaild) {
                    walk.abort("线路车站校验失败");
                    log.message("构建铁路图任务已中止，未写入任何文件：" + walk.getAbortReason(), NamedTextColor.RED);
                    finishTraversal(log, progressTask, bypassCooldown);
                    return;
                }

                log.message("验证完成，开始计算LineString层级...", NamedTextColor.DARK_AQUA);
                if (!targetLineIds.isEmpty()) {
                    // 单线增量：先写出本次改动的 geojson，清理线路配置文件后，再读回全部 geojson 全局重算 layer 并写回。
                    // 这样 layer 与全图 walkAll 一致（跨全部线路），不受"只把其它文件当固定障碍"的局限。
                    files = saveLineIncremental(walk, log);
                    int orphans = cleanOrphanFiles(log);
                    if (orphans > 0) {
                        log.message("清理了 %d 个线路配置文件（线路配置已删除）".formatted(orphans), NamedTextColor.YELLOW);
                    }
                    int relayered = recomputeAllLayers(log);
                    log.info("全局重算 layer：更新了 " + relayered + " 个文件的层级");
                } else {
                    // 全图遍历：所有区间收集完毕后，按空间交叉关系全局重算 layer（高架压平交）
                    collector.assignLayers();
                    files = saveAll(collector, log);
                    int orphans = cleanOrphanFiles(log);
                    if (orphans > 0) {
                        log.message("清理了 %d 个线路配置文件（线路配置已删除）".formatted(orphans), NamedTextColor.YELLOW);
                    }
                }
                log.message("构建铁路图任务已完成：共写入 %d 个文件".formatted(files), NamedTextColor.GREEN);
            } catch (Exception e) {
                log.error("构建铁路图任务收尾失败", e);
                log.message("构建铁路图任务失败：" + e, NamedTextColor.RED);
                finishTraversal(log, progressTask, bypassCooldown);
                return;
            }

            // 回主线程：重载配置（会触碰 Bukkit / 各配置单例）+ 推送快照，随后收尾。
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    log.message("重载配置文件...", NamedTextColor.DARK_AQUA);
                    plugin.loadConfig(null);
                    log.message("重载配置完成", NamedTextColor.GREEN);

                    // 遍历产出新 geojson，若已连后端则推送 geo 快照
                    if (plugin.getWebLink() != null && plugin.getWebLink().getClient().isConnected()) {
                        plugin.getWebLink().getSnapshotPublisher().publishGeo();
                        log.message("已向线路图后端推送 geojson 快照", NamedTextColor.GREEN);
                    }
                } catch (Exception e) {
                    log.error("重载配置失败", e);
                    log.message("重载配置失败：" + e, NamedTextColor.RED);
                } finally {
                    finishTraversal(log, progressTask, bypassCooldown);
                }
            });
        });
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

        boolean vaild = true;
        Set<String> expected = new LinkedHashSet<>(info.getBossbarStations());
        StringBuilder invaildStations = new StringBuilder();
        for (String want : expected) {
            if (!visited.contains(want)) {
                invaildStations.append("\"").append(want).append("\" ");
                vaild = false;
            }
        }
        if (!invaildStations.isEmpty()) {
            log.message("线路 " + lineId + " 校验：配置车站 " + invaildStations + "未在遍历中到达（轨道未铺设或道岔未声明该线？）", NamedTextColor.RED, Level.SEVERE);
        }

        invaildStations = new StringBuilder();
        for (String got : visited) {
            if (!expected.contains(got)) {
                invaildStations.append("\"").append(got).append("\" ");
                vaild = false;
            }
        }
        if (!invaildStations.isEmpty()) {
            log.message("线路 " + lineId + " 校验：到达了配置外的车站 " + invaildStations + "（站名写错或控制牌归属线路有误？）", NamedTextColor.RED, Level.SEVERE);
        }
        return vaild;
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
     * 清理无用 geojson 文件：删除 geodata 目录下「文件名（lineId）在当前线路配置中已不存在」的
     * {@code <lineId>.geojson}。
     * <p>
     * 玩家用 ticketconfig 删除某条线路后，其旧 {@code <lineId>.geojson} 不会被 {@link #saveAll} /
     * {@link #saveLineIncremental} 覆盖（本次遍历不产出该线），会残留成线路配置文件，前端仍会加载显示一条已经
     * 不存在的线。故 walk / walkAll 收尾统一按<b>当前线路配置</b>核对：文件名不是当前任何线路 id、且不是
     * {@code contact.geojson}（联络线聚合文件，无对应单线配置，恒保留）的，删除。
     *
     * @param log 日志
     * @return 删除的线路配置文件数
     */
    private int cleanOrphanFiles(GeoTraversalLogger log) {
        File dir = plugin.getGeodataDir();
        File[] geoFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".geojson"));
        if (geoFiles == null) {
            return 0;
        }
        int deleted = 0;
        for (File f : geoFiles) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName();
            String fileKey = name.substring(0, name.length() - ".geojson".length());
            // contact.geojson 是联络线聚合文件，无对应单线配置，恒保留
            if (RailwaySystemConfig.CONTACT_ID.equals(fileKey)) {
                continue;
            }
            Set<String> lineIds = plugin.getGeoDatabaseManager().getAllGeoNodeLoc()
                    .stream().map(GeoNodeLoc::getLineId).collect(Collectors.toSet());
            if (lineIds.contains(fileKey)) {
                continue;
            }
            if (f.delete()) {
                deleted++;
                log.info("清理线路配置文件：" + name + "（线路配置已不存在）");
            } else {
                log.error("清理线路配置文件失败：" + name, null);
            }
        }
        return deleted;
    }

    /**
     * 把收集器中的每个文件分组写成 geojson。
     * <p>
     * 普通 {@code walkAll}（无 {@code --ignore}）是全图权威重建，直接整体覆盖每个文件。
     * 带 {@code --ignore} 时，被忽略线的轨道本次不遍历，凡「起点节点落在被忽略线上、本次走不到」的旧边
     * （如外线经被忽略线汇入本线的入边）会被整体覆盖误删，故对每个文件读回旧区间、保留本次到不了的边
     * （见 {@link #preserveUnreachedEdges}），并从旧文件补齐这些边引用到的节点。
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
        boolean preserveUnreached = !ignoreLineIds.isEmpty();
        Set<String> visitedNodeIds = preserveUnreached ? collector.nodeIds() : Collections.emptySet();
        GeojsonReader reader = preserveUnreached ? new GeojsonReader() : null;
        int files = 0;
        for (String fileKey : collector.fileKeys()) {
            List<RailEdge> edges = new ArrayList<>(collector.edgesOf(fileKey));
            List<RailNode> nodes;
            if (preserveUnreached) {
                // --ignore：保留旧文件里起点节点本次没走到的边，并补齐其引用到的节点
                GeojsonReader.Result old = reader.read(new File(dir, fileKey + ".geojson"));
                mergeOldEnterFaces(edges, old.edges);
                edges.addAll(preserveUnreachedEdges(old.edges, edges, visitedNodeIds));
                Map<String, RailNode> nodePool = new LinkedHashMap<>(old.nodes);
                collector.nodesOf(fileKey).forEach(n -> mergeNodeInto(nodePool, n));
                nodes = referencedNodes(edges, nodePool);
            } else {
                nodes = collector.nodesOf(fileKey);
            }
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
     * 增量保存：为每条目标线写 {@code <lineId>.geojson}（整体覆盖）、合并写 {@code contact.geojson}，
     * 其它线路文件不动。本方法<b>不算 layer</b>——写出几何后由 {@link #recomputeAllLayers} 读回全部
     * geojson 全局重算 layer 并写回，与全图 {@code walkAll} 的层级口径一致。
     * <p>
     * 联络线合并规则：按 {@link RailEdge#getOwnerLineId() owner} 精确删除旧 {@code contact.geojson} 里
     * 「归属本次目标线」的段（本次会重新走出），保留其它线拥有的段，再并入本次新走出的联络线段。
     * 同一物理联络线从两条线的道岔出发会产出方向相反、owner 不同的两条边，故只按 owner 删除，绝不误删
     * 对端线路的反向段（旧的按 from 节点归属判定会误删对端段，导致联络线断开）。owner 为 null 的旧段
     * （本字段引入前产出）保守保留，一次 {@code walkAll} 全量重建即可补齐。
     *
     * @param walk 本次增量遍历驱动器（提供结果收集器与起点首节点 id）
     * @param log  日志
     * @return 写出的文件数
     */
    private int saveLineIncremental(GraphWalk walk, GeoTraversalLogger log) {
        TraversalCollector collector = walk.getCollector();
        File dir = plugin.getGeodataDir();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        GeojsonReader reader = new GeojsonReader();

        // 本次遍历实际到达过的节点：据此保留旧文件里「起点节点本次没走到」的区间（见 preserveUnreachedEdges）
        Set<String> visitedNodeIds = collector.nodeIds();

        // 逐条目标线：读旧文件、保留本次结构上到不了的旧区间、算本线待写区间与节点表
        Map<String, List<RailEdge>> targetEdgesByLine = new LinkedHashMap<>();
        Map<String, Map<String, RailNode>> targetNodesByLine = new LinkedHashMap<>();
        for (String lineId : targetLineIds) {
            GeojsonReader.Result oldTarget = reader.read(new File(dir, lineId + ".geojson"));
            List<RailEdge> edges = new ArrayList<>(collector.edgesOf(lineId));
            // 先并回旧边的入向门控到达面（外线汇入本线起点道岔贡献、本次 scope 走不到），再保留本次结构上到不了的整条旧边
            mergeOldEnterFaces(edges, oldTarget.edges);
            edges.addAll(preserveExternalIngressEdges(oldTarget.edges, edges, visitedNodeIds,
                    lineIdsByNode(oldTarget.nodes), lineId));
            targetEdgesByLine.put(lineId, edges);

            Map<String, RailNode> nodes = new LinkedHashMap<>(oldTarget.nodes);
            collector.nodesOf(lineId).forEach(n -> mergeNodeInto(nodes, n));
            targetNodesByLine.put(lineId, nodes);
        }

        // 合并联络线段：按 owner 精确删除——只丢弃「归属本次目标线」的旧联络线段（本次会重新走出），
        // 保留其它线拥有的段。这样才不会误删同一物理联络线上属于对端线路的反向段（否则联络线断开）。
        // owner 为 null 的旧段（本字段引入前产出）保守保留，一次 walkAll 全量重建即可补齐 owner。
        GeojsonReader.Result oldContact = reader.read(new File(dir, RailwaySystemConfig.CONTACT_ID + ".geojson"));
        Map<String, RailEdge> mergedContact = new LinkedHashMap<>();
        int preservedOthers = 0;
        for (RailEdge e : oldContact.edges) {
            String owner = e.getOwnerLineId();
            if (owner != null && targetLineIds.contains(owner)) {
                continue; // 归属本次目标线：丢弃旧段，由本次遍历重新走出
            }
            mergedContact.put(e.getId(), e);
            preservedOthers++;
        }
        List<RailEdge> newContact = collector.edgesOf(RailwaySystemConfig.CONTACT_ID);
        // 本次新走出的联络线段也并回旧同 id 段的入向门控到达面（同理：外线在道岔的到达面本次 scope 走不到）
        mergeOldEnterFaces(newContact, oldContact.edges);
        for (RailEdge e : newContact) {
            mergedContact.put(e.getId(), e);
        }
        log.info("合并 contact.geojson：保留其它线联络线段 " + preservedOthers + " 条，本次新增 "
                + collector.edgesOf(RailwaySystemConfig.CONTACT_ID).size() + " 条");
        List<RailEdge> contactEdges = new ArrayList<>(mergedContact.values());

        // layer 不在此处计算：本方法只负责写出本次改动的几何，layer 由随后的 recomputeAllLayers
        // 读回全部 geojson 全局重算并写回（见 finalizeAndSave）。

        // 联络线节点表：旧段端点节点 + 本次新联络线节点（按 id 合并 lineIds，不覆盖旧节点累积的其它线归属）
        Map<String, RailNode> contactNodes = new LinkedHashMap<>(oldContact.nodes);
        collector.nodesOf(RailwaySystemConfig.CONTACT_ID).forEach(n -> mergeNodeInto(contactNodes, n));

        int files = 0;
        for (String lineId : targetLineIds) {
            List<RailEdge> edges = targetEdgesByLine.get(lineId);
            files += writeFile(dir, lineId, referencedNodes(edges, targetNodesByLine.get(lineId)), edges, log);
        }
        files += writeFile(dir, RailwaySystemConfig.CONTACT_ID, referencedNodes(contactEdges, contactNodes), contactEdges, log);
        return files;
    }

    /**
     * 从某文件的旧区间里保留「起点节点本次遍历没走到」的边，避免整体覆盖误删本次结构上不可能重现的边。
     * <p>
     * 增量遍历（{@code /railgeo walk}，scope 限定 {@code {目标线, contact}}）与 {@code walkAll --ignore}
     * （跳过忽略线分支）都会使矿车走不到某些节点：
     * <ul>
     *   <li><b>外线汇入本线</b>：如 L2 的道岔 N1 上并入 L1 的出向声明 lineId=L1，边 {@code N1→N2} 归入
     *       {@code L1.geojson}，但只有遍历 L2 到达 N1 才会走出它。{@code walk L1} 的 scope 到不了 N1、
     *       {@code walkAll --ignore L2} 跳过 L2 分支也到不了 N1，整体覆盖 {@code L1.geojson} 就会丢掉这条边，
     *       导致 {@code N1→N2} 断链。</li>
     *   <li><b>起点首节点入边</b>：起点登记在轨道中段，seed 首段无「上一节点」，本次不会给首节点记入边；
     *       其前驱节点本次通常也走不到，故其旧入边同样由本规则保留。</li>
     * </ul>
     * 判据用「起点节点 ∉ 本次到达节点集」而非「本线没走出该起点的新边」：只有<b>完全没走到</b>该起点时才保留。
     * 若该起点本次其实走到了（如轨道改造后 {@code N1→N2} 被真正删除），{@code visitedNodeIds} 含该起点，
     * 不保留，让本次遍历的新结果权威——从而只补「结构上到不了」的边，不会把已删除的旧边错误复活。
     * <p>
     * 一次完整的 {@code walkAll}（不带 {@code --ignore}）全量重建、不调用本方法，可纠正任何被保守保留的陈旧边。
     *
     * @param oldEdges       旧文件解析出的区间
     * @param currentEdges   本次已收集的该文件区间（用于按 edge id 去重，不重复保留本次已产出的边）
     * @param visitedNodeIds 本次遍历实际到达过的节点 id 集（见 {@link TraversalCollector#nodeIds()}）
     * @return 需保留的旧区间（可能为空）
     */
    private List<RailEdge> preserveUnreachedEdges(List<RailEdge> oldEdges, List<RailEdge> currentEdges,
                                                  Set<String> visitedNodeIds) {
        Set<String> currentIds = new HashSet<>();
        for (RailEdge e : currentEdges) {
            currentIds.add(e.getId());
        }
        List<RailEdge> preserved = new ArrayList<>();
        for (RailEdge e : oldEdges) {
            // 起点节点本次走到了：本次结果对该起点的出边权威，不保留旧边（避免复活已删除的边）
            if (visitedNodeIds.contains(e.getFromNodeId())) {
                continue;
            }
            // 本次已产出同一条边则无需重复保留
            if (currentIds.contains(e.getId())) {
                continue;
            }
            preserved.add(e);
        }
        return preserved;
    }

    /**
     * Incremental {@code /railgeo walk <lineId>} treats the walked target line as authoritative.
     * Stale target-line segments whose start node belongs to the target line are dropped, even if
     * that old node was not reached this time. The only old target-line segments kept are external
     * ingress edges: an old edge in the target file whose start node metadata does not contain the
     * target line, which means the edge was produced by another line flowing into this line and the
     * current scoped walk cannot prove it obsolete.
     */
    static List<RailEdge> preserveExternalIngressEdges(List<RailEdge> oldEdges, List<RailEdge> currentEdges,
                                                       Set<String> visitedNodeIds,
                                                       Map<String, Set<String>> oldLineIdsByNode,
                                                       String targetLineId) {
        Set<String> currentIds = new HashSet<>();
        for (RailEdge e : currentEdges) {
            currentIds.add(e.getId());
        }
        List<RailEdge> preserved = new ArrayList<>();
        for (RailEdge e : oldEdges) {
            if (!Objects.equals(targetLineId, e.getLineId())) {
                continue;
            }
            if (visitedNodeIds.contains(e.getFromNodeId())) {
                continue;
            }
            if (currentIds.contains(e.getId())) {
                continue;
            }
            Set<String> fromLineIds = oldLineIdsByNode == null
                    ? Collections.emptySet()
                    : oldLineIdsByNode.getOrDefault(e.getFromNodeId(), Collections.emptySet());
            if (fromLineIds.contains(targetLineId)) {
                continue;
            }
            if (fromLineIds.isEmpty()) {
                continue;
            }
            preserved.add(e);
        }
        return preserved;
    }

    private static Map<String, Set<String>> lineIdsByNode(Map<String, RailNode> nodes) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, RailNode> entry : nodes.entrySet()) {
            result.put(entry.getKey(), new LinkedHashSet<>(entry.getValue().getLineIds()));
        }
        return result;
    }

    /**
     * 把旧文件里同一条边（同 id）的到达面 {@link RailEdge#getEnterFacesFrom() enterFrom} 并入本次新走出的边。
     * <p>
     * {@code enterFrom} 是运行时寻路的<b>入向门控白名单</b>（见 {@code GeoRouteEngine#enterFaceAllows}）：
     * 只有到达起点道岔的入边到达面 ∈ 本集合，才允许接本段出边。同一条出向边可被<b>多个不同到达面</b>产出
     * ——除本线沿途续行外，还包括<b>外线经联络线汇入本线起点道岔</b>的到达面（如 pr-cw 经 contact 汇入
     * pr-s1 的道岔，给 pr-s1 出向边贡献一个额外到达面）。
     * <p>
     * 增量遍历（{@code /railgeo walk}，scope 限定 {@code {目标线, contact}}）从目标线起点出发，<b>走不到</b>
     * 那条外线的道岔，故本次只会记到「本线续行」的到达面，外线汇入贡献的到达面丢失。而该出向边的起点节点
     * 本次确实走到了（本线到达过），{@link #preserveUnreachedEdges} 不会保留旧边，整体覆盖就会把外线到达面
     * 抹掉——运行时外线列车到达该道岔后被门控拒绝、无法接上本线，表现为「汇入本线的线路断开」。
     * <p>
     * 故对本次已走出、且旧文件存在同 id 的边，把旧边到达面并入本次新边：本次能走到的到达面权威更新，
     * 本次结构上到不了（外线汇入）的到达面从旧边补回。多出的陈旧到达面无害（没有列车真的从该向到达就不会
     * 触发门控），一次完整 {@code walkAll} 全量重建即可校正。
     *
     * @param currentEdges 本次已收集的该文件区间（原地修改：并入旧到达面）
     * @param oldEdges     旧文件解析出的区间
     */
    private void mergeOldEnterFaces(List<RailEdge> currentEdges, List<RailEdge> oldEdges) {
        Map<String, RailEdge> oldById = new HashMap<>();
        for (RailEdge e : oldEdges) {
            oldById.put(e.getId(), e);
        }
        for (RailEdge cur : currentEdges) {
            RailEdge old = oldById.get(cur.getId());
            if (old != null) {
                old.getEnterFacesFrom().forEach(cur::addEnterFaceFrom);
            }
        }
    }

    /**
     * 把新节点并入节点池：同 id 已有旧节点时，<b>并集累积</b>其 {@code lineIds} / {@code railwaySystemIds}
     * 而非整体覆盖。
     * <p>
     * 一个道岔 / 车站可被多条线经过，其 {@code lineIds} 是遍历过程中<b>跨线累积</b>的（如某道岔同时属
     * {@code pr-s1} 与 {@code contact}）。增量遍历只 seed 目标线、scope 受限，本次到达该节点时只累积到目标线一侧的
     * lineId，直接覆盖旧节点会抹掉其它线贡献的 lineId，使写出的节点 {@code lineIds} 残缺（前端图例 / 归属显示
     * 劣化）。故按 id 合并：先保留旧节点的累积 lineId，再并入本次新到达贡献的 lineId。
     * <p>
     * 运行时 {@code GeoGraphLoader} 会跨全部 geojson 按 id 再累积一次 lineId，故此劣化通常不影响寻路；此处
     * 合并是为保持单文件节点元数据完整、与全量 {@code walkAll} 口径一致。
     *
     * @param pool    节点池（原地修改：旧节点已在池中）
     * @param newNode 本次新到达的节点
     */
    private void mergeNodeInto(Map<String, RailNode> pool, RailNode newNode) {
        RailNode old = pool.get(newNode.getId());
        if (old != null) {
            old.getLineIds().forEach(newNode::addLineId);
            old.getRailwaySystemIds().forEach(newNode::addRailwaySystemId);
        }
        pool.put(newNode.getId(), newNode);
    }

    /**
     * 读回 geodata 目录下全部 geojson 的区间，按空间交叉关系<b>全局重算 layer</b>，把新层级写回各文件。
     * <p>
     * 单线增量遍历（{@code /railgeo walk}）先写出改动的几何（{@link #saveLineIncremental}），再调用本方法：
     * 只有跨全部线路一起算，才能保证新走出的高架 / 平交与既有线路的叠压关系正确、且联络线总在下层。
     * <p>
     * 为避免丢失节点（其它文件的世界可能未加载，经 {@link GeojsonReader#read} 会丢节点），本方法在
     * <b>原始 {@link FeatureCollection} 层面</b>只改 LineString 的 {@code layer} 属性再原样重写，节点与其它
     * 属性一律保留。只重写 layer 确有变化的文件，减少无谓磁盘写入。
     *
     * @param log 日志
     * @return 因 layer 变化而被重写的文件数
     */
    private int recomputeAllLayers(GeoTraversalLogger log) {
        File dir = plugin.getGeodataDir();
        File[] geoFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".geojson"));
        if (geoFiles == null || geoFiles.length == 0) {
            return 0;
        }
        ObjectMapper mapper = new ObjectMapper();
        GeojsonReader reader = new GeojsonReader();
        // 保留每个文件的原始 FeatureCollection（无损重写用）+ 提取其区间（重算 layer 用）
        Map<File, FeatureCollection> fcByFile = new LinkedHashMap<>();
        List<RailEdge> allEdges = new ArrayList<>();
        for (File f : geoFiles) {
            FeatureCollection fc;
            try {
                fc = mapper.readValue(f, FeatureCollection.class);
            } catch (Exception e) {
                log.error("重算 layer：读取失败，跳过 " + f.getName(), e);
                continue;
            }
            fcByFile.put(f, fc);
            allEdges.addAll(reader.readEdges(fc));
        }
        if (allEdges.isEmpty()) {
            return 0;
        }
        // 全局重算：assign 会把每条边 layer 归零后按空间交叉重新分层
        LayerAssigner.assign(allEdges);
        // 边 id -> 新 layer
        Map<String, Integer> layerById = new HashMap<>();
        for (RailEdge e : allEdges) {
            layerById.put(e.getId(), e.getLayer());
        }
        int rewritten = 0;
        for (Map.Entry<File, FeatureCollection> entry : fcByFile.entrySet()) {
            if (applyLayers(entry.getValue(), layerById)) {
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(entry.getKey(), entry.getValue());
                    rewritten++;
                } catch (Exception e) {
                    log.error("重算 layer：写回失败 " + entry.getKey().getName(), e);
                }
            }
        }
        return rewritten;
    }

    /**
     * 把新算出的 layer 写回一个 FeatureCollection 的各 LineString 属性；返回是否有任一区间 layer 发生变化。
     *
     * @param fc        待更新的 FeatureCollection
     * @param layerById 边 id -> 新 layer
     * @return 有变化返回 true（调用方据此决定是否重写文件）
     */
    private static boolean applyLayers(FeatureCollection fc, Map<String, Integer> layerById) {
        if (fc.getFeatures() == null) {
            return false;
        }
        boolean changed = false;
        for (org.geojson.Feature f : fc.getFeatures()) {
            if (!(f.getGeometry() instanceof org.geojson.LineString)) {
                continue;
            }
            Object idProp = f.getProperty("id");
            if (idProp == null) {
                continue;
            }
            Integer newLayer = layerById.get(idProp.toString());
            if (newLayer == null) {
                continue;
            }
            Object old = f.getProperty("layer");
            int oldLayer = old instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
            if (oldLayer != newLayer) {
                f.setProperty("layer", newLayer);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 从节点表中取出被给定区间引用到的节点（保序去重）。
     *
     * @param edges    区间
     * @param nodePool 可用节点表（id -> 节点）
     * @return 被引用的节点
     */
    private static List<RailNode> referencedNodes(List<RailEdge> edges, Map<String, RailNode> nodePool) {
        List<RailNode> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RailEdge e : edges) {
            for (String id : new String[]{e.getFromNodeId(), e.getToNodeId()}) {
                if (seen.add(id)) {
                    RailNode node = nodePool.get(id);
                    if (node != null) {
                        result.add(node);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 写单个 geojson 文件。
     *
     * @param dir     目录
     * @param fileKey 文件键（不含扩展名）
     * @param nodes   节点
     * @param edges   区间
     * @param log     日志
     * @return 成功写出 1，失败 0
     */
    private int writeFile(File dir, String fileKey, List<RailNode> nodes, List<RailEdge> edges, GeoTraversalLogger log) {
        FeatureCollection fc = new GeojsonBuilder().build(nodes, edges);
        File file = new File(dir, fileKey + ".geojson");
        try {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, fc);
            log.info("写入 " + file.getName() + "：" + nodes.size() + " 节点，" + edges.size() + " 区间");
            return 1;
        } catch (Exception e) {
            log.error("写入文件失败：" + file.getName(), e);
            return 0;
        }
    }
}
