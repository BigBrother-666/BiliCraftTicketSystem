package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * 道岔选向调试追踪：一个会话级开关，开启后每辆列车经过 bcswitcher 时把
 * 「节点 id / 推进前指针 / 当前应走 lineId / 该牌可选分支 / 最终选中分支」打到控制台。
 * <p>
 * 用途：定位「直达车该跨站却拐进停靠线」——把列车<b>规划的</b>导航序列与它<b>实际经过</b>的
 * 物理道岔逐个对照。若实际经过的 bcswitcher 数量与导航序列长度对不上（指针越界 / lineId 错位），
 * 即为运行时指针错位；若每个道岔的 currentLineId 都对、但寻路本身就把路径定到了停靠线，则为寻路层问题。
 * <p>
 * 纯内存开关，随插件重启复位，不持久化。默认关闭，避免正常运营时刷屏。
 */
public final class SwitchTrace {
    @Setter
    @Getter
    private static volatile boolean enabled = false;

    private SwitchTrace() {
    }

    /**
     * 记录一次道岔选向（仅在开启时输出）。
     *
     * @param group         列车
     * @param nodeId        道岔节点 id（铁轨方块）
     * @param indexBefore   推进前的导航指针下标
     * @param total         导航序列总长
     * @param currentLineId 当前应走的 lineId（无导航返回 null）
     * @param branchLineIds 该控制牌声明的所有分支 lineId
     * @param selectedLineId 最终选中分支的 lineId（无匹配为 null）
     */
    public static void log(MinecartGroup group, String nodeId, int indexBefore, int total,
                           String currentLineId, List<String> branchLineIds, String selectedLineId) {
        if (!enabled) {
            return;
        }
        String trainName = group == null ? "(null)" : group.getProperties().getTrainName();
        Component msg = Component.text("[道岔追踪] ", NamedTextColor.AQUA)
                .append(Component.text("车=" + trainName, NamedTextColor.GRAY))
                .append(Component.text(" 节点=" + nodeId, NamedTextColor.WHITE))
                .append(Component.text(" 进度=" + (indexBefore + 1) + "/" + total, NamedTextColor.YELLOW))
                .append(Component.text(" 应走=" + currentLineId, NamedTextColor.GOLD))
                .append(Component.text(" 可选=" + branchLineIds, NamedTextColor.GRAY))
                .append(Component.text(" 选中=" + selectedLineId,
                        selectedLineId == null ? NamedTextColor.RED : NamedTextColor.GREEN));
        BiliCraftTicketSystem.plugin.getComponentLogger().info(msg);
    }

    /**
     * 记录一次 platform（车站）节点的导航推进（仅在开启时输出）。
     * <p>
     * 与道岔追踪配合，完整还原列车经过的<b>每一个</b>节点（含车站），便于核对节点步骤序列与
     * 物理经过是否逐项对齐。
     *
     * @param group       列车
     * @param nodeId      站台节点 id（铁轨方块）
     * @param stationName 车站名
     * @param indexBefore 推进前的导航指针下标
     * @param total       节点步骤序列总长
     * @param action      触发动作（如 "进站" / "出站推进"）
     */
    public static void logPlatform(MinecartGroup group, String nodeId, String stationName,
                                   int indexBefore, int total, String action) {
        if (!enabled) {
            return;
        }
        String trainName = group == null ? "(null)" : group.getProperties().getTrainName();
        Component msg = Component.text("[站台追踪] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("车=" + trainName, NamedTextColor.GRAY))
                .append(Component.text(" 节点=" + nodeId, NamedTextColor.WHITE))
                .append(Component.text(" 站名=" + stationName, NamedTextColor.AQUA))
                .append(Component.text(" 进度=" + (indexBefore + 1) + "/" + total, NamedTextColor.YELLOW))
                .append(Component.text(" 动作=" + action, NamedTextColor.GOLD));
        BiliCraftTicketSystem.plugin.getComponentLogger().info(msg);
    }
}
