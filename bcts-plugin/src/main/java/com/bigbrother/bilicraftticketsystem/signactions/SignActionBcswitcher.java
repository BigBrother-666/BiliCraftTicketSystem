package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.route.NodeId;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoNode;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.SwitchTrace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * bcswitcher 道岔控制牌。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [+train:&lt;进入方向&gt;]              ← 牌头进入方向必填（f/b/l/r/e/s/w/n 一个或多个），不能空、不能 *
 *   bcswitcher
 *   &lt;出向&gt;@&lt;线路id&gt;;[线路id]...      ← 一个出向可挂多条共用线路（分号分隔）
 *   &lt;出向&gt;@&lt;线路id&gt;;[线路id]...
 * </pre>
 * 功能：
 * <ul>
 *   <li>声明该道岔的<b>入边方向</b>（牌头）与各<b>出边</b>（第三、四行，每个出向归属一条或多条线路）。
 *       遍历铁轨时作为有向图的道岔节点：到达方向匹配牌头进入方向，按出向逐条展开。</li>
 *   <li>运行时根据列车<b>当前应走的 lineId</b> 控制道岔走向。</li>
 * </ul>
 * 方向沿用 traincarts switcher 的写法：绝对方向 e/s/w/n、相对牌子的 f/b/l/r，或<b>道岔节点名</b>
 * （TCCoasters 云轨节点用数字标记各出向，如 {@code 1@pr-cw}）。出向统一交给 {@link #findJunction}
 * 解析（先按节点名匹配、再退回方向解析），与 traincarts 原生 switcher 牌一致。进入方向写在牌头
 * （如 {@code [+train:lf]}、云轨可写 {@code [+train:1]}），由 traincarts 原生解析并在运行时只对匹配
 * 进入方向的列车触发本牌。
 * <p>
 * 运行时选向：列车携带有序的导航序列（{@link com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator}），
 * 本道岔按列车<b>当前道岔步骤的物理出向</b>直接 {@code setRailsTo}，列车每经过一个 bcswitcher 推进一格——
 * 因此行程多次经过同一条线也能逐段消歧，且进站道岔正线 / 到发线共用同一 lineId 时也能按出向区分。
 * 列车无导航序列时回退到「列车 tag / 线路属性首个匹配」（兼容遍历用矿车与手动列车）。
 * <p>
 * 遍历强制选向：遍历器（{@link com.bigbrother.bilicraftticketsystem.route.geodata.traversal.TrackWalker}）
 * 在道岔节点 fork 时，给矿车打一个 {@link #FORCE_DIR_TAG_PREFIX} 前缀的 tag 指定出向，
 * {@link #predictPathFinding} 读到即按该方向强制走（优先级高于 lineId 选向），使「同一条线在一个道岔
 * 有多个出边」的情形能逐个出向走到。
 */
public class SignActionBcswitcher extends SignAction {
    /**
     * 遍历强制出向 tag 前缀。遍历器给矿车打形如 {@code bcsw_force_dir:e} 的 tag，
     * {@link #predictPathFinding} 读到后强制把道岔切到该方向（绕过 lineId 选向）。
     */
    public static final String FORCE_DIR_TAG_PREFIX = "bcsw_force_dir:";

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("bcswitcher");
    }

    /**
     * 解析控制牌第二、三行的所有道岔分支。
     *
     * @param info 控制牌事件
     * @return 分支列表（已过滤无效行）
     */
    private List<BcSwitcherBranch> parseBranches(SignActionEvent info) {
        List<BcSwitcherBranch> branches = new ArrayList<>();
        for (int i = 2; i <= 3; i++) {
            BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(info.getLine(i));
            if (branch != null) {
                branches.add(branch);
            }
        }
        return branches;
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isTrainSign() || !info.isAction(SignActionType.GROUP_ENTER)) {
            return;
        }
        if (!info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getGroup();
        if (group == null || !info.isWatchedDirection(info.getCartEnterDirection())) {
            return;
        }

        // 一个铁轨方块可能挂多个 bcswitcher（或与其它控制牌共块），会各自触发一次 GROUP_ENTER。
        // 若本物理节点上次已推进过，说明同块的另一块牌已完成选向 + 推进：此时指针已前移，
        // 再读当前步骤会读成下一步、切错道岔，故整体跳过。
        String nodeId = info.getRails() == null ? null : NodeId.ofBlock(info.getRails());
        if (BcRouteNavigator.hasRoute(group) && BcRouteNavigator.alreadyAdvancedAt(group, nodeId)) {
            return;
        }
        publishRideEvent(group, nodeId);

        // 运行时节点对齐校验（仅带导航的快速车）：把「实际到达的物理道岔节点」与「当前步骤应到的节点」比对。
        // 防止玩家新放置的 bcswitcher / platform 在重新遍历前被列车经过、污染导航指针，或列车走错方向。
        boolean navUsable = true;
        if (BcRouteNavigator.hasRoute(group) && nodeId != null && !BcRouteNavigator.finished(group)) {
            String expected = BcRouteNavigator.currentStepNodeId(group);
            if (expected != null && !expected.equals(nodeId)) {
                if (GeoRouteEngine.getGraph().getNode(nodeId) == null) {
                    // 节点不在图中 → 玩家新放置、尚未重新遍历的道岔：整体跳过（不选向、不推进，指针不动）。
                    SwitchTrace.logSkip(group, nodeId, expected);
                    return;
                }
                // 节点在图中但顺序不符 → 列车走错方向：按终点站名重算路线（可到终点任意站台）。
                String end = BcRouteNavigator.endStationName(group);
                boolean ok = BcRouteNavigator.reroute(group, nodeId);
                SwitchTrace.logReroute(group, nodeId, end, ok);
                // 重算成功：指针已归零、新序列首步即本节点，下面照常按新 navDir 选向 + 推进。
                // 重算失败：保留旧指针（不动），本道岔回退到 lineId / tag 选向——按错误步骤的出向选必然切错。
                if (!ok) {
                    navUsable = false;
                }
            }
        }

        List<BcSwitcherBranch> branches = parseBranches(info);
        // 选向优先级：带导航(直达)按 S:出向；无导航在进站道岔按结构判定的到发线出向；再回退 lineId/tag。
        String navDir = navUsable ? BcRouteNavigator.currentSwitchDirection(group) : null;
        String sidingDir = navDir != null ? null : structuralSidingDir(info);
        BcSwitcherBranch branch = null;
        if (navDir != null) {
            setRailsTo(info, navDir);
        } else if (sidingDir != null) {
            setRailsTo(info, sidingDir);
        } else {
            branch = selectBranch(branches, group);
            if (branch != null) {
                setRailsTo(info, branch.getDirectionStr());
            }
        }
        // 没有匹配的线路，保持默认（不切换）

        // 调试追踪：把列车规划的导航序列与它实际经过的物理道岔逐个对照（开关默认关闭）
        if (SwitchTrace.isEnabled()) {
            int[] progress = BcRouteNavigator.progress(group);
            List<String> branchLines = new ArrayList<>();
            for (BcSwitcherBranch b : branches) {
                branchLines.add(b.getDirectionStr() + "@" + String.join(";", b.getLineIds()));
            }
            String chosen = navDir != null ? navDir
                    : (sidingDir != null ? sidingDir
                    : (branch == null ? null : String.join(";", branch.getLineIds())));
            SwitchTrace.log(group,
                    NodeId.ofBlock(info.getRails()),
                    progress[0], progress[1],
                    navDir == null ? BcLineIdProperty.read(group) : navDir,
                    branchLines,
                    chosen);
        }

        // 列车经过本道岔，导航指针推进一格（节点步骤序列里 bcswitcher 对应一个 S 步骤）。
        // 仅在列车带有导航序列且当前指针确实指向道岔步骤时推进，避免与 platform 推进错位。
        // 按节点 id 去重：同一铁轨方块上多块控制牌重复触发时只推进一次。
        if (navUsable && BcRouteNavigator.hasRoute(group) && BcRouteNavigator.isAtSwitchStep(group)) {
            if (BcRouteNavigator.advance(group, nodeId)) {
                // 直达车 bossbar 进度随节点推进刷新
                BcRouteNavigator.refreshExpressBossbar(group);
            }
        }
    }

    /**
     * 路径预测：遍历铁轨（{@link com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint#setFollowPredictedPath}）
     * 和列车寻路时，traincarts 会调用本方法询问道岔走向。逻辑与 {@link #execute} 一致：按列车
     * 当前应走的 lineId 选向。
     * <p>
     * <b>关键：预测只读当前 lineId 选向，绝不推进指针</b>——预测会被 TC 多次/提前调用，
     * 若在此推进会污染导航状态。推进只在 {@link #execute}（真实经过）里做。
     *
     * @param info       控制牌事件（member 为预测中的列车）
     * @param prediction 路径预测事件
     */
    @Override
    public void predictPathFinding(SignActionEvent info, PathPredictEvent prediction) {
        if (!info.hasRailedMember() || !info.isWatchedDirection(info.getCartEnterDirection())) {
            return;
        }
        MinecartGroup group = info.getMember().getGroup();
        // 遍历强制选向：矿车带 bcsw_force_dir:<dir> tag 时，直接按该方向切道岔（绕过 lineId 选向）。
        // 使遍历器能在一个道岔逐个出向 fork（同一条线在此可能有多个出边，lineId 选向只会返回一个）。
        String forcedDir = readForcedDirection(group);
        if (forcedDir != null) {
            setSwitchedJunction(info, prediction, forcedDir);
            return;
        }
        // 带导航：按当前道岔步骤的物理出向直接选向（与 execute 一致，消除共用 lineId 歧义）。
        // 预测模拟进行中（如 slowdown 跨多道岔预测）：按道岔铁轨方块消重逐格取出向，使下一个道岔读到
        // 下一步出向、同一方块的多块牌读同一格（不超前），而非重复读列车真实指针所指的同一步。
        String navDir;
        if (BcRouteNavigator.isPredictionSim()) {
            String blockKey = info.getRails() == null ? null : NodeId.ofBlock(info.getRails());
            navDir = BcRouteNavigator.predictionSwitchDirection(blockKey);
        } else {
            // 节点对齐校验（只读，不改导航）：预测中的道岔节点与当前步骤应到的节点不符时，不按当前步骤出向
            // 选向（那是别的道岔的出向，会切错），回退到 lineId / tag。真正的跳过 / 重算在 execute 里做。
            String blockKey = info.getRails() == null ? null : NodeId.ofBlock(info.getRails());
            String expected = BcRouteNavigator.currentStepNodeId(group);
            boolean aligned = expected == null || blockKey == null || expected.equals(blockKey);
            navDir = aligned ? BcRouteNavigator.currentSwitchDirection(group) : null;
        }
        if (navDir != null) {
            setSwitchedJunction(info, prediction, navDir);
            return;
        }
        // 无导航车在进站道岔：按结构判定的到发线出向选向（普通车 / 手动车一律走到发线进站停靠）。
        String sidingDir = structuralSidingDir(info);
        if (sidingDir != null) {
            setSwitchedJunction(info, prediction, sidingDir);
            return;
        }
        // 回退：无导航 / 出向缺失 / 非进站道岔，按 lineId / tag 选 branch。
        BcSwitcherBranch branch = selectBranch(parseBranches(info), group);
        if (branch != null) {
            setSwitchedJunction(info, prediction, branch.getDirectionStr());
        }
    }

    /**
     * 解析一个出向字符串为本道岔铁轨上的具体道岔分叉（junction）。
     * <p>
     * 委托 traincarts 的 {@code SignActionEvent.findJunction(String)}，其解析顺序与原生 switcher 牌一致：
     * <ol>
     *   <li>先按<b>道岔节点名</b>精确匹配（普通铁轨的节点名就是 {@code n/e/s/w}；<b>TCCoasters 云轨的
     *       节点名是数字</b>，如 {@code 1}、{@code 2}）；</li>
     *   <li>再识别 {@code c/continue}（沿进入方向续行）与 {@code i/rev/reverse/inverse}（反向）；</li>
     *   <li>最后退回 {@link com.bergerkiller.bukkit.tc.Direction} 解析，支持相对方向 {@code f/b/l/r}
     *       与绝对方向别名。</li>
     * </ol>
     * 因此本方法同时支持普通铁轨的方向写法与 TCC 云轨的数字节点写法。绝不可改回
     * {@code findJunction(Direction.parse(dir))}：那样会跳过节点名匹配，数字出向被解析成
     * {@code Direction.NONE}，云轨道岔无法切换。
     *
     * @param info 控制牌事件
     * @param dir  出向字符串（方向或道岔节点名）
     * @return 对应的道岔分叉；出向为空或该铁轨上无此分叉时返回 null
     */
    private RailJunction findJunction(SignActionEvent info, String dir) {
        if (dir == null || dir.isEmpty()) {
            return null;
        }
        return info.findJunction(dir);
    }

    /**
     * 按出向切换本道岔铁轨走向（运行时真实经过）。出向无法解析成分叉时保持默认，不切换。
     *
     * @param info 控制牌事件
     * @param dir  出向字符串（方向或 TCC 节点名）
     */
    private void setRailsTo(SignActionEvent info, String dir) {
        RailJunction junction = findJunction(info, dir);
        if (junction != null) {
            info.setRailsTo(junction);
        }
    }

    /**
     * 按出向设置<b>预测寻路</b>的道岔走向。出向无法解析成分叉时不干预预测（由 TC 走默认分叉）。
     *
     * @param info       控制牌事件
     * @param prediction 路径预测事件
     * @param dir        出向字符串（方向或 TCC 节点名）
     */
    private void setSwitchedJunction(SignActionEvent info, PathPredictEvent prediction, String dir) {
        RailJunction junction = findJunction(info, dir);
        if (junction != null) {
            prediction.setSwitchedJunction(junction);
        }
    }

    /**
     * 读取列车携带的遍历强制出向（{@link #FORCE_DIR_TAG_PREFIX} 前缀 tag）。
     *
     * @param group 列车
     * @return 强制出向字符串（如 "e"），无则 null
     */
    private String readForcedDirection(MinecartGroup group) {
        if (group == null) {
            return null;
        }
        for (String tag : group.getProperties().getTags()) {
            if (tag.startsWith(FORCE_DIR_TAG_PREFIX)) {
                return tag.substring(FORCE_DIR_TAG_PREFIX.length());
            }
        }
        return null;
    }

    /**
     * 判断是否是进站道岔（也就是包含正线的车站的进站道岔）
     *
     * @param info 控制牌事件
     * @return 是否是进站道岔（也就是包含正线的车站的进站道岔）
     */
    private boolean isEnterSwitcher(SignActionEvent info) {
        List<BcSwitcherBranch> bcSwitcherBranches = parseBranches(info);
        if (bcSwitcherBranches.size() < 2) {
            // 只有一个分支
            return false;
        }
        String propLineId = BcLineIdProperty.read(info.getGroup());
        if (propLineId != null && !propLineId.isEmpty()) {
            for (BcSwitcherBranch bcSwitcherBranch : bcSwitcherBranches) {
                if (!bcSwitcherBranch.getLineIds().contains(propLineId)) {
                    // 某个分支不包含当前列车的ID
                    return false;
                }
            }
        }
        // 全部分支包含列车ID
        return true;
    }

    /**
     * 取本道岔（若为「有正线的进站道岔」）的到发线物理出向，供无导航列车（普通车 / 手动车）一律走
     * 到发线进站停靠。按运行时路由图结构判定：进站道岔节点有通往车站的出边时，返回通往
     * 车站那条出边的物理出向。
     * <p>
     * 节点 id 由铁轨方块坐标确定性生成（{@link NodeId#ofBlock}），与遍历建图同源，可直接在图中查到。
     * 非进站道岔 / 图未加载 / 旧 geojson 无出向记录时返回 null（调用方回退到 lineId / tag 选向）。
     *
     * @param info 控制牌事件
     * @return 到发线物理出向（如 "s"）；不适用返回 null
     */
    private String structuralSidingDir(SignActionEvent info) {
        if (info.getRails() == null) {
            return null;
        }
        if (!isEnterSwitcher(info)) {
            return null;
        }
        GeoNode node = GeoRouteEngine.getGraph().getNode(NodeId.ofBlock(info.getRails()));
        return node == null ? null : GeoRouteEngine.getGraph().sidingDirectionOfMainlineSwitch(node);
    }

    /**
     * 回退选向（仅无导航序列时使用）：普通车 / 手动列车 / 遍历矿车没有导航出向，按线路属性 / tag 匹配
     * 第一个归属出向，避免打断存量列车。带导航的列车不走这里——它们在 execute / predict 里按
     * {@link BcRouteNavigator#currentSwitchDirection} 的物理出向直接选向。
     *
     * @param branches 道岔出向
     * @param group    列车
     * @return 匹配的出向，无匹配返回 null
     */
    private BcSwitcherBranch selectBranch(List<BcSwitcherBranch> branches, MinecartGroup group) {
        if (group == null) {
            return null;
        }
        // 普通车按 BcLineIdProperty 选；遍历临时矿车无该 property，回退按 tag 选（walker 用 setLineTag 加 tag）。
        String propLineId = BcLineIdProperty.read(group);
        if (propLineId != null && !propLineId.isEmpty()) {
            for (BcSwitcherBranch branch : branches) {
                if (branch.hasLineId(propLineId)) {
                    return branch;
                }
            }
        }
        return selectBranchByTags(branches, group.getProperties().getTags());
    }

    /**
     * 回退选向：按列车携带的 tag 集合，选择第一个其归属线路被 tag 命中的出向。
     *
     * @param branches 道岔出向
     * @param tags     列车携带的 tag
     * @return 匹配的出向，无匹配返回 null
     */
    private BcSwitcherBranch selectBranchByTags(List<BcSwitcherBranch> branches, Collection<String> tags) {
        for (BcSwitcherBranch branch : branches) {
            for (String lineId : branch.getLineIds()) {
                if (tags.contains(lineId)) {
                    return branch;
                }
            }
        }
        return null;
    }

    private void publishRideEvent(MinecartGroup group, String nodeId) {
        var webLink = BiliCraftTicketSystem.plugin.getWebLink();
        if (webLink != null) {
            webLink.getRideEventPublisher().publish(group, nodeId, null);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.bcswitcher")) {
            return false;
        }
        // 牌头进入方向必填：不能为空、不能为 *（任意方向），只能是 f/b/l/r/e/s/w/n 或 TCC 数字节点名
        if (!GeoUtils.hasValidBcSwitcherEnterDirection(event.getLine(0))) {
            event.getPlayer().sendMessage(Component.text(
                    "bcswitcher 控制牌格式错误，牌头必须指定进入方向（如 [+train:lf]），不能为空或 *，"
                            + "只能用 f/b/l/r/e/s/w/n 或 TCC 道岔节点名（数字，如 [+train:1]）", NamedTextColor.RED));
            return false;
        }
        List<BcSwitcherBranch> branches = new ArrayList<>();
        for (int i = 2; i <= 3; i++) {
            BcSwitcherBranch branch = GeoUtils.parseBcSwitcherBranch(event.getLine(i));
            if (branch != null) {
                branches.add(branch);
            }
        }
        if (branches.isEmpty()) {
            event.getPlayer().sendMessage(Component.text(
                    "bcswitcher 控制牌格式错误，第三、四行需要 <方向>@<线路id>[;线路id...]", NamedTextColor.RED));
            return false;
        }
        event.getPlayer().sendMessage(Component.text(
                "建立 bcswitcher 道岔控制牌成功，声明了 %d 个出向".formatted(branches.size()), NamedTextColor.GREEN));
        return true;
    }

    /**
     * 声明本控制牌会切换铁轨方向，使 traincarts 在寻路时把它当作道岔处理。
     *
     * @param info 控制牌事件
     * @return 固定 true
     */
    @Override
    public boolean isRailSwitcher(SignActionEvent info) {
        return true;
    }
}
