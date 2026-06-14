package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 列车 bossbar 的抽象基类。
 * <p>
 * 两种 bossbar 都实现 {@link #onArrive} / {@link #onLeave}，由
 * {@link com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform} 在进出站时<b>统一多态调用</b>
 * （调用方不区分具体类型）。两者驱动来源不同，但都能从入参 {@code group} 读到自己需要的上下文：
 * <ul>
 *   <li>{@link CommonRouteBossbar}（普通车）：用当前站名滚动站名带。</li>
 *   <li>{@link ExpressRouteBossbar}（直达车）：从 {@code group} 读导航进度刷新进度条 / 终到文案。</li>
 * </ul>
 * 另有一个 {@link #refreshProgress} 钩子，供列车经过<b>道岔</b>（非进出站，不属于 onArrive/onLeave 语义）
 * 时刷新直达车进度；普通车默认空实现。
 * <p>
 * 注意：bossbar 方法只<b>读</b>导航状态来刷新显示，绝不推进导航指针——指针推进属于导航逻辑，
 * 由 platform / bcswitcher 控制牌负责。
 * <p>
 * 实例绑定在 {@code MinecartMember} 上（见 {@link BossbarManager}），观众随座位进出增删。
 */
@Getter
public abstract class RouteBossbarBase {
    /**
     * 底层 bossbar；构造失败（数据缺失）时可能为 null，调用方需判空。
     */
    protected final BossBar bossBar;

    protected RouteBossbarBase(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    /**
     * 列车进站时调用：按自身类型刷新显示。
     *
     * @param group       列车（携带导航上下文，直达车据此读进度）
     * @param currStation 当前车站名（普通车据此滚动站名带）
     */
    public abstract void onArrive(MinecartGroup group, String currStation);

    /**
     * 列车出站时调用：按自身类型刷新显示。
     *
     * @param group 列车（携带导航上下文）
     */
    public abstract void onLeave(MinecartGroup group);

    /**
     * 列车经过道岔（非进出站）时刷新进度。默认空实现：只有直达车关心道岔推进，普通车不覆写。
     *
     * @param index 当前已推进到的节点下标
     * @param total 节点步骤序列总长
     */
    public void refreshProgress(int index, int total) {
    }

    /**
     * 列车所属线路变化时，platform 是否需要为其重建本 bossbar（lifecycle，非行为分发）。
     * <p>
     * 普通车换乘到别的线路需重建（返回 true）；直达车整程固定，默认不需要。
     *
     * @param currentLine 列车当前所属线路
     * @return true 表示需要重建
     */
    public boolean needsRebuild(LineInfo currentLine) {
        return false;
    }

    /**
     * 给一名玩家显示该 bossbar。
     *
     * @param player 玩家
     */
    public void addViewer(Player player) {
        if (bossBar != null) {
            bossBar.addViewer(player);
        }
    }

    /**
     * 对一名实体（玩家）隐藏该 bossbar。
     *
     * @param entity 实体
     */
    public void removeViewer(Entity entity) {
        if (bossBar != null) {
            bossBar.removeViewer(entity);
        }
    }
}

