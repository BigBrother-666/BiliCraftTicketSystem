package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.geojson.LngLatAlt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全图 BFS 遍历驱动器（取代旧的按线行走 LineWalk）。
 * <p>
 * 从数据库登记的起点进入，把整张铁路网当一张图展开：bcswitcher / platform 为节点、其间铁路为有向边。
 * 矿车携带一个「当前 lineId」沿途更新，决定每段边的归属：
 * <ul>
 *   <li><b>起点首段</b>：用数据库登记的 lineId（platform 不提供 lineId，需此兜底）。</li>
 *   <li><b>platform 节点</b>：一进一出，当前 lineId 原样带过去。按站名查 routes 判断折返站（{@code :RV}）：
 *       非折返沿进入方向续行，折返反向驶出。</li>
 *   <li><b>bcswitcher 节点</b>：按进入方向取出该牌所有匹配出向<b>全部展开</b>（不按线过滤）；离开某出向时
 *       当前 lineId 更新为<b>该出向声明的 lineId</b>。共用出向（{@code r@a;b}）按 lineId 拆成多个 fork，
 *       各挂单一 tag 各走各记，使下游普通道岔靠单一 tag 正确选向。</li>
 * </ul>
 * 去重 key = {@code (节点, 入向, 出向, lineId)}，跨所有起点共享：一个起点即可覆盖其连通子网，后续起点撞到
 * 已访问状态立即终止（多起点主要用于覆盖不连通子网）。共用轨道在每条线各自的 geojson 里都完整。
 * <p>
 * 仍用实体矿车行走（{@link TrackWalker}）以正确触发沿途原版 switcher 的 addtag / remtag。
 */
public class GraphWalk {
    @Getter
    private final TraversalCollector collector;
    private final GeoTraversalLogger log;
    private final int maxNodes;
    private final int maxEdgesPerWalk;

    /**
     * 已展开的 {@code (节点,入向,出向,lineId)} 状态，跨所有起点共享，防止环线 / 重复死循环、并在
     * 多起点间复用进度（撞到已访问状态即停）。
     */
    private final Set<String> visited;
    /**
     * 每条线遍历实际到达的车站名（按首次到达顺序），供事后与配置站序比对。
     */
    @Getter
    private final Map<String, Set<String>> visitedStationsByLine = new LinkedHashMap<>();
    /**
     * 整次遍历累计处理的段数（跨所有起点），用于兜底防环。
     * 用 {@link AtomicInteger}：主线程递增，异步进度反馈线程读取（见 GeoTraversalTask 的进度反馈）。
     */
    private final AtomicInteger processed = new AtomicInteger(0);
    /**
     * 是否已因异常情况（达到段数上限）中止。中止后整次遍历应停止并放弃写文件。
     */
    @Getter
    private boolean aborted = false;
    /**
     * 中止原因（含停止位置等调试信息），供反馈给发起者。
     */
    @Getter
    private String abortReason = null;

    /**
     * @param collector       结果收集器（跨起点共享）
     * @param log             日志
     * @param visited         去重状态集合（跨起点共享）
     * @param maxNodes        整次遍历最多展开段数（兜底防环）
     * @param maxEdgesPerWalk 单段行走最多采样的坐标点数
     */
    public GraphWalk(TraversalCollector collector, GeoTraversalLogger log, Set<String> visited,
                     int maxNodes, int maxEdgesPerWalk) {
        this.collector = collector;
        this.log = log;
        this.visited = visited;
        this.maxNodes = maxNodes;
        this.maxEdgesPerWalk = maxEdgesPerWalk;
    }

    /**
     * 待展开的一段行走状态。
     *
     * @param prevNodeId 上一节点 id（起点首段为 null，无入边可记）
     * @param rail       本段起始铁轨
     * @param direction  本段起始方向
     * @param forcedDir  离开起始 bcswitcher 时的强制出向（platform 续行 / 起点首段为 null）
     * @param lineId     本段携带的当前线路 id（决定本段边归属及矿车导向 tag）
     */
    private record WalkState(String prevNodeId, Block rail, Vector direction, String forcedDir, String lineId) {
    }

