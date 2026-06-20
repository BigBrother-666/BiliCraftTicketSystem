package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.database.service.SlowdownCacheService;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.signactions.component.SlowdownPredictor;
import com.bigbrother.bilicraftticketsystem.signactions.component.SlowdownTrace;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * slowdown 减速控制牌。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [+train]
 *   slowdown
 *   [到达 platform 时的速度，支持 traincarts 的 launch 速度语法，默认 0.2 block/tick]
 * </pre>
 * 功能：列车经过本牌时，向前预测其将行驶的路径（依赖列车自身导航 / tag 选向，见
 * {@link SlowdownPredictor}），直到找到 platform 控制牌，取 slowdown 到 platform 的距离，
 * 用一段 launch 动作让列车在这段距离内平滑减速到设定速度，恰好在 platform 处达到该速度——
 * <b>只改速度不改最大速度</b>（用 distance-based launch，不传 speedlimit）。
 * <p>
 * 预测途中若先遇到另一个 slowdown 控制牌，则停止流程（减速由后者负责）；超过
 * {@code config.yml} 的 {@code slowdown-max-detect-distance}（默认 500 block）仍未找到 platform 则不减速。
 * <p>
 * 普通车站站停车，固定减速到下一个 platform。直达车跨站直达，需额外判断：预测到达的 platform 站名
 * 必须与列车<b>终点车站名</b>（{@link TrainListeners#trainTicketInfo} 的 {@link GeoRoutePath#getEndStationName()}）
 * 一致才减速，防止在中途经过的 platform 处被误减速。
 * <p>
 * 计算开销较大，结果按车种缓存到 {@link SlowdownCacheService}（数据库持久化，启动 / 重载时载入）。
 */
public class SignActionSlowdown extends SignAction {
    /**
     * 第三行留空时的默认到站速度（block/tick）。
     */
    private static final double DEFAULT_SPEED = 0.2;

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("slowdown");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isTrainSign() || !info.isAction(SignActionType.GROUP_ENTER) || !info.hasRailedMember()) {
            return;
        }
        MinecartGroup group = info.getGroup();
        MinecartMember<?> member = info.getMember();
        if (group == null || member == null) {
            return;
        }

        Block rail = info.getRails();
        if (rail == null) {
            return;
        }

        double targetSpeed = parseTargetSpeed(info.getLine(2));
        SlowdownCacheService cacheService = BiliCraftTicketSystem.plugin.getTrainDatabaseManager().getSlowdownCacheService();

        SlowdownTrace.log(group, rail, "触发 slowdown：当前速度=%.3f 最大速度=%.3f 目标到站速度=%.3f"
                .formatted(member.getRealSpeed(), group.getProperties().getSpeedLimit(), targetSpeed));

        // 直达车（带导航序列）与普通车分别处理
        if (BcRouteNavigator.hasRoute(group)) {
            handleExpress(group, member, rail, targetSpeed, cacheService);
        } else {
            handleCommon(member, rail, targetSpeed, cacheService);
        }
    }

    /**
     * 普通车减速：固定减速到下一个 platform（站站停）。先查缓存，未命中实时预测并写回。
     *
     * @param member       触发控制牌的车厢
     * @param rail         slowdown 所在铁轨方块
     * @param targetSpeed  到站目标速度（block/tick）
     * @param cacheService 缓存服务
     */
    private void handleCommon(MinecartMember<?> member, Block rail, double targetSpeed,
                              SlowdownCacheService cacheService) {
        MinecartGroup group = member.getGroup();
        SlowdownCacheService.CommonResult cached = cacheService.getCommon(rail);
        if (cached != null) {
            SlowdownTrace.log(group, rail, "普通车：缓存命中 站名=%s 距离=%.2f → 减速"
                    .formatted(cached.station(), cached.distance()));
            applySlowdown(member, cached.distance(), targetSpeed);
            return;
        }
        SlowdownTrace.log(group, rail, "普通车：缓存未命中，开始预测（最大检测距离=%.1f）"
                .formatted(MainConfig.slowdownMaxDetectDistance));
        SlowdownPredictor.Result result = SlowdownPredictor.predict(member, MainConfig.slowdownMaxDetectDistance);
        if (result.reason() == SlowdownPredictor.Reason.PLATFORM) {
            SlowdownTrace.log(group, rail, "普通车：预测到 platform 站名=%s 距离=%.2f → 写缓存并减速"
                    .formatted(result.station(), result.distance()));
            cacheService.putCommon(rail, result.station(), result.distance());
            applySlowdown(member, result.distance(), targetSpeed);
        } else {
            SlowdownTrace.log(group, rail, "普通车：预测结果=%s → 不减速".formatted(result.reason()));
        }
        // ANOTHER_SLOWDOWN / NOT_FOUND：不减速、不缓存
    }

    /**
     * 直达车减速：仅当预测到达的 platform 站名与列车终点车站名一致时减速（防止在中途站被误减速）。
     * 取不到终点信息（如重启后内存丢失）时跳过不减速。
     *
     * @param group        列车
     * @param member       触发控制牌的车厢
     * @param rail         slowdown 所在铁轨方块
     * @param targetSpeed  到站目标速度（block/tick）
     * @param cacheService 缓存服务
     */
    private void handleExpress(MinecartGroup group, MinecartMember<?> member, Block rail,
                               double targetSpeed, SlowdownCacheService cacheService) {
        BCTransitPass pass = TrainListeners.trainTicketInfo.get(group);
        if (pass == null || pass.getPathInfo() == null) {
            SlowdownTrace.log(group, rail, "直达车：取不到车票/路径信息（重启后内存丢失？）→ 不减速");
            return;
        }
        String destStation = pass.getPathInfo().getEndStationName();
        if (destStation == null) {
            SlowdownTrace.log(group, rail, "直达车：终点站名为空 → 不减速");
            return;
        }

        // 缓存按 (铁轨, 终点站名) 命中：缓存只存「到达站名 == 终点」的成功结果，命中即说明本牌为本车终点减速
        Double cachedDistance = cacheService.getExpress(rail, destStation);
        if (cachedDistance != null) {
            SlowdownTrace.log(group, rail, "直达车：缓存命中 终点=%s 距离=%.2f → 减速"
                    .formatted(destStation, cachedDistance));
            applySlowdown(member, cachedDistance, targetSpeed);
            return;
        }

        SlowdownTrace.log(group, rail, "直达车：缓存未命中 终点=%s，开始预测（最大检测距离=%.1f）"
                .formatted(destStation, MainConfig.slowdownMaxDetectDistance));
        SlowdownPredictor.Result result = SlowdownPredictor.predict(member, MainConfig.slowdownMaxDetectDistance);
        if (result.reason() == SlowdownPredictor.Reason.PLATFORM && destStation.equals(result.station())) {
            // 预测到的 platform 正是本车终点：减速并缓存
            SlowdownTrace.log(group, rail, "直达车：预测到终点 platform 站名=%s 距离=%.2f → 写缓存并减速"
                    .formatted(result.station(), result.distance()));
            cacheService.putExpress(rail, result.station(), result.distance());
            applySlowdown(member, result.distance(), targetSpeed);
        } else if (result.reason() == SlowdownPredictor.Reason.PLATFORM) {
            SlowdownTrace.log(group, rail, "直达车：预测到 platform 站名=%s（≠终点 %s，中途站）距离=%.2f → 不减速"
                    .formatted(result.station(), destStation, result.distance()));
        } else {
            SlowdownTrace.log(group, rail, "直达车：预测结果=%s → 不减速".formatted(result.reason()));
        }
        // 到达的 platform 非本车终点（中途经过）/ ANOTHER_SLOWDOWN / NOT_FOUND：不减速、不缓存
    }

    /**
     * 施加减速 launch 动作：在 {@code distance} 距离内把列车减速到 {@code targetSpeed}，
     * 恰好在 platform 处达到该速度。用 distance-based launch（不传 speedlimit），故<b>不改变最大速度</b>。
     *
     * @param member      触发控制牌的车厢（launch 动作作用于其所在列车）
     * @param distance    slowdown 到 platform 的距离（block）
     * @param targetSpeed 目标速度（block/tick）
     */
    private void applySlowdown(MinecartMember<?> member, double distance, double targetSpeed) {
        if (distance <= 0) {
            SlowdownTrace.log(member.getGroup(), null, "施加减速被跳过：距离<=0 (%.2f)".formatted(distance));
            return;
        }
        BlockFace direction = member.getDirection();
        // 清掉列车上可能存在的旧 launch 动作，避免与本次减速冲突（与 TC 原版 launcher 行为一致）
        member.getGroup().getActions().clear();
        member.getActions().addActionLaunch(direction, distance, targetSpeed);
        SlowdownTrace.log(member.getGroup(), null, "已施加减速 launch：方向=%s 距离=%.2f 目标速度=%.3f"
                .formatted(direction, distance, targetSpeed));
    }

    /**
     * 解析第三行的目标速度，支持 traincarts 的 launch 速度语法（如 {@code 0.5}、{@code 20km/h}）。
     * 留空 / 解析失败时返回默认 {@link #DEFAULT_SPEED}。
     *
     * @param line3 控制牌第三行
     * @return 目标速度（block/tick）
     */
    private double parseTargetSpeed(String line3) {
        String trimmed = line3 == null ? "" : line3.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_SPEED;
        }
        FormattedSpeed speed = FormattedSpeed.parse(trimmed, FormattedSpeed.of(DEFAULT_SPEED));
        return speed.getValue();
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.getPlayer().hasPermission("bcts.buildsign.slowdown")) {
            return false;
        }
        double targetSpeed = parseTargetSpeed(event.getLine(2));
        event.getPlayer().sendMessage(Component.text(
                "建立 slowdown 减速控制牌成功（到站速度：%.2f block/tick）".formatted(targetSpeed),
                NamedTextColor.GREEN));
        return true;
    }
}
