package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;

/**
 * slowdown 减速距离预测器：从 slowdown 控制牌所在位置出发，沿<b>列车将要行驶的路径</b>向前预测，
 * 直到遇到 platform 控制牌（得到减速目标距离）或另一个 slowdown 控制牌（说明减速由后者负责）。
 * <p>
 * 关键设计（与 {@link com.bigbrother.bilicraftticketsystem.route.geodata.traversal.TrackWalker} 同源）：
 * 用 {@link TrackWalkingPoint#setFollowPredictedPath(MinecartMember)} 让 TC 的寻路预测驱动行走——
 * 沿途各 bcswitcher（含原版 switcher）按<b>该列车自身</b>携带的导航序列 / tag 选向，因此预测出的路径
 * 与列车实际会走的路径一致（直达车按导航出向，普通车按结构 / lineId 选向）。
 * <p>
 * 预测只读不写：{@link com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher#predictPathFinding}
 * 不推进导航指针，故预测不会污染列车导航状态。预测在主线程执行（依赖实时轨道数据）。
 */
public final class SlowdownPredictor {

    /**
     * 预测停止原因。
     */
    public enum Reason {
        /**
         * 找到 platform 控制牌，{@link Result#distance} 为 slowdown 到 platform 的距离，
         * {@link Result#station} 为该 platform 站名。
         */
        PLATFORM,
        /**
         * 在找到 platform 前先遇到另一个 slowdown 控制牌：减速应由后者负责，本牌不减速。
         */
        ANOTHER_SLOWDOWN,
        /**
         * 达到最大检测距离仍未找到 platform，或轨道结束 / 死路。
         */
        NOT_FOUND
    }

    /**
     * 预测结果。
     *
     * @param reason   停止原因
     * @param distance slowdown 到 platform 的距离（block），仅 {@link Reason#PLATFORM} 时有意义
     * @param station  到达的 platform 站名，仅 {@link Reason#PLATFORM} 时有意义
     */
    public record Result(Reason reason, double distance, String station) {
    }

    private SlowdownPredictor() {
    }

    /**
     * 从列车当前所在铁轨向前预测，找到下一个 platform 或另一个 slowdown。
     *
     * @param member      触发 slowdown 控制牌的车厢（用于驱动预测寻路：沿其导航 / tag 选向）
     * @param maxDistance 最大检测距离（block），超出仍未找到 platform 返回 {@link Reason#NOT_FOUND}
     * @return 预测结果
     */
    public static Result predict(MinecartMember<?> member, double maxDistance) {
        RailState start = member.discoverRail();
        TrackWalkingPoint wp = new TrackWalkingPoint(start);
        // loopFilter 关闭：与 TrackWalker 一致，环线 / 折返不应被误判为断轨；改由距离上限兜底。
        wp.setLoopFilter(false);
        wp.setFollowPredictedPath(member);
        // 跳过起点铁轨（slowdown 控制牌所在铁轨），避免立即在出发点停下。
        wp.skipFirst();

        // 装预测模拟游标：直达车跨多个道岔预测时，每个 bcswitcher 逐个推进出向，而非重复读列车真实指针
        // 所指的同一步骤（普通车无导航序列，beginPredictionSim 不装游标，无影响）。
        BcRouteNavigator.beginPredictionSim(member.getGroup());
        try {
            double startMoved = wp.movedTotal;
            while (true) {
                // 前进一格铁轨
                if (!wp.moveFull()) {
                    SlowdownTrace.log(member.getGroup(), null,
                            "预测：轨道结束/死路 @ 已走 %.2f".formatted(wp.movedTotal - startMoved));
                    return new Result(Reason.NOT_FOUND, 0, null);
                }
                double traveled = wp.movedTotal - startMoved;
                if (traveled > maxDistance) {
                    SlowdownTrace.log(member.getGroup(), null,
                            "预测：超过最大检测距离 %.1f（已走 %.2f）未找到 platform".formatted(maxDistance, traveled));
                    return new Result(Reason.NOT_FOUND, 0, null);
                }

                RailLookup.TrackedSign[] signs = signsOf(wp.state.railPiece());
                if (signs == null) {
                    continue;
                }
                for (RailLookup.TrackedSign sign : signs) {
                    String type = sign.getLine(1).trim().toLowerCase();
                    if (type.startsWith("bcswitcher")) {
                        // 仅追踪：记录预测路径经过的道岔（定位路径是否走偏）
                        SlowdownTrace.log(member.getGroup(), wp.state.railBlock(),
                                "预测经过道岔 @ 已走 %.2f".formatted(traveled));
                    }
                    if (type.startsWith("slowdown")) {
                        // 先遇到另一个 slowdown：减速交给它处理，本牌停止流程
                        SlowdownTrace.log(member.getGroup(), wp.state.railBlock(),
                                "预测：先遇到另一个 slowdown @ 已走 %.2f → 本牌不负责减速".formatted(traveled));
                        return new Result(Reason.ANOTHER_SLOWDOWN, 0, null);
                    }
                    if (type.startsWith("platform")) {
                        String station = sign.getLine(2).trim();
                        SlowdownTrace.log(member.getGroup(), wp.state.railBlock(),
                                "预测：找到 platform 站名=%s @ 已走 %.2f".formatted(station, traveled));
                        return new Result(Reason.PLATFORM, traveled, station);
                    }
                }
            }
        } finally {
            BcRouteNavigator.endPredictionSim();
        }
    }

    /**
     * 取铁轨片上的控制牌数组。
     *
     * @param railPiece 铁轨片
     * @return 控制牌数组，无则 null
     */
    private static RailLookup.TrackedSign[] signsOf(RailPiece railPiece) {
        return railPiece == null ? null : railPiece.signs();
    }
}
