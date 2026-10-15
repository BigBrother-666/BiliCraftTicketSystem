package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.MemberRemoveEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatExitEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform;
import com.bigbrother.bilicraftticketsystem.ticket.BCTransitPass;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * 列车 bossbar 的中央持有者与观众生命周期管理。
 * <p>
 * bossbar 实例按 {@link MinecartMember} 绑定，观众随座位进出 / 车厢移除自动增删。
 * 普通车 bossbar 由
 * {@link SignActionPlatform} 创建，
 * 直达车 bossbar 由 {@link BCTransitPass} 创建。
 * <p>
 * 另持有一个定时任务（{@link #startTicker}），每 {@code bossbar.update-interval-ticks} tick 按列车车头实时位置
 * 刷新所有 bossbar 的进度（{@link RouteBossbarBase#tick}）；车头位置每列车只取一次。
 */
public class BossbarManager implements Listener {
    private static final Map<MinecartMember<?>, RouteBossbarBase> mapping = new HashMap<>();
    private static BukkitTask ticker;

    /**
     * 启动（或按新间隔重启）实时进度刷新任务。
     *
     * @param plugin        插件
     * @param intervalTicks 刷新间隔（tick），<=0 表示关闭实时刷新
     */
    public static void startTicker(Plugin plugin, int intervalTicks) {
        stopTicker();
        if (intervalTicks > 0) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, BossbarManager::tickAll, intervalTicks, intervalTicks);
        }
    }

    /**
     * 停止实时进度刷新任务。
     */
    public static void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static void tickAll() {
        if (mapping.isEmpty()) {
            return;
        }
        Map<MinecartGroup, Location> heads = new HashMap<>();
        for (Map.Entry<MinecartMember<?>, RouteBossbarBase> entry : mapping.entrySet()) {
            MinecartGroup group = entry.getKey().getGroup();
            if (group == null || group.isEmpty() || group.isUnloaded()) {
                continue;
            }
            Location head = heads.computeIfAbsent(group, g -> g.head().getEntity().getLocation());
            try {
                entry.getValue().tick(group, head);
            } catch (Exception e) {
                // 单个 bossbar 出错不影响其它列车
                BiliCraftTicketSystem.plugin.getComponentLogger().warn(Component.text("bossbar 实时进度刷新失败：" + e));
            }
        }
    }

    /**
     * 取某车厢绑定的 bossbar。
     *
     * @param member 车厢
     * @return bossbar，未绑定返回 null
     */
    public static RouteBossbarBase get(MinecartMember<?> member) {
        return mapping.get(member);
    }

    /**
     * 绑定 bossbar 到车厢，并让车上现有乘客立即可见。
     *
     * @param member  车厢
     * @param bossbar bossbar
     */
    public static void put(MinecartMember<?> member, RouteBossbarBase bossbar) {
        mapping.put(member, bossbar);
        if (member.getEntity() != null) {
            for (Player passenger : member.getEntity().getPlayerPassengers()) {
                bossbar.addViewer(passenger);
            }
        }
    }

    /**
     * 解除某车厢的 bossbar 绑定，并移除其观众。
     *
     * @param member 车厢
     */
    public static void remove(MinecartMember<?> member) {
        RouteBossbarBase bossbar = mapping.remove(member);
        if (bossbar != null && member.getEntity() != null) {
            for (Entity passenger : member.getEntity().getPlayerPassengers()) {
                bossbar.removeViewer(passenger);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberRemove(MemberRemoveEvent event) {
        remove(event.getMember());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberSeatEnter(MemberSeatEnterEvent event) {
        if (event.getEntity() instanceof Player player) {
            RouteBossbarBase bossbar = mapping.get(event.getMember());
            if (bossbar != null) {
                bossbar.addViewer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMemberSeatExit(MemberBeforeSeatExitEvent event) {
        RouteBossbarBase bossbar = mapping.get(event.getMember());
        if (bossbar != null) {
            bossbar.removeViewer(event.getEntity());
        }
    }
}
