package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.route.geodata.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条线路的遍历：从该线路登记的起点出发，矿车全程带该 lineId 的 tag，沿轨道连续行走，
 * 把经过的 platform / bcswitcher 记为节点、相邻节点间记为区间，直到该线路 routes.yml 配置的
 * 车站（{@code bossbar-stations}）按序全部到齐，或轨道结束。
 * <ul>
 *   <li><b>platform 不参与选向</b>，只有 bcswitcher 按矿车 tag 转向。本线遍历全程矿车只带本
 *       lineId 的 tag，bcswitcher 会把它导向本线路。platform 仅作为车站节点，并核对车站名顺序。</li>
 *   <li>遇到声明了 default 分支的 bcswitcher，表示该处有正线，额外用一节带 default
 *       tag 的临时矿车走一段正线子遍历，坐标归入<b>当前线路文件</b>。</li>
 * </ul>
 * 必须在主线程执行。
 */
public class LineWalk {
    private final String lineId;
    private final Block startRail;
    private final Vector startDirection;
    private final LineInfo lineInfo;
    private final TraversalCollector collector;
    private final GeoTraversalLogger log;
    private final int maxNodes;
    private final int maxEdgesPerWalk;
    /**
     * 本次遍历是否为联络线（lineId == contact）。
     */
    private final boolean isContact;
    /**
     * 起步时的「上一个节点」id（联络线从来源道岔出发时设为该道岔节点 id，使第一段区间能正确保存）。
     * 主线遍历从登记起点出发，无前置节点，保持 null。
     */
    private String initialPrevNodeId = null;

    /**
     * @param lineId          线路 id
     * @param startRail       起点铁轨
     * @param startDirection  起点方向
     * @param collector       结果收集器
     * @param log             遍历日志
     * @param maxNodes        本线最多经过的节点数（兜底防止断轨配置造成死循环）
     * @param maxEdgesPerWalk 单段行走最多记录的铁轨格数
     */
    public LineWalk(String lineId, Block startRail, Vector startDirection,
                    TraversalCollector collector, GeoTraversalLogger log,
                    int maxNodes, int maxEdgesPerWalk) {
        this.lineId = lineId;
        this.startRail = startRail;
        this.startDirection = startDirection;
        this.lineInfo = LineConfig.get(lineId);
        this.collector = collector;
        this.log = log;
        this.maxNodes = maxNodes;
        this.maxEdgesPerWalk = maxEdgesPerWalk;
        this.isContact = LineInfo.CONTACT_ID.equalsIgnoreCase(lineId);
    }

    /**
     * 设置起步时的「上一个节点」id。联络线从来源道岔出发时调用，传该道岔节点 id，
     * 使联络线第一段区间（道岔 -> 下一节点）能正确成段保存。
     *
     * @param nodeId 来源节点 id
     * @return this，便于链式调用
     */
    public LineWalk withInitialPrevNode(String nodeId) {
        this.initialPrevNodeId = nodeId;
        return this;
    }

