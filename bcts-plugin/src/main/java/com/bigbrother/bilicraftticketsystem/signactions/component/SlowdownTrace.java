package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;

/**
 * slowdown 减速控制牌调试追踪：一个会话级开关，开启后把列车经过 slowdown 控制牌时的
 * 预测过程与减速决策打到控制台，用于定位「减速距离不对 / 该减速却没减 / 减错位置」等问题。
 * <p>
 * 输出涵盖：触发车种与终点站、缓存命中与否、预测停止原因（platform / 另一 slowdown / 未找到）、
 * 预测到的站名与距离、直达车终点匹配判断、最终是否施加减速及目标速度。
 * <p>
 * 纯内存开关，随插件重启复位，不持久化。默认关闭，避免正常运营时刷屏。
 */
public final class SlowdownTrace {
    @Setter
    @Getter
    private static volatile boolean enabled = false;

    private SlowdownTrace() {
    }

    /**
     * 输出一行 slowdown 追踪信息（仅在开启时）。
     *
     * @param group   列车（可空）
     * @param rail    slowdown 所在铁轨方块（可空）
     * @param message 追踪信息
     */
    public static void log(MinecartGroup group, Block rail, String message) {
        if (!enabled) {
            return;
        }
        String trainName = group == null ? "(null)" : group.getProperties().getTrainName();
        String trainType = BcRouteNavigator.hasRoute(group) ? "快速车" : "普通车";
        String railStr = rail == null ? "(null)"
                : "%s,%d,%d,%d".formatted(rail.getWorld().getName(), rail.getX(), rail.getY(), rail.getZ());
        Component msg = Component.text("[减速追踪] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("列车=%s(%s)".formatted(trainName, trainType), NamedTextColor.GRAY))
                .append(Component.text(" 铁轨=" + railStr, NamedTextColor.WHITE))
                .append(Component.text(" " + message, NamedTextColor.GOLD));
        BiliCraftTicketSystem.plugin.getComponentLogger().info(msg);
    }
}