    /**
     * 从一个起点把其连通子网 BFS 展开（与其它起点共享 {@code visited} / {@code collector}）。
     * 必须在主线程调用。
     *
     * @param startLineId    起点登记的线路 id（首段及之后未经道岔改写前的当前 lineId）
     * @param startRail      起点铁轨
     * @param startDirection 起点方向
     */
    public void walkFrom(String startLineId, Block startRail, Vector startDirection) {
        Deque<WalkState> queue = new ArrayDeque<>();
        queue.add(new WalkState(null, startRail, startDirection, null, startLineId));

        WalkState st;
        while ((st = queue.poll()) != null) {
            if (aborted) {
                break;
            }
            if (processed.get() >= maxNodes) {
                String pos = String.valueOf(st.rail().getLocation());
                abort("达到段数上限 " + maxNodes + "，可能存在配置缺失或异常环路。当前线路 "
                        + st.lineId() + "，停止位置 " + pos);
                break;
            }
            processed.incrementAndGet();
            walkSegment(st, queue);
        }
    }

    /**
     * 当前累计已展开的段数。供异步进度反馈线程读取。
     *
     * @return 已展开段数
     */
    public int getProcessed() {
        return processed.get();
    }

    /**
     * 标记整次遍历因异常情况中止：记录原因并写日志。调用后 {@link #walkFrom} 的循环会尽快退出，
     * 上层据 {@link #isAborted()} 放弃写文件并把 {@link #getAbortReason()} 反馈给发起者。
     *
     * @param reason 中止原因（含调试信息）
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
        log.error("遍历中止：" + reason, null);
    }

    /**
     * 走一段：从 state 起步走到下一个节点，记录区间，并按节点类型把后继状态入队。
     *
     * @param st    当前段状态
     * @param queue 待展开队列
     */
    private void walkSegment(WalkState st, Deque<WalkState> queue) {
        String lineId = st.lineId();
        String color = LineConfig.getColor(lineId);
        String railwaySystemId = LineConfig.getSystemId(lineId);
        TrackWalker walker = new TrackWalker(st.rail(), st.direction());
        walker.setLineTag(lineId);
        walker.setForcedDirection(st.forcedDir());
        try {
            List<LngLatAlt> coords = new ArrayList<>();
            int[] count = {0};
            TrackWalker.WalkResult result = walker.walkToNextNode(railBlock -> {
                if (count[0] < maxEdgesPerWalk) {
                    coords.add(toCoord(railBlock));
                    count[0]++;
                }
            });

            if (result.reason() == TrackWalker.StopReason.END) {
                log.info("线路 " + lineId + " 轨道结束（断轨/死路 @ " + result.railBlock().getLocation() + "）");
                return;
            }

            Vector arrival = result.direction();
            String arrivalFace = faceKey(arrival);

            RailNode node;
            if (result.reason() == TrackWalker.StopReason.PLATFORM) {
                String stationName = result.sign().getLine(2).trim();
                node = collector.resolveNode(RailNode.Type.STATION, result.railBlock(), stationName);
                visitedStationsByLine.computeIfAbsent(lineId, k -> new LinkedHashSet<>()).add(stationName);
                log.info("  到达车站 " + stationName + " @ " + node.getId());
            } else {
                node = collector.resolveNode(RailNode.Type.SWITCH, result.railBlock(), null);
                log.info("  经过道岔 @ " + node.getId());
            }
            node.addLineId(lineId);
            node.addRailwaySystemId(railwaySystemId);

            // 记录入边（起点首段 prevNodeId 为 null，无边可记）。st.forcedDir() 即离开上一道岔所用出向，
            // 作为本段物理出向写入，供运行时道岔对带导航的列车直接选向。
            if (st.prevNodeId() != null && !st.prevNodeId().equals(node.getId())) {
                collector.recordEdge(lineId, st.prevNodeId(), node.getId(), lineId, railwaySystemId, color,
                        GeoUtils.simplifyLineString(coords), result.length(), st.forcedDir(), node.getRailBlock().getWorld().getName());
            }

            if (result.reason() == TrackWalker.StopReason.PLATFORM) {
                expandPlatform(node, lineId, arrival, arrivalFace, queue);
            } else {
                expandSwitcher(node, lineId, walker, result, arrival, arrivalFace, queue);
            }
        } finally {
            walker.destroy();
        }
    }

