package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.Direction;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * bcswitcher 道岔控制牌的一个出向分支声明：{@code <方向>@<线路id>;[线路id]...}。
 * <p>
 * 例如 {@code e@pr-cw} 表示「向东的轨道属于 pr-cw 线路」；{@code r@pr-cw;pr-s1} 表示
 * 「右侧这段轨道由 pr-cw 与 pr-s1 两条线路共用」——在路由图视角里它是两条边（各属一条线），
 * 但物理上是同一出向。
 * <p>
 * 方向沿用 traincarts 的写法：绝对方向 e/s/w/n，或相对牌子的 f/b/l/r，解析交给
 * {@link Direction#parse(String)}。线路 id 为 railway_routes.yml 中的线路 id（不再有 contact / default
 * 特殊 id——遍历系统已取消正线 / 联络线概念，每个出向都显式声明其归属线路）。
 */
@Getter
public class BcSwitcherBranch {
    /**
     * 方向原始字符串（如 "e"、"l"、"f"）。
     */
    private final String directionStr;
    /**
     * 解析后的 traincarts 方向，解析失败为 {@link Direction#NONE}。
     */
    private final Direction direction;
    /**
     * 本出向归属的线路 id 列表（共用轨道时为多条，分号分隔）。至少一个。
     */
    private final List<String> lineIds;

    /**
     * @param directionStr 方向字符串
     * @param lineIds      线路 id 列表（至少一个）
     */
    public BcSwitcherBranch(String directionStr, List<String> lineIds) {
        this.directionStr = directionStr;
        this.direction = Direction.parse(directionStr);
        this.lineIds = lineIds == null ? Collections.emptyList() : lineIds;
    }

    /**
     * 本出向是否归属给定线路。
     *
     * @param lineId 线路 id
     * @return true 表示该出向属于这条线路
     */
    public boolean hasLineId(String lineId) {
        return lineIds.contains(lineId);
    }

    @Override
    public String toString() {
        return directionStr + "@" + String.join(";", lineIds);
    }
}
