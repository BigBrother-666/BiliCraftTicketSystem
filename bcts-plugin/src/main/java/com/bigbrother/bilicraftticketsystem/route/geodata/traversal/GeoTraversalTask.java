package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.route.geodata.entity.GeoNodeLoc;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.geojson.FeatureCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 铁路遍历任务：以<b>遍历单元</b>为原子，逐条线路遍历、验证、保存。
 * <p>
 * 一个单元 = 一条已登记起点的营运线路 + 它派生的联络线分支（见 {@link UnitWalk}），
 * 产出该线独占的 {@code <lineId>.geojson}。单元是调度、验证、保存、失败的唯一边界：
 * <ol>
 *   <li>按线依次遍历（主线程分片，每 {@link MapConfig#getTraversalIntervalTicks()} tick 展开
 *       {@link MapConfig#getTraversalEdgesPerInterval()} 段），一个单元走完立即验证；</li>
 *   <li><b>验证通过的单元立即纳入待写集合，失败的单元保留其上一次的 geojson 不动</b>——
 *       大型路网不会因为一条线配置写错而整体回退（旧实现是全有或全无）；</li>
 *   <li>全部单元结束后做跨单元校验（越界边落点，仅告警）、跨全部线路重算 layer、写文件、汇总报告。</li>
 * </ol>
 * 因为每个文件只有一个写者（产出它的单元），保存就是整体覆盖：不读旧文件、不合并、不保留。
 * <p>
 * 全局约束：同一时刻只允许一个遍历任务（{@link #RUNNING}），且完成后有全局冷却
 * （{@link MapConfig#getTraversalCooldownSeconds()}）。遍历期间车票/交通卡使用被暂停
 * （见 {@code TrainListeners}，靠 {@link #isTraversalRunning()} 判断）。可用 {@link #stopWalk(CommandSender)}
 * 提前停止。持 {@link #PERM_BYPASS_COOLDOWN} 权限者可绕过冷却，且其执行不刷新冷却。
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
     * 上次 {@code walkAll}（全图遍历）完成的时间戳（ms）；0 表示从未运行。与 {@link #lastWalkFinishTime} 独立计时。
     */
    private static volatile long lastFinishTime = 0;
    /**
     * 上次 {@code walk}（单线增量遍历）完成的时间戳（ms）；0 表示从未运行。
     */
    private static volatile long lastWalkFinishTime = 0;
    /**
     * 当前正在运行的任务，供 {@link #stopWalk(CommandSender)} 请求中止；无任务时为 null。
     */
    private static volatile GeoTraversalTask runningTask = null;

    private final BiliCraftTicketSystem plugin;
    private final CommandSender sender;
    /**
     * 增量遍历的目标线路 id 集合；为空表示全图遍历（{@code walkAll}）。
     */
    private final Set<String> targetLineIds;
    /**
     * 忽略的线路 id 集合（{@code walkAll --ignore} 用）：不遍历这些线、不展开指向它们的出向分支、
     * 收尾也不校验它们的车站完整性。仅全图遍历有意义。
     */
    private final Set<String> ignoreLineIds;
    /**
     * 本次所有单元（按执行顺序）。
     */
    private final List<UnitResult> units = new ArrayList<>();
    /**
     * 整个任务是否被用户请求停止。停止后不写任何文件（与单元级失败不同）。
     */
    private volatile boolean stopRequested = false;
    private volatile String stopReason = null;

    /**
     * 一个单元的执行结果。
     */
    private static class UnitResult {
        private final String lineId;
        private final UnitWalk walk;
        /**
         * 验证是否通过（通过才写文件）。
         */
        private boolean valid = true;
        /**
         * 失败原因（验证不通过 / 中止）。
         */
        private String failReason;

        private UnitResult(String lineId, UnitWalk walk) {
            this.lineId = lineId;
            this.walk = walk;
        }
    }

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
     * @param ignoreLineIds 忽略的线路 id 集合（见 {@link #ignoreLineIds}）；空 / null 表示不忽略
     */
    public GeoTraversalTask(BiliCraftTicketSystem plugin, CommandSender sender, Set<String> targetLineIds,
                            Set<String> ignoreLineIds) {
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
        GeoTraversalTask task = runningTask;
        if (!RUNNING.get() || task == null) {
            sender.sendMessage(MainConfig.prefix.append(
                    CommonUtils.mmStr2Component(msg("traversal-no-running", "<red>当前没有正在进行的构建铁路图任务"))));
            return;
        }
        task.stopReason = "用户 " + sender.getName() + " 手动停止了构建铁路图任务";
        task.stopRequested = true;
        sender.sendMessage(MainConfig.prefix.append(
                CommonUtils.mmStr2Component(msg("traversal-stop-requested", "<yellow>已请求停止当前构建铁路图任务..."))));
    }

    /**
     * 遍历所有已登记线路起点并分文件保存。
     */
    public void runAll() {
        start();
    }

    /**
     * 只遍历指定线路（及各自的联络线分支），增量更新这些线的 geojson。
     */
    public void runLine() {
        start();
    }

    /**
     * 全图 / 单线遍历共用的启动骨架：向导校验 + 冷却校验（不抢锁）+ 抢单运行锁，随后建单元列表并启动分片遍历。
     * <p>
     * 冷却按模式独立：walk 与 walkAll 各查各的冷却值与上次完成时间；但单运行锁 {@link #RUNNING} 是共用的。
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

        runningTask = this;
        GeoTraversalLogger log = new GeoTraversalLogger(plugin, sender);
        BukkitTask progressTask = startProgressFeedback(log);
        startSlicedTraversal(log, bypassCooldown, progressTask);
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
        runningTask = null;
        RUNNING.set(false);
    }

    /**
     * 在主线程把整次遍历分片到多个 tick 执行：
     * <ol>
     *   <li>先解析所有待遍历线路的登记起点、建单元列表（读起点铁轨方块，需主线程）；</li>
     *   <li>用定时任务每 {@link MapConfig#getTraversalIntervalTicks()} tick 推进<b>当前单元</b>一批；
     *       当前单元走完即就地验证，然后切到下一个单元；</li>
     *   <li>全部单元结束后做跨单元校验、layer 重算、写文件、重载配置。</li>
     * </ol>
     * 每段的行走矿车都在单个 tick 内生成并销毁，不跨 tick 持有。
     *
     * @param log            日志
     * @param bypassCooldown 是否绕过冷却（透传给收尾）
     * @param progressTask   进度反馈任务（透传给收尾）
     */
    private void startSlicedTraversal(GeoTraversalLogger log, boolean bypassCooldown, BukkitTask progressTask) {
        // 起点解析与后续每 tick 的展开都必须在主线程。
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!prepareUnits(log)) {
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
            new BukkitRunnable() {
                /**
                 * 当前正在推进的单元下标。
                 */
                private int index = 0;

                @Override
                public void run() {
                    if (stopRequested) {
                        cancel();
                        log.message("构建铁路图任务已中止，未写入任何文件：" + stopReason, NamedTextColor.RED);
                        finishTraversal(log, progressTask, bypassCooldown);
                        return;
                    }
                    try {
                        int budget = edgesPerInterval;
                        // 一批内可跨过多个单元（某单元可能只剩很少的段），把预算用完为止
                        while (budget > 0 && index < units.size()) {
                            UnitResult unit = units.get(index);
                            if (unit.walk.hasPending()) {
                                budget -= Math.max(1, unit.walk.stepBatch(budget));
                                continue;
                            }
                            // 本单元走完：就地验证并切到下一个
                            validateUnit(unit, log);
                            index++;
                        }
                    } catch (Exception e) {
                        log.error("遍历展开失败", e);
                        log.message("构建铁路图任务失败：" + e, NamedTextColor.RED);
                        cancel();
                        finishTraversal(log, progressTask, bypassCooldown);
                        return;
                    }
                    if (index < units.size()) {
                        return;
                    }
                    // 全部单元结束：收尾（跨单元校验 / layer / 写文件）为 CPU/磁盘密集且不触碰 Bukkit API，
                    // 放异步线程执行；只有最后的 loadConfig / 推送快照回主线程。
                    cancel();
                    finalizeAndSave(log, progressTask, bypassCooldown);
                }
            }.runTaskTimer(plugin, 1L, intervalTicks);
        });
    }

    /**
     * 解析待遍历线路的登记起点并建立单元列表（读起点铁轨方块，须主线程）。
     * <p>
     * 单元从登记起点出发，<b>没有起点的线路不会有自己的文件</b>（也不会被别的线被动覆盖——那样它的边
     * 会落在别人的文件里，正是文件归属混乱的根源）。缺起点时：
     * <ul>
     *   <li>{@code walk <lineIds>}：点名的线没有起点无从遍历，直接拒绝；</li>
     *   <li>{@code walkAll}：配置里登记了但还没建完的线路通常没设起点，<b>告警并跳过</b>，其余线路照常遍历。</li>
     * </ul>
     *
     * @param log 日志
     * @return 成功建立至少一个单元返回 true；walk 目标缺起点 / 无可遍历线路返回 false（已反馈原因）
     */
    private boolean prepareUnits(GeoTraversalLogger log) {
        // 待遍历线路：walk 用目标线集合；walkAll 用全部营运线（除联络线与忽略名单）
        List<String> lineIds = new ArrayList<>();
        if (!targetLineIds.isEmpty()) {
            // 联络线不能作为遍历单元：它没有自己的起点，其区间归属各营运线单元。
            // 显式拒绝比让它走到「没有登记起点」的报错更容易理解。
            if (targetLineIds.contains(RailwaySystemConfig.CONTACT_ID)) {
                log.message("联络线 [%s] 不能单独遍历：它没有自己的遍历起点，其区间随各营运线一起构建。"
                        .formatted(RailwaySystemConfig.CONTACT_ID)
                        + "请改为遍历与它相连的营运线", NamedTextColor.RED);
                return false;
            }
            lineIds.addAll(targetLineIds);
        } else {
            for (LineInfo info : LineConfig.allLines()) {
                String id = info.getId();
                // 联络线没有自己的起点（设计如此）：它作为各营运线单元的阶段 2 被带出来
                if (RailwaySystemConfig.CONTACT_ID.equals(id) || ignoreLineIds.contains(id)) {
                    continue;
                }
                lineIds.add(id);
            }
            if (lineIds.isEmpty()) {
                log.message("没有可遍历的线路，请先在 railway_routes.yml 中配置线路", NamedTextColor.RED);
                return false;
            }
        }

        // 先一次性检出所有缺登记起点的线路，一并报出（避免玩家一条条试）
        Map<String, GeoNodeLoc> starts = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String lineId : lineIds) {
            GeoNodeLoc start = plugin.getGeoDatabaseManager().getGeoNodeLoc(lineId);
            if (start == null) {
                missing.add(lineId);
            } else {
                starts.put(lineId, start);
            }
        }
        if (!missing.isEmpty()) {
            if (!targetLineIds.isEmpty()) {
                // walk <lineIds>：玩家点名要建的线没有起点，无从遍历，直接拒绝
                log.message("以下线路没有已登记的遍历起点，请先用 /railgeo setStartPos <lineId> 登记：%s"
                        .formatted(String.join(", ", missing)), NamedTextColor.RED);
                return false;
            }
            // walkAll：已在配置里登记、但轨道还没建完的线路通常还没设起点，跳过它们、照常遍历其余线路
            log.message("以下线路没有已登记的遍历起点，本次跳过：%s"
                    .formatted(String.join(", ", missing)), NamedTextColor.YELLOW, Level.WARNING);
            if (starts.isEmpty()) {
                log.message("没有任何线路登记了遍历起点，请先用 /railgeo setStartPos <lineId> 登记",
                        NamedTextColor.RED);
                return false;
            }
        }

        // 注意：walk <lineIds> 不限定越界边的目标线。越界边写进产出它的单元自己的文件，
        // 按目标线过滤会让 walk L 少记出边、与 walkAll 产出的 L.geojson 不一致。
        // 「只重建哪些文件」已由单元集合本身保证。
        if (targetLineIds.isEmpty()) {
            log.message("开始构建铁路图，共 " + starts.size() + " 条线路...", NamedTextColor.DARK_AQUA);
        } else {
            log.message("开始构建线路 [%s] 及其联络线...".formatted(String.join(", ", targetLineIds)),
                    NamedTextColor.DARK_AQUA);
        }

        for (Map.Entry<String, GeoNodeLoc> entry : starts.entrySet()) {
            String lineId = entry.getKey();
            GeoNodeLoc start = entry.getValue();
            UnitWalk walk = new UnitWalk(lineId, log, MapConfig.getTraversalMaxEdgesPerUnit(),
                    MapConfig.getTraversalMaxEdgesPerWalk(), ignoreLineIds,
                    MapConfig.getTraversalCoasterSampleStep());
            UnitResult unit = new UnitResult(lineId, walk);
            units.add(unit);

            Block startRail = StartRailResolver.resolve(start.getStartLocation(), start.getStartDirection());
            if (startRail == null) {
                // 单元级失败：该线不写文件，其它线照常遍历
                walk.abort("起点坐标处没有铁轨（坐标 " + start.getStartLocation()
                        + "）。实体铁轨与 TCC 轨道均未找到，请检查该线路登记的起点是否仍在轨道上");
                continue;
            }
            log.info("单元 " + lineId + " 从起点 " + start.getStartLocation() + " 展开");
            walk.seed(startRail, start.getStartDirection());
        }
        return true;
    }

    /**
     * 单元级验证：中止检查 + 车站覆盖检查。通过才纳入待写集合。
     * <p>
     * 失败只影响本单元——该线保留上一次的 geojson 不动，其余线照常写出。
     *
     * @param unit 单元
     * @param log  日志
     */
    private void validateUnit(UnitResult unit, GeoTraversalLogger log) {
        if (unit.walk.isAborted()) {
            unit.valid = false;
            unit.failReason = unit.walk.getAbortReason();
            log.message("线路 %s 构建失败：%s（该线保留上次的数据）".formatted(unit.lineId, unit.failReason),
                    NamedTextColor.RED, Level.SEVERE);
            return;
        }
        // --ignore 的线路不校验车站完整性（其轨道本就未遍历，缺站是预期行为）
        if (ignoreLineIds.contains(unit.lineId)) {
            return;
        }
        Set<String> visited = unit.walk.getVisitedStationsByLine()
                .getOrDefault(unit.lineId, Collections.emptySet());
        String missing = missingStations(unit.lineId, visited);
        if (missing != null) {
            unit.valid = false;
            unit.failReason = "配置车站 " + missing + "未在遍历中到达";
            log.message("线路 %s 校验失败：配置车站 %s未在遍历中到达".formatted(unit.lineId, missing), NamedTextColor.RED, Level.SEVERE);
            // 缺站多因遍历在半路断掉（断轨 / 道岔没声明该线），输出本线最有代表性的那条途经车站链路
            reportLineChain(unit, log);
            return;
        }
        log.info("单元 " + unit.lineId + " 校验通过：" + unit.walk.getCollector().totalEdges() + " 条区间");
    }

    /**
     * 车站覆盖检查：把本单元实际到达的车站与配置的 {@code bossbar-stations} 比对。
     * <p>
     * 图遍历可能有分叉（如正线跨站 + 停靠线进站），不强求顺序严格一致，只做集合层面的覆盖检查：
     * 配置里有但没走到的即为缺失（可能轨道未铺或控制牌缺声明）。
     *
     * @param lineId  线路 id
     * @param visited 实际到达的车站名
     * @return 缺失车站的描述（形如 {@code "A" "B" }）；无缺失返回 null
     */
    private String missingStations(String lineId, Set<String> visited) {
        LineInfo info = LineConfig.get(lineId);
        if (info == null || info.getBossbarStations().isEmpty()) {
            return null;
        }
        StringBuilder missing = new StringBuilder();
        for (String want : new LinkedHashSet<>(info.getBossbarStations())) {
            if (!visited.contains(want)) {
                missing.append("\"").append(want).append("\" ");
            }
        }
        return missing.isEmpty() ? null : missing.toString();
    }

    /**
     * 跨单元校验：每条越界边的落点节点，应当出现在<b>目标线自己单元</b>的到达节点集中。
     * <p>
     * 不出现说明该出向声明的线路写错了（如写成 {@code @L1} 实际那段轨道属 L2），或目标线的轨道
     * 没铺到该处。这是按单元遍历才可能做的校验——旧的全图乱序遍历无法区分这种情况。
     * <p>
     * 只产生<b>告警</b>而不阻止写入：现存路网可能已有若干这类归属错误，设为失败会导致重构当天
     * 全网构建不过。
     *
     * @param log 日志
     * @return 告警条数
     */
    private int validateCrossBoundary(GeoTraversalLogger log) {
        // 只对本次成功遍历过的线路做比对：
        //  - 没遍历的线路（walk 范围外 / --ignore）没有到达节点集可比；
        //  - 构建失败的线路只走了一部分，拿它的残缺节点集比对会刷出一堆假告警
        //    （该线自己的失败已经单独报过了）。
        Map<String, Set<String>> reachedByUnit = new HashMap<>();
        for (UnitResult unit : units) {
            if (unit.valid) {
                reachedByUnit.put(unit.lineId, unit.walk.getCollector().nodeIds());
            }
        }
        int warnings = 0;
        for (UnitResult unit : units) {
            if (!unit.valid) {
                continue;
            }
            for (Map.Entry<String, Set<String>> entry : unit.walk.getCrossBoundaryLandings().entrySet()) {
                String targetLine = entry.getKey();
                Set<String> reached = reachedByUnit.get(targetLine);
                if (reached == null) {
                    continue; // 目标线本次未遍历，无从比对
                }
                for (String landing : entry.getValue()) {
                    if (!reached.contains(landing)) {
                        log.warn(("线路 %s 的道岔声明了通往 %s 的出向，但其落点 %s 不在线路 %s 的遍历结果中。"
                                + "请检查该出向的线路 id 是否写错，或 %s 的轨道是否已铺到该处")
                                .formatted(unit.lineId, targetLine, landing, targetLine, targetLine));
                        warnings++;
                    }
                }
            }
        }
        return warnings;
    }

    /**
     * 输出某单元的遍历路径，用于「配置车站未在遍历中到达」的定位。
     * <p>
     * 路径从该线登记起点出发一直向前探寻本线分支（见 {@link TraversalTrace#pathOfLine}）：岔路优先走
     * 经停站那一支，被去重跳过的后继按状态 key 缝合，故末端给出的是<b>真正走不下去的位置与原因</b>。
     *
     * @param unit 单元
     * @param log  日志
     */
    private void reportLineChain(UnitResult unit, GeoTraversalLogger log) {
        TraversalTrace.Path path = unit.walk.getTrace().pathOfLine(unit.lineId);
        if (path.nodes().isEmpty()) {
            log.message("线路 " + unit.lineId + " 没有任何遍历记录，请检查其登记起点与起点处的轨道",
                    NamedTextColor.RED, Level.SEVERE);
            return;
        }
        log.message("线路 %s 的遍历路径：".formatted(unit.lineId), NamedTextColor.YELLOW, Level.SEVERE);
        emitPath(path, log);
    }

    /**
     * 竖向渲染并输出一条路径（每个节点一行）：聊天栏按
     * {@link MapConfig#getTraversalTraceMaxChatLines()} 截断（{@code <0} 不限制，{@code 0} 不输出到聊天栏），
     * 日志文件恒写全量。
     *
     * @param path 路径
     * @param log  日志
     */
    private void emitPath(TraversalTrace.Path path, GeoTraversalLogger log) {
        int maxChat = MapConfig.getTraversalTraceMaxChatLines();
        List<Component> rows = TraversalTrace.renderVertical(path);
        int shown = 0;
        for (Component row : rows) {
            boolean toSender = maxChat < 0 || shown < maxChat;
            log.message(row, Level.SEVERE, toSender);
            if (toSender) {
                shown++;
            }
        }
        if (shown < rows.size()) {
            log.message("（聊天栏仅显示前 %d 行，共 %d 行，完整路径见 logs/railgeo_*.log）"
                    .formatted(shown, rows.size()), NamedTextColor.GRAY, Level.SEVERE);
        }
    }

    /**
     * 全部单元结束后的收尾：跨单元校验、写文件、清理孤立文件、全局重算 layer、重载配置。
     * <p>
     * 由分片定时任务在主线程、全部单元跑完后触发。校验 / 写文件 / layer 计算为纯计算与磁盘 I/O，
     * 不触碰 Bukkit API，放到异步线程执行；仅最后的 {@code loadConfig} 与快照推送切回主线程。
     * <b>本方法自行负责所有退出路径上调用 {@link #finishTraversal}</b>。
     *
     * @param log            日志
     * @param progressTask   进度反馈任务（透传给收尾）
     * @param bypassCooldown 是否绕过冷却（透传给收尾）
     */
    private void finalizeAndSave(GeoTraversalLogger log, BukkitTask progressTask, boolean bypassCooldown) {
        if (progressTask != null) {
            progressTask.cancel();
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UnitResult> writable = new ArrayList<>();
            List<UnitResult> failed = new ArrayList<>();
            try {
                log.message("全部线路构建完毕，开始校验...", NamedTextColor.DARK_AQUA);
                int warnings = validateCrossBoundary(log);

                for (UnitResult unit : units) {
                    (unit.valid ? writable : failed).add(unit);
                }

                int files = 0;
                if (!writable.isEmpty()) {
                    files = saveUnits(writable, log);
                    int orphans = cleanOrphanFiles(log);
                    if (orphans > 0) {
                        log.message("清理了 %d 个线路配置文件（线路配置已删除）".formatted(orphans), NamedTextColor.YELLOW);
                    }
                    log.message("计算 LineString 层级...", NamedTextColor.DARK_AQUA);
                    int relayered = recomputeAllLayers(log);
                    log.info("全局重算 layer：更新了 " + relayered + " 个文件的层级");
                }
                reportSummary(log, files, writable, failed, warnings);
            } catch (Exception e) {
                log.error("构建铁路图任务收尾失败", e);
                log.message("构建铁路图任务失败：" + e, NamedTextColor.RED);
                finishTraversal(log, progressTask, bypassCooldown);
                return;
            }

            // 回主线程：重载配置（会触碰 Bukkit / 各配置单例）+ 推送快照，随后收尾。
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!writable.isEmpty()) {
                        log.message("重载配置文件...", NamedTextColor.DARK_AQUA);
                        plugin.loadConfig(null);
                        log.message("重载配置完成", NamedTextColor.GREEN);

                        // 遍历产出新 geojson，若已连后端则推送 geo 快照
                        if (plugin.getWebLink() != null && plugin.getWebLink().getClient().isConnected()) {
                            plugin.getWebLink().getSnapshotPublisher().publishGeo();
                            log.message("已向线路图后端推送 geojson 快照", NamedTextColor.GREEN);
                        }
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
     * 汇总报告：成功 / 失败（逐条给原因）/ 告警。失败线路保留了上一次的 geojson，须说明清楚。
     *
     * @param log      日志
     * @param files    实际写出的文件数
     * @param writable 验证通过的单元
     * @param failed   验证失败的单元
     * @param warnings 跨线路校验告警条数
     */
    private void reportSummary(GeoTraversalLogger log, int files, List<UnitResult> writable,
                               List<UnitResult> failed, int warnings) {
        if (failed.isEmpty()) {
            log.message("构建铁路图任务已完成：%d 条线路全部成功，写入 %d 个文件".formatted(writable.size(), files),
                    NamedTextColor.GREEN);
        } else {
            log.message("构建铁路图任务已完成：成功 %d 条（写入 %d 个文件），失败 %d 条"
                    .formatted(writable.size(), files, failed.size()), NamedTextColor.YELLOW);
            for (UnitResult unit : failed) {
                log.message("  失败：%s —— %s".formatted(unit.lineId, unit.failReason), NamedTextColor.RED);
            }
            log.info("失败的线路保留了上一次构建的数据，修正后可用 /railgeo walk <lineId> 单独重建");
        }
        if (warnings > 0) {
            log.warn("有 %d 条跨线路校验告警（道岔出向的线路 id 可能写错），详见上方与日志".formatted(warnings));
        }
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
     * 按单元遍历后进度可以说清「哪条线在走、已完成几条」，而不只是一个总节点数。
     *
     * @param log 日志（同时写入日志文件）
     * @return 反馈定时任务（遍历结束须取消）；不反馈时为 null
     */
    private BukkitTask startProgressFeedback(GeoTraversalLogger log) {
        int intervalSec = MapConfig.getTraversalProgressIntervalSeconds();
        if (intervalSec <= 0) {
            return null;
        }
        long periodTicks = intervalSec * 20L;
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int done = 0;
            int edges = 0;
            double distance = 0;
            String current = null;
            for (UnitResult unit : units) {
                edges += unit.walk.getCollector().totalEdges();
                distance += unit.walk.getCollector().totalDistance();
                if (unit.walk.hasPending()) {
                    if (current == null) {
                        current = unit.lineId;
                    }
                } else {
                    done++;
                }
            }
            String text = current == null
                    ? "正在构建铁路图，已完成 %d/%d 条线路，%d 条区间，%.2fkm ...".formatted(done, units.size(), edges, distance)
                    : "正在构建铁路图：当前线路 %s（已完成 %d/%d 条），共 %d 条区间，%.2fkm ..."
                    .formatted(current, done, units.size(), edges, distance);
            log.message(text, NamedTextColor.GRAY);
        }, periodTicks, periodTicks);
    }

    /**
     * 清理无用 geojson 文件：删除 geodata 目录下「文件名（lineId）不是任何已登记起点线路」的
     * {@code <lineId>.geojson}。
     * <p>
     * 玩家用 ticketconfig 删除某条线路后，其旧文件不会被本次遍历覆盖（本次不产出该线），会残留成
     * 线路配置文件，前端仍会加载显示一条已经不存在的线。
     * <p>
     * 按单元分文件后不再有 {@code contact.geojson}（联络线段归属各营运线单元的文件），
     * 故不再需要那条「恒保留」特例——它本身就是一个孤立文件，该删。
     *
     * @param log 日志
     * @return 删除的文件数
     */
    private int cleanOrphanFiles(GeoTraversalLogger log) {
        File dir = plugin.getGeodataDir();
        File[] geoFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".geojson"));
        if (geoFiles == null) {
            return 0;
        }
        // 当前有登记起点的线路即当前应存在的文件名集合（单元 = 文件）
        Set<String> validKeys = new LinkedHashSet<>();
        for (GeoNodeLoc loc : plugin.getGeoDatabaseManager().getAllGeoNodeLoc()) {
            validKeys.add(loc.getLineId());
        }
        // 这同时自动清理掉重构前遗留的旧 contact.geojson。
        validKeys.remove(RailwaySystemConfig.CONTACT_ID);
        int deleted = 0;
        for (File f : geoFiles) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName();
            String fileKey = name.substring(0, name.length() - ".geojson".length());
            if (validKeys.contains(fileKey)) {
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
     * 把验证通过的各单元写成 {@code <lineId>.geojson}（整体覆盖）。
     * <p>
     * <b>不读旧文件、不合并、不保留</b>：每个文件只有一个写者（产出它的单元），本次产出即权威。
     * 这是「文件归属按单元」带来的最大收益——旧实现要靠一系列「保留本次结构上到不了的旧边」规则
     * 才能避免断链，现在那些规则全部不需要了（见 {@link TraversalCollector} 的类注释）。
     *
     * @param units 验证通过的单元
     * @param log   日志
     * @return 写出的文件数
     */
    private int saveUnits(List<UnitResult> units, GeoTraversalLogger log) {
        File dir = plugin.getGeodataDir();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper();
        int files = 0;
        for (UnitResult unit : units) {
            List<RailEdge> edges = unit.walk.getCollector().edges();
            List<RailNode> nodes = unit.walk.getCollector().referencedNodes();
            FeatureCollection fc = new GeojsonBuilder().build(nodes, edges);
            File file = new File(dir, unit.lineId + ".geojson");
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
     * 读回 geodata 目录下全部 geojson 的区间，按空间交叉关系<b>全局重算 layer</b>，把新层级写回各文件。
     * <p>
     * layer 必须跨全部线路一起算，才能保证新走出的高架 / 平交与既有线路的叠压关系正确、且联络线总在下层。
     * 本次失败的线路文件（保留的旧几何）也参与计算——它们仍然在图上显示，必须参与叠压判定。
     * <p>
     * 为避免丢失节点（其它文件的世界可能未加载，经 {@link GeojsonReader#read} 会丢节点），本方法在
     * <b>原始 {@link FeatureCollection} 层面</b>只改 LineString 的 {@code layer} 属性再原样重写。
     * 只重写 layer 确有变化的文件，减少无谓磁盘写入。
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
        // 全局重算：按空间交叉重新分层
        long startMs = System.currentTimeMillis();
        int cyclic = LayerAssigner.assign(allEdges);
        log.info("重算 layer：" + allEdges.size() + " 区间，耗时 " + (System.currentTimeMillis() - startMs) + " ms");
        if (cyclic > 0) {
            log.warn("重算 layer：" + cyclic + " 个区间的叠压约束成环（不同交叉点高低相反），已取同一层级");
        }
        // 边 id -> 新 layer。同 id 边可能被多个单元各产出一份（共用轨道 / 越界边），
        // 几何相同故 layer 相同，取任一即可。
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
}