    /**
     * platform 节点展开：一进一出。非折返站沿进入方向续行；折返站（{@code :RV}）反向驶出。
     * 当前 lineId 原样带过去（platform 不提供 lineId）。出向不强制，forcedDir 传 null。
     *
     * @param node        platform 节点
     * @param lineId      本段携带的当前线路 id
     * @param arrival     到达方向
     * @param arrivalFace 到达方向的面 key（入向）
     * @param queue       待展开队列
     */
    private void expandPlatform(RailNode node, String lineId, Vector arrival, String arrivalFace, Deque<WalkState> queue) {
        LineInfo lineInfo = LineConfig.get(lineId);
        boolean reverse = lineInfo != null && lineInfo.isReverseStationByName(node.getStationName());
        Vector outDir = reverse ? arrival.clone().multiply(-1) : arrival.clone();
        if (reverse) {
            log.info("    折返站 " + node.getStationName() + "，反向驶出");
        }
        String outFace = faceKey(outDir);
        tryEnqueue(node, arrivalFace, outFace, lineId, queue,
                new WalkState(node.getId(), node.getRailBlock(), outDir, null, lineId));
    }

    /**
     * bcswitcher 节点展开：按进入方向取出该牌所有匹配出向<b>全部展开</b>（不按线过滤）。对每个出向声明的
     * 每个 lineId 各 fork 一段强制行走——离开后当前 lineId 即更新为该声明 lineId，使全图 BFS 自然流入
     * 其它线路，共用出向也按 lineId 拆开各走各记。
     *
     * @param node        道岔节点
     * @param lineId      到达本道岔时携带的当前线路 id（仅用于日志/兜底，出边线路以出向声明为准）
     * @param walker      当前行走器（提供 member 上下文以判断牌头进入方向匹配）
     * @param result      到达本道岔的行走结果
     * @param arrival     到达方向
     * @param arrivalFace 到达方向的面 key（入向）
     * @param queue       待展开队列
     */
    @SuppressWarnings("unused")
    private void expandSwitcher(RailNode node, String lineId, TrackWalker walker, TrackWalker.WalkResult result,
                                Vector arrival, String arrivalFace, Deque<WalkState> queue) {
        RailPiece rail = result.sign().getRail();
        List<BcSwitcherBranch> branches = walker.collectSwitcherBranches(rail);
        for (BcSwitcherBranch branch : branches) {
            for (String outLineId : branch.getLineIds()) {
                if (!LineConfig.getLines().containsKey(outLineId)) {
                    log.info("bcswitcher(%s)的道岔lineId %s 不存在，跳过该分支".formatted(rail.block().getLocation(), outLineId));
                    continue;
                }
                // 出向 key 用 (方向, 出向lineId)：共用出向按线拆 fork，各挂单一 tag 各走各记。
                tryEnqueue(node, arrivalFace, branch.getDirectionStr(), outLineId, queue,
                        new WalkState(node.getId(), node.getRailBlock(), arrival.clone(),
                                branch.getDirectionStr(), outLineId));
            }
        }
    }

    /**
     * 按 {@code (节点,入向,出向,lineId)} 去重后入队。已展开过的状态跳过，防止环线 / 重复死循环，
     * 并在多起点间复用进度。
     *
     * @param node    节点
     * @param inFace  入向 key
     * @param outFace 出向 key
     * @param lineId  本后继段携带的当前线路 id
     * @param queue   待展开队列
     * @param next    后继状态
     */
    private void tryEnqueue(RailNode node, String inFace, String outFace, String lineId,
                            Deque<WalkState> queue, WalkState next) {
        String key = node.getId() + "|" + inFace + "|" + outFace + "|" + lineId;
        if (visited.add(key)) {
            queue.add(next);
        }
    }

    /**
     * 把方向向量规整为离散面 key（仅取水平主轴的符号），用于去重。
     *
     * @param dir 方向向量
     * @return 形如 "1_0" / "0_-1" 的面 key
     */
    private String faceKey(Vector dir) {
        int x = (int) Math.signum(dir.getX());
        int z = (int) Math.signum(dir.getZ());
        return x + "_" + z;
    }

    /**
     * 铁轨方块 -> geojson 坐标（经度=x, 纬度=z, 高度=y，沿用旧 geojson 约定）。
     *
     * @param railBlock 铁轨方块
     * @return 坐标
     */
    private LngLatAlt toCoord(Block railBlock) {
        return new LngLatAlt(railBlock.getX(), railBlock.getZ(), railBlock.getY());
    }
}

