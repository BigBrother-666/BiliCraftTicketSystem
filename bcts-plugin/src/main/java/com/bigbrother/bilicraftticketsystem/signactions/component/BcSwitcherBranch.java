package com.bigbrother.bilicraftticketsystem.signactions.component;

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
 * 出向写法沿用 traincarts switcher 牌：可以是绝对方向 e/s/w/n、相对牌子的 f/b/l/r，也可以是
 * <b>道岔节点名</b>（TCCoasters 云轨节点用数字标记各出向，如 {@code 1@pr-cw}）。
 * <p>
 * 出向<b>只作为字符串保留</b>，不在此处解析成 {@link com.bergerkiller.bukkit.tc.Direction}：节点名要
 * 结合具体铁轨的节点表才能解析，故统一在运行时交给
 * {@link com.bigbrother.bilicraftticketsystem.signactions.SignActionBcswitcher} 用
 * {@code SignActionEvent.findJunction(String)}（先按节点名匹配、再退回方向解析）处理。若在此提前用
 * {@code Direction.parse} 解析，数字节点名会被解析成 {@code NONE}，道岔无法切换。
 * <p>
 * 线路 id 为 railway_routes.yml 中的线路 id（不再有 contact / default 特殊 id——遍历系统已取消正线 /
 * 联络线概念，每个出向都显式声明其归属线路）。
 */
@Getter
public class BcSwitcherBranch {
    /**
     * 出向原始字符串：绝对方向（如 "e"）、相对方向（如 "l"、"f"）或 TCC 道岔节点名（如 "1"）。
     */
    private final String directionStr;
    /**
     * 本出向归属的线路 id 列表（共用轨道时为多条，分号分隔）。至少一个。
     */
    private final List<String> lineIds;

    /**
     * @param directionStr 出向字符串（方向或 TCC 节点名）
     * @param lineIds      线路 id 列表（至少一个）
     */
    public BcSwitcherBranch(String directionStr, List<String> lineIds) {
        this.directionStr = directionStr;
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
