package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.route.geodata.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.signactions.component.BossbarManager;
import com.bigbrother.bilicraftticketsystem.signactions.component.ExpressRouteBossbar;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbarBase;
import com.bigbrother.bilicraftticketsystem.route.NodeId;
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
 *   [+train]
 *   bcswitcher
 *   &lt;方向&gt;@&lt;线路id | contact | default&gt;
 *   &lt;方向&gt;@&lt;线路id | contact | default&gt;
 * </pre>
 * 功能：
 * <ul>
 *   <li>声明该道岔可通向的线路（遍历铁轨时作为道岔节点，分叉探索各方向）。</li>
 *   <li>运行时根据列车携带的线路 id（tag）控制道岔走向。</li>
 * </ul>
 * 方向沿用 traincarts switcher 的写法（e/s/w/n 或 f/b/l/r），通过
 * {@link com.bergerkiller.bukkit.tc.Direction} 解析。
 * <p>
 * 运行时选向：列车携带有序的导航序列（{@link com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator}），
 * 本道岔按列车<b>当前应走的 lineId</b> 精确匹配分支，列车每经过一个 bcswitcher 推进一格——因此
 * 行程多次经过同一条线（如 L1…contact…L1）也能逐段消歧。列车无导航序列时回退到「列车 tag 首个匹配」
 * （兼容遍历用矿车与手动列车）。
 */
public class SignActionBcswitcher extends SignAction {

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
        BcSwitcherBranch branch = selectBranch(branches, group);
        if (branch != null) {
            info.setRailsTo(branch.getDirection());
        }
        // 没有匹配的线路，保持默认（不切换）

        // 调试追踪：把列车规划的导航序列与它实际经过的物理道岔逐个对照（开关默认关闭）
        if (SwitchTrace.isEnabled()) {
            int[] progress = BcRouteNavigator.progress(group);
            List<String> branchLineIds = new ArrayList<>();
            for (BcSwitcherBranch b : branches) {
                branchLineIds.add(b.getLineId());
            }
            SwitchTrace.log(group,
                    NodeId.ofBlock(info.getRails()),
                    progress[0], progress[1],
                    BcRouteNavigator.currentLineId(group),
                    branchLineIds,
                    branch == null ? null : branch.getLineId());
        }

        // 列车经过本道岔，导航指针推进一格（与 GeoRoutePath.switcherLineIds() 序列逐一对齐）。
        // 仅在列车带有导航序列时推进；execute 在 GROUP_ENTER 每个物理道岔触发一次，符合"每过道岔推进"。
        if (BcRouteNavigator.hasRoute(group)) {
            BcRouteNavigator.advance(group);
            // 直达车 bossbar 进度 = index / total，随道岔推进刷新
            int[] progress = BcRouteNavigator.progress(group);
            for (MinecartMember<?> member : group) {
                RouteBossbarBase bossbar = BossbarManager.get(member);
                if (bossbar instanceof ExpressRouteBossbar express) {
                    express.refreshProgress(progress[0], progress[1]);
                }
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
        if (!info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getMember().getGroup();
        BcSwitcherBranch branch = selectBranch(parseBranches(info), group);
        if (branch != null) {
            prediction.setSwitchedJunction(info.findJunction(branch.getDirection()));
        }
    }

    /**
     * 选择道岔分支：优先按列车导航序列的<b>当前 lineId</b> 精确匹配；列车无导航序列（旧车 /
     * 手动列车）时回退到原「列车 tag 集合首个匹配」逻辑，避免打断存量列车。
     *
     * @param branches 道岔分支
     * @param group    列车
     * @return 匹配的分支，无匹配返回 null
     */
    private BcSwitcherBranch selectBranch(List<BcSwitcherBranch> branches, MinecartGroup group) {
        if (group == null) {
            return null;
        }
        String currentLineId = BcRouteNavigator.currentLineId(group);
        if (currentLineId != null) {
            // 有导航序列：按当前应走的 lineId 精确选向
            for (BcSwitcherBranch branch : branches) {
                if (currentLineId.equals(branch.getLineId())) {
                    return branch;
                }
            }
            return null;
        }
        // 回退：无导航序列时按列车 tag 集合首个匹配（兼容存量 / 手动列车）
        return selectBranchByTags(branches, group.getProperties().getTags());
    }

    /**
     * 回退选向：按列车携带的 tag 集合，选择第一个匹配的道岔分支。
     *
     * @param branches 道岔分支
     * @param tags     列车携带的 tag
     * @return 匹配的分支，无匹配返回 null
     */
    private BcSwitcherBranch selectBranchByTags(List<BcSwitcherBranch> branches, Collection<String> tags) {
        for (BcSwitcherBranch branch : branches) {
            if (tags.contains(branch.getLineId())) {
                return branch;
            }
        }
        return null;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.bcswitcher")) {
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
                    "bcswitcher 控制牌格式错误，第二、三行需要 <方向>@<线路id>", NamedTextColor.RED));
            return false;
        }
        event.getPlayer().sendMessage(Component.text(
                "建立 bcswitcher 道岔控制牌成功，声明了 %d 个方向".formatted(branches.size()), NamedTextColor.GREEN));
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
