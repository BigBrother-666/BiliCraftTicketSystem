package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bigbrother.bilicraftticketsystem.signactions.component.*;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.SwitchTrace;
import com.bigbrother.bilicraftticketsystem.route.NodeId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * platform 站台控制牌。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [+train]
 *   platform
 *   &lt;当前车站名&gt;
 *   [ 空格分隔的**不使用的**可选功能 ]
 * </pre>
 * 可选功能（反选，第四行列出要禁用的）：BB(BossBar)、DY(DestroY 空车销毁)、
 * AN(Arrival-Notice 进站提示)、DN(Departure-Notice 出站提示)。
 * <p>
 * 功能：
 * <ul>
 *   <li>显示 bossbar（进度代表列车行程，环线保持满进度）—— 进度逻辑依赖路由引擎，
 *       将在 Phase 4 接入；本类先处理 bossbar 之外的功能。</li>
 *   <li>空列车离开时自动销毁。</li>
 *   <li>进站 / 出站显示提示消息或音效（提示内容来自 railway_routes.yml 中列车所属线路）。</li>
 *   <li>遍历铁轨时作为车站节点。</li>
 *   <li>每个站台只能放置一个控制牌。</li>
 * </ul>
 */
public class SignActionPlatform extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("platform");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isTrainSign() || !info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getGroup();
        if (group == null) {
            return;
        }

        Set<PlatformFeature> enabled = PlatformFeature.parseEnabled(info.getLine(3));
        String stationName = info.getLine(2).trim();

        if (info.isAction(SignActionType.GROUP_ENTER)) {
            // 调试追踪：列车进入站台
            if (SwitchTrace.isEnabled()) {
                int[] progress = BcRouteNavigator.progress(group);
                SwitchTrace.logPlatform(group, NodeId.ofBlock(info.getRails()), stationName,
                        progress[0], progress[1], "进站");
            }
            // 进站提示（仅普通车）
            if (enabled.contains(PlatformFeature.ARRIVAL_NOTICE)&& !BcRouteNavigator.hasRoute(group)) {
                sendNotice(group, stationName, true, null);
            }
            // bossbar 进站刷新（普通车滚动站名带 / 直达车进度与终到文案，统一多态调用）
            updateBossbarOnArrive(group, stationName, enabled.contains(PlatformFeature.BOSSBAR));
        } else if (info.isAction(SignActionType.GROUP_LEAVE)) {
            // 空车销毁/直达车终点强制销毁
            if (enabled.contains(PlatformFeature.DESTROY) && (isEmptyTrain(group) ||
                    BcRouteNavigator.hasRoute(group) && BcRouteNavigator.finished(group))) {
                group.destroy();
                return;
            }
            // 导航：当前步骤为车站则推进指针（使无正线中途站也能推进到终点）。
            // 推进属于导航逻辑，不受 BOSSBAR 功能位影响；bossbar 显示刷新交由 onLeave 多态处理。
            // 按节点 id 去重：同一铁轨方块挂多块控制牌重复触发时只推进一次。
            if (BcRouteNavigator.hasRoute(group)) {
                String nodeId = info.getRails() == null ? null : NodeId.ofBlock(info.getRails());
                if (SwitchTrace.isEnabled()) {
                    int[] progress = BcRouteNavigator.progress(group);
                    SwitchTrace.logPlatform(group, nodeId, stationName,
                            progress[0], progress[1], "出站");
                }
                BcRouteNavigator.advance(group, nodeId);
            }
            // 运行时转线（仅普通车）：离开本线终点站且本线配置了 nextLineId 时，把列车所属线路改为
            // 下一线。出站提示仍属本线（到达本线终点），但下一站用转线后的进入站名；bossbar 重建为
            // 新线路并定位到进入站。entryStation 为 null 表示本站非「终点 + 转线」，按常规处理。
            String entryStation = null;
            if (!BcRouteNavigator.hasRoute(group)) {
                entryStation = transferLineIfTerminus(group, stationName);
            }
            // 出站提示（仅普通车）。转线时下一站用进入站名覆盖（不在本线车站列表中，无法推算）。
            if (enabled.contains(PlatformFeature.DEPARTURE_NOTICE) && !BcRouteNavigator.hasRoute(group)) {
                sendNotice(group, stationName, false,
                        entryStation != null && !entryStation.isEmpty() ? entryStation : null);
            }
            // bossbar 出站刷新：转线则重建为新线路并定位到进入站，否则常规 onLeave 多态刷新。
            if (entryStation != null) {
                rebuildBossbarForTransfer(group, entryStation, enabled.contains(PlatformFeature.BOSSBAR));
            } else {
                updateBossbarOnLeave(group);
            }
        }
    }

    /**
     * 列车进站时刷新 bossbar：统一对每个车厢的 bossbar 多态调用 {@code onArrive}，不区分类型。
     * <p>
     * 唯一的类型相关处理是<b>生命周期</b>：普通车 bossbar 在首次进站时由 platform 懒创建
     * （仅当本站启用 BOSSBAR 功能位时），换乘线路时重建；直达车 bossbar 已在上车时创建、
     * 此处只刷新不创建。
     *
     * @param group       列车
     * @param stationName 当前车站名
     * @param bbEnabled   本站是否启用 BOSSBAR 功能位（仅影响普通车 bossbar 的懒创建）
     */
    private void updateBossbarOnArrive(MinecartGroup group, String stationName, boolean bbEnabled) {
        LineInfo line = resolveLine(group);
        for (MinecartMember<?> member : group) {
            RouteBossbarBase bossbar = BossbarManager.get(member);
            // 生命周期：普通车 bossbar 换乘到别的线路需重建
            if (bossbar != null && bossbar.needsRebuild(line)) {
                BossbarManager.remove(member);
                bossbar = null;
            }
            // 生命周期：普通车首次进站 / 重建后懒创建（需本站启用 BB 且能识别线路）
            if (bossbar == null && bbEnabled && line != null) {
                CommonRouteBossbar common = new CommonRouteBossbar(line);
                if (common.getBossBar() != null) {
                    BossbarManager.put(member, common);
                    bossbar = common;
                }
            }
            if (bossbar != null) {
                bossbar.onArrive(group, stationName);
            }
        }
    }

    /**
     * 列车出站时刷新 bossbar：对每个车厢的 bossbar 多态调用 {@code onLeave}，不区分类型。
     *
     * @param group 列车
     */
    private void updateBossbarOnLeave(MinecartGroup group) {
        for (MinecartMember<?> member : group) {
            RouteBossbarBase bossbar = BossbarManager.get(member);
            if (bossbar != null) {
                bossbar.onLeave(group);
            }
        }
    }

    /**
     * 判断列车是否为空车（没有任何玩家乘客）。
     *
     * @param group 列车
     * @return true 表示空车
     */
    private boolean isEmptyTrain(MinecartGroup group) {
        for (MinecartMember<?> member : group) {
            if (member.getEntity() != null && !member.getEntity().getPlayerPassengers().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据列车所属线路（携带的 lineId tag）发送进站 / 出站提示。
     *
     * @param group       列车
     * @param stationName 当前车站名
     * @param arrival     true 进站，false 出站
     */
    private void sendNotice(MinecartGroup group, String stationName, boolean arrival, String nextStationOverride) {
        LineInfo line = resolveLine(group);
        if (line == null) {
            return;
        }
        Collection<String> notices = arrival ? line.getNoticeArrival() : line.getNoticeDeparture();
        if (notices.isEmpty()) {
            return;
        }
        for (MinecartMember<?> member : group) {
            NoticePlayer.play(member, notices, line, stationName, nextStationOverride);
        }
    }

    /**
     * 运行时转线：普通车离开本线<b>终点站</b>且本线配置了 {@code nextLineId} 时，把列车所属线路
     * （{@link BcLineIdProperty}）改为下一条线路，使其转入新线继续运行。
     * <p>
     * 仅当当前站名正是本线 bossbar 车站列表的最后一站时触发。终点站可能不在下一线车站列表中
     * （如 S1-D 转入 S2 线、下一站 S2-B），故转线在<b>出站</b>时进行：到达提示仍属本线终点，
     * 出站后才转入新线。下一线不存在时不改写（避免列车丢失线路标识）。
     *
     * @param group       列车
     * @param stationName 当前离开的车站名
     * @return 转线后在新线的进入站名（可能为空串表示未指定）；本站非「终点 + 转线」时返回 null
     */
    private String transferLineIfTerminus(MinecartGroup group, String stationName) {
        LineInfo line = resolveLine(group);
        if (line == null || line.getNextLineId() == null) {
            return null;
        }
        List<String> stations = line.getBossbarStations();
        if (stations.isEmpty() || !stations.getLast().equals(stationName)) {
            return null;
        }
        LineInfo next = LineConfig.get(line.getNextLineId());
        if (next == null) {
            return null;
        }
        BcLineIdProperty.write(group, next.getId());
        String entry = line.getNextLineEntryStation();
        return entry == null ? "" : entry;
    }

    /**
     * 转线后重建 bossbar：移除旧线 bossbar，按新线路懒创建并定位到进入站
     * （{@link CommonRouteBossbar#approach}）。仅当本站启用 BOSSBAR 功能位且能识别新线路时创建。
     *
     * @param group        列车
     * @param entryStation 转线后在新线的进入站名
     * @param bbEnabled    本站是否启用 BOSSBAR 功能位
     */
    private void rebuildBossbarForTransfer(MinecartGroup group, String entryStation, boolean bbEnabled) {
        LineInfo line = resolveLine(group);
        for (MinecartMember<?> member : group) {
            // 移除旧线 bossbar（换乘到别的线路必重建）
            RouteBossbarBase old = BossbarManager.get(member);
            if (old != null) {
                BossbarManager.remove(member);
            }
            if (bbEnabled && line != null) {
                CommonRouteBossbar common = new CommonRouteBossbar(line);
                if (common.getBossBar() != null) {
                    BossbarManager.put(member, common);
                    common.approach(entryStation);
                }
            }
        }
    }

    /**
     * 从列车所属线路 property（{@link BcLineIdProperty}）解析线路信息。
     *
     * @param group 列车
     * @return 线路信息，找不到返回 null
     */
    private LineInfo resolveLine(MinecartGroup group) {
        return LineConfig.get(BcLineIdProperty.read(group));
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.platform")) {
            return false;
        }
        if (event.getLine(2).trim().isEmpty()) {
            event.getPlayer().sendMessage(Component.text(
                    "platform 控制牌第三行需要填写当前车站名", NamedTextColor.RED));
            return false;
        }
        Set<PlatformFeature> enabled = PlatformFeature.parseEnabled(event.getLine(3));
        event.getPlayer().sendMessage(Component.text(
                "建立 platform 站台控制牌成功（车站：%s，启用功能数：%d）".formatted(event.getLine(2).trim(), enabled.size()),
                NamedTextColor.GREEN));
        return true;
    }

}
