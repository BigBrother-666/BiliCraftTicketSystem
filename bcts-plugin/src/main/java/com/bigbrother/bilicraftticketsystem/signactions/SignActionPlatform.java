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
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
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
 *   <li>进站 / 出站显示提示消息或音效（提示内容来自 routes.yml 中列车所属线路）。</li>
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
            // 进站提示
            if (enabled.contains(PlatformFeature.ARRIVAL_NOTICE)) {
                sendNotice(group, stationName, true);
            }
            // bossbar 进站推进（普通车：每站停，显示滚动站名带）
            if (enabled.contains(PlatformFeature.BOSSBAR)) {
                updateBossbarOnArrive(group, stationName);
            }
        } else if (info.isAction(SignActionType.GROUP_LEAVE)) {
            // 空车销毁/直达车终点强制销毁
            if (enabled.contains(PlatformFeature.DESTROY) && (isEmptyTrain(group) ||
                    BcRouteNavigator.hasRoute(group) && BcRouteNavigator.finished(group))) {
                group.destroy();
                return;
            }
            // 出站提示
            if (enabled.contains(PlatformFeature.DEPARTURE_NOTICE)) {
                sendNotice(group, stationName, false);
            }
            // bossbar 出站滚动到下一站
            if (enabled.contains(PlatformFeature.BOSSBAR)) {
                updateBossbarOnLeave(group);
            }
        }
    }

    /**
     * 列车进站时推进 bossbar。
     * <p>
     * 直达车 bossbar（{@link ExpressRouteBossbar}）
     * 跨越中间站、不在此更新（其 onArrive 为 no-op）；普通车按当前线路 lineId 取/建
     * {@link CommonRouteBossbar}，换乘到别的线路时重建。
     *
     * @param group       列车
     * @param stationName 当前车站名
     */
    private void updateBossbarOnArrive(MinecartGroup group, String stationName) {
        LineInfo line = resolveLine(group.getProperties().getTags());
        for (MinecartMember<?> member : group) {
            RouteBossbarBase bossbar = BossbarManager.get(member);
            if (bossbar instanceof CommonRouteBossbar common) {
                // 换乘到别的线路：重建为新线路的 bossbar
                if (line != null && !line.getId().equals(common.getLineId())) {
                    BossbarManager.remove(member);
                    bossbar = null;
                }
            } else if (bossbar != null) {
                // 直达车 bossbar：不在站台牌处更新
                bossbar.onArrive(stationName);
                continue;
            }
            if (bossbar == null) {
                // 普通车首次进站：创建该线路的 bossbar
                if (line == null) {
                    continue;
                }
                CommonRouteBossbar common = new CommonRouteBossbar(line);
                if (common.getBossBar() == null) {
                    continue;
                }
                BossbarManager.put(member, common);
                bossbar = common;
            }
            bossbar.onArrive(stationName);
        }
    }

    /**
     * 列车出站时让普通车 bossbar 滚动到下一站。
     *
     * @param group 列车
     */
    private void updateBossbarOnLeave(MinecartGroup group) {
        for (MinecartMember<?> member : group) {
            RouteBossbarBase bossbar = BossbarManager.get(member);
            if (bossbar instanceof CommonRouteBossbar) {
                bossbar.onLeave();
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
    private void sendNotice(MinecartGroup group, String stationName, boolean arrival) {
        LineInfo line = resolveLine(group.getProperties().getTags());
        if (line == null) {
            return;
        }
        Collection<String> notices = arrival ? line.getNoticeArrival() : line.getNoticeDeparture();
        if (notices.isEmpty()) {
            return;
        }
        for (MinecartMember<?> member : group) {
            NoticePlayer.play(member, notices, line, stationName);
        }
    }

    /**
     * 从列车携带的 tag 中找出对应的线路信息（首个在 routes.yml 中存在的 lineId）。
     *
     * @param trainTags 列车 tag
     * @return 线路信息，找不到返回 null
     */
    private LineInfo resolveLine(Collection<String> trainTags) {
        for (String tag : trainTags) {
            LineInfo info = LineConfig.get(tag);
            if (info != null && !info.isSpecial()) {
                return info;
            }
        }
        return null;
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