    /**
     * 执行本线遍历。
     */
    public void walk() {
        if (lineInfo == null) {
            log.warn("线路 " + lineId + " 在 routes.yml 中不存在，跳过");
            return;
        }
        log.info("=== 开始遍历线路 " + lineId + "（" + lineInfo.getLineName() + "）===");

        String color = lineInfo.getLineColor();
        // 本线所属铁路系统 id（contact / default 返回 null）。本线主线及其正线子遍历的区间 / 节点都用它。
        String railwaySystemId = LineConfig.getSystemId(lineId);
        List<String> expectedStations = lineInfo.getBossbarStations();
        int stationIndex = 0;

        TrackWalker walker = new TrackWalker(startRail, startDirection);
        try {
            walker.setLineTag(lineId);

            String prevNodeId = initialPrevNodeId;
            String firstNodeId = null;
            int nodeCount = 0;

            while (nodeCount < maxNodes) {
                List<LngLatAlt> coords = new ArrayList<>();
                int[] count = {0};
                TrackWalker.WalkResult result = walker.walkToNextNode(railBlock -> {
                    if (count[0] < maxEdgesPerWalk) {
                        coords.add(toCoord(railBlock));
                        count[0]++;
                    }
                });

                if (result.reason() == TrackWalker.StopReason.END) {
                    log.info("线路 " + lineId + " 轨道结束（断轨/死路，坐标：" + result.railBlock().getLocation() + "），共经过 " + nodeCount + " 个节点");
                    break;
                }

                nodeCount++;
                RailNode node;
                boolean needReverse = false;
                if (result.reason() == TrackWalker.StopReason.PLATFORM) {
                    String stationName = result.sign().getLine(2).trim();
                    node = collector.resolveNode(RailNode.Type.STATION, result.railBlock(), stationName);
                    if (isContact) {
                        // 规定：联络线上不允许设置车站。遇到则记警告（仍记节点继续，便于定位问题）。
                        log.warn("  联络线上不应出现车站，却在 @ " + node.getId() + " 发现车站 \"" + stationName + "\"，请检查控制牌");
                    } else {
                        int matchedIndex = stationIndex;
                        stationIndex = verifyStation(expectedStations, stationIndex, stationName);
                        // 折返站（routes.yml 中站名带 :RV）：进站后需反向驶出，标记待折返
                        if (matchedIndex < expectedStations.size()
                                && expectedStations.get(matchedIndex).equals(stationName)
                                && lineInfo.isReverseStation(matchedIndex)) {
                            needReverse = true;
                        }
                    }
                    log.info("  到达车站 " + stationName + " @ " + node.getId());
                } else {
                    node = collector.resolveNode(RailNode.Type.SWITCH, result.railBlock(), null);
                    log.info("  经过道岔 @ " + node.getId());
                }
                node.addLineId(lineId);
                node.addRailwaySystemId(railwaySystemId);

                // 记录从上一个节点到此节点的区间（归入本线路文件）
                if (prevNodeId != null && !prevNodeId.equals(node.getId())) {
                    collector.recordEdge(lineId, prevNodeId, node.getId(), lineId, railwaySystemId, color,
                            GeoUtils.simplifyLineString(coords), result.length());
                }

                // 环线闭合：回到本线第一个节点即成环，闭合区间已在上面记录，停止
                if (firstNodeId == null) {
                    firstNodeId = node.getId();
                } else if (node.getId().equals(firstNodeId)) {
                    log.info("线路 " + lineId + " 回到起始节点 @ " + firstNodeId + "，环线闭合，停止");
                    break;
                }

                // bcswitcher 处理
                if (result.reason() == TrackWalker.StopReason.SWITCHER) {
                    if (isContact) {
                        // 联络线：若该道岔不含 contact 分支，说明往前是主线（已被主线遍历覆盖），
                        // 停止当前联络线遍历，避免与主线重复。
                        if (!hasContactBranch(result.sign())) {
                            log.info("  联络线到达不含 contact 分支的道岔 @ " + node.getId() + "，停止（主线已覆盖）");
                            break;
                        }
                    } else {
                        // 主线：含 default 分支额外遍历正线；含 contact 分支记联络线种子
                        walkDefaultMainlineIfAny(result, node, color, railwaySystemId);
                        collectContactSeedIfAny(result, node);
                    }
                }

                prevNodeId = node.getId();

                // 折返站：进站后反向驶出。销毁当前 walker，从站台铁轨以进站方向的反向重建继续。
                // 出站后如何驶入新轨由实际轨道的 tag + 原版 switcher 决定（TrackWalker 沿途处理
                // addtag/remtag），遍历器只负责在此掉头。
                if (needReverse) {
                    Vector reversed = result.direction().clone().multiply(-1);
                    log.info("  折返站，反向驶出 @ " + node.getId() + "，新方向 " + reversed);
                    walker.destroy();
                    walker = new TrackWalker(result.railBlock(), reversed);
                    walker.setLineTag(lineId);
                }

                // 终止：配置车站全部按序到齐
                if (!expectedStations.isEmpty() && stationIndex >= expectedStations.size()) {
                    log.info("线路 " + lineId + " 已走完配置的全部 " + expectedStations.size() + " 个车站");
                    break;
                }
            }

            if (nodeCount >= maxNodes) {
                log.warn("线路 " + lineId + " 达到节点上限 " + maxNodes + "，可能存在环路或配置缺失，提前停止");
            }
        } finally {
            walker.destroy();
        }
    }

    /**
     * 核对到达的车站名是否与配置车站列表的当前期望位置一致。
     * <p>
     * 一致则推进期望下标；不一致则记警告（但仍按到达继续），便于排查配置或轨道铺设问题。
     *
     * @param expected   配置车站列表
     * @param index      当前期望下标
     * @param actualName 实际到达车站名
     * @return 推进后的期望下标
     */
    private int verifyStation(List<String> expected, int index, String actualName) {
        if (expected.isEmpty()) {
            return index;
        }
        if (index >= expected.size()) {
            log.warn("  车站 " + actualName + " 超出配置车站数（配置仅 " + expected.size() + " 站），可能轨道未正确终止");
            return index;
        }
        String want = expected.get(index);
        if (!want.equals(actualName)) {
            log.warn("  车站顺序不符：配置第 " + (index + 1) + " 站应为 \"" + want + "\"，实际到达 \"" + actualName + "\"");
        }
        return index + 1;
    }

