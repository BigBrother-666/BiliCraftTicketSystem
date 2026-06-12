package com.bigbrother.bilicraftticketsystem.signactions.component;

import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 列车 bossbar 的抽象基类。
 * <p>
 * 持有底层 {@link BossBar} 与观众增删，子类负责进度 / 标题更新逻辑：
 * <ul>
 *   <li>{@link CommonRouteBossbar}：普通车（每站停，滚动站名带）。</li>
 *   <li>{@link ExpressRouteBossbar}：直达车（起点→终点 + 进度条）。</li>
 * </ul>
 * 实例绑定在 {@code MinecartMember} 上（见 {@link BossbarManager}），观众随座位进出增删。
 */
public abstract class RouteBossbarBase {
    /**
     * 底层 bossbar；构造失败（数据缺失）时可能为 null，调用方需判空。
     */
    @Getter
    protected final BossBar bossBar;

    protected RouteBossbarBase(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    /**
     * 列车进站时调用：更新进度与标题。
     *
     * @param currStation 当前车站名
     */
    public abstract void onArrive(String currStation);

    /**
     * 列车出站时调用：推进到下一站显示。
     */
    public abstract void onLeave();

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
