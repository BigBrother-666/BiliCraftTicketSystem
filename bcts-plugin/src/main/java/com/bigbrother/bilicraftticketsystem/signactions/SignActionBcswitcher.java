package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
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
 * 方向沿用 traincarts switcher 的写法（e/s/w/n 或 f/b/l/r），通过
 * {@link com.bergerkiller.bukkit.tc.Direction} 解析。进入方向写在牌头（如 {@code [+train:lf]}），
 * 由 traincarts 原生解析并在运行时只对匹配进入方向的列车触发本牌。
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
        if (group == null) {
            return;
        }

        List<BcSwitcherBranch> branches = parseBranches(info);
        // 选向优先级：带导航(直达)按 S:出向；无导航在进站道岔按结构判定的到发线出向；再回退 lineId/tag。
        String navDir = BcRouteNavigator.currentSwitchDirection(group);
        String sidingDir = navDir != null ? null : structuralSidingDir(info);
        BcSwitcherBranch branch = null;
        if (navDir != null) {
            info.setRailsTo(info.findJunction(Direction.parse(navDir)));
        } else if (sidingDir != null) {
            info.setRailsTo(info.findJunction(Direction.parse(sidingDir)));
        } else {
            branch = selectBranch(branches, group);
            if (branch != null) {
                info.setRailsTo(info.findJunction(branch.getDirection()));
            }
        }
        // 没有匹配的线路，保持默认（不切换）

        // 调试追踪：把列车规划的导航序列与它实际经过的物理道岔逐个对照（开关默认关闭）
        if (SwitchTrace.isEnabled()) {
            int[] progress = BcRouteNavigator.progress(group);
            List<String> branchLines = new ArrayList<>();
            for (BcSwitcherBranch b : branches) {
                branchLines.add(b.getDirectionStr() + ":" + String.join(";", b.getLineIds()));
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
        if (BcRouteNavigator.hasRoute(group) && BcRouteNavigator.isAtSwitchStep(group)) {
            BcRouteNavigator.advance(group);
            // 直达车 bossbar 进度随节点推进刷新
            BcRouteNavigator.refreshExpressBossbar(group);
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
        if (!info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getMember().getGroup();
        // 遍历强制选向：矿车带 bcsw_force_dir:<dir> tag 时，直接按该方向切道岔（绕过 lineId 选向）。
        // 使遍历器能在一个道岔逐个出向 fork（同一条线在此可能有多个出边，lineId 选向只会返回一个）。
        String forcedDir = readForcedDirection(group);
        if (forcedDir != null) {
            prediction.setSwitchedJunction(info.findJunction(Direction.parse(forcedDir)));
            return;
        }
        // 带导航：按当前道岔步骤的物理出向直接选向（与 execute 一致，消除共用 lineId 歧义）。
        String navDir = BcRouteNavigator.currentSwitchDirection(group);
        if (navDir != null) {
            prediction.setSwitchedJunction(info.findJunction(Direction.parse(navDir)));
            return;
        }
        // 无导航车在进站道岔：按结构判定的到发线出向选向（普通车 / 手动车一律走到发线进站停靠）。
        String sidingDir = structuralSidingDir(info);
        if (sidingDir != null) {
            prediction.setSwitchedJunction(info.findJunction(Direction.parse(sidingDir)));
            return;
        }
        // 回退：无导航 / 出向缺失 / 非进站道岔，按 lineId / tag 选 branch。
        BcSwitcherBranch branch = selectBranch(parseBranches(info), group);
        if (branch != null) {
            prediction.setSwitchedJunction(info.findJunction(branch.getDirection()));
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

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.bcswitcher")) {
            return false;
        }
        // 牌头进入方向必填：不能为空、不能为 *（任意方向），只能是 f/b/l/r/e/s/w/n 的一个或多个
        if (!hasValidEnterDirection(event.getLine(0))) {
            event.getPlayer().sendMessage(Component.text(
                    "bcswitcher 控制牌格式错误，牌头必须指定进入方向（如 [+train:lf]），不能为空或 *，"
                            + "只能用 f/b/l/r/e/s/w/n", NamedTextColor.RED));
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
     * 校验牌头是否指定了合法的进入方向。
     * <p>
     * 牌头形如 {@code [+train:lf]}，冒号后为进入方向字符。要求：非空、不含 {@code *}（任意方向），
     * 且每个字符都是 f/b/l/r/e/s/w/n 之一。
     *
     * @param headerLine 牌头原始文本（第一行）
     * @return true 表示进入方向合法
     */
    private boolean hasValidEnterDirection(String headerLine) {
        if (headerLine == null) {
            return false;
        }
        int colon = headerLine.indexOf(':');
        if (colon < 0 || colon == headerLine.length() - 1) {
            return false;
        }
        // 取冒号后到结尾，去掉可能的右括号与空白
        String dirs = headerLine.substring(colon + 1).replace("]", "").trim().toLowerCase();
        if (dirs.isEmpty() || dirs.contains("*")) {
            return false;
        }
        for (int i = 0; i < dirs.length(); i++) {
            if ("fblreswn".indexOf(dirs.charAt(i)) < 0) {
                return false;
            }
        }
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