    /**
     * 若该 bcswitcher 声明了 default 分支，则用一节带 default tag 的临时矿车走一段正线子遍历，
     * 把正线坐标作为区间归入当前线路文件。
     *
     * @param switcherResult 到达 bcswitcher 的行走结果
     * @param switcherNode   bcswitcher 节点
     * @param color          当前线路颜色（正线沿用本线颜色显示）
     * @param railwaySystemId 当前营运线所属铁路系统 id（正线区间沿用之）
     */
    private void walkDefaultMainlineIfAny(TrackWalker.WalkResult switcherResult, RailNode switcherNode,
                                          String color, String railwaySystemId) {
        boolean hasDefault = false;
        for (BcSwitcherBranch branch : parseBranches(switcherResult.sign())) {
            if (LineInfo.DEFAULT_ID.equals(branch.getLineId())) {
                hasDefault = true;
                break;
            }
        }
        if (!hasDefault) {
            return;
        }

        log.info("    道岔含 default 正线，遍历正线分支");
        TrackWalker sub = new TrackWalker(switcherResult.railBlock(), switcherResult.direction());
        try {
            sub.setLineTag(LineInfo.DEFAULT_ID);
            List<LngLatAlt> coords = new ArrayList<>();
            int[] count = {0};
            TrackWalker.WalkResult subResult = sub.walkToNextNode(railBlock -> {
                if (count[0] < maxEdgesPerWalk) {
                    coords.add(toCoord(railBlock));
                    count[0]++;
                }
            });
            if (subResult.reason() == TrackWalker.StopReason.END) {
                log.info("    正线分支轨道结束");
                return;
            }
            RailNode endNode;
            if (subResult.reason() == TrackWalker.StopReason.PLATFORM) {
                String stationName = subResult.sign().getLine(2).trim();
                endNode = collector.resolveNode(RailNode.Type.STATION, subResult.railBlock(), stationName);
            } else {
                endNode = collector.resolveNode(RailNode.Type.SWITCH, subResult.railBlock(), null);
            }
            endNode.addLineId(LineInfo.DEFAULT_ID);
            endNode.addRailwaySystemId(railwaySystemId);
            if (!switcherNode.getId().equals(endNode.getId())) {
                // 正线区间用 default 作 lineId（geojson 中按 default 着色规则），但归入当前线路文件；
                // 铁路系统 id 沿用当前营运线（正线是该营运线的一部分）
                collector.recordEdge(lineId, switcherNode.getId(), endNode.getId(), LineInfo.DEFAULT_ID,
                        railwaySystemId, color, GeoUtils.simplifyLineString(coords), subResult.length());
                log.info("    正线区间 " + switcherNode.getId() + " -> " + endNode.getId());
            }
        } finally {
            sub.destroy();
        }
    }

    /**
     * 若该 bcswitcher 声明了 contact 分支，则记一个联络线种子（按道岔节点去重）。
     * 联络线本身在所有主线遍历完后统一遍历（见 {@link GeoTraversalTask}），避免重复且单独成文件。
     *
     * @param switcherResult 到达 bcswitcher 的行走结果
     * @param switcherNode   bcswitcher 节点
     */
    private void collectContactSeedIfAny(TrackWalker.WalkResult switcherResult, RailNode switcherNode) {
        if (hasContactBranch(switcherResult.sign())) {
            collector.addContactSeed(switcherNode.getId(), switcherResult.railBlock(), switcherResult.direction());
            log.info("    道岔含 contact 联络线，记为联络线种子 @ " + switcherNode.getId());
        }
    }

    /**
     * 判断 bcswitcher 控制牌是否声明了 contact 分支。
     *
     * @param sign 控制牌
     * @return true 表示含 contact 分支
     */
    private boolean hasContactBranch(com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign sign) {
        for (BcSwitcherBranch branch : parseBranches(sign)) {
            if (LineInfo.CONTACT_ID.equalsIgnoreCase(branch.getLineId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 bcswitcher 控制牌的所有分支。
     *
     * @param sign 控制牌
     * @return 分支列表
     */
    private List<BcSwitcherBranch> parseBranches(com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign sign) {
        List<BcSwitcherBranch> branches = new ArrayList<>();
        for (int i = 2; i <= 3; i++) {
            BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(sign.getLine(i));
            if (branch != null) {
                branches.add(branch);
            }
        }
        return branches;
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
