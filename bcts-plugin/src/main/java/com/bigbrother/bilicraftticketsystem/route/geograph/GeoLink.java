package com.bigbrother.bilicraftticketsystem.route.geograph;

import lombok.Getter;

import java.util.Set;

/**
 * 由 geojson 反向构建的路由图中的一条有向边（一段轨道区间）。
 * <p>
 * 对应 geojson 的一个 LineString feature。从 {@link #fromNodeId} 指向 {@link #toNodeId}，
 * 属于线路 {@link #lineId}，权重为 {@link #distance}（取 geojson 的 {@code length}）。
 * <p>
 * 纯数据对象，不依赖 Bukkit。
 */
@Getter
public class GeoLink {
    /**
     * 边唯一 id（geojson LineString 的 {@code id} 属性，格式 {@code e.lineId.from__to}）。
     */
    private final String id;
    /**
     * 起点节点 id。
     */
    private final String fromNodeId;
    /**
     * 终点节点 id。
     */
    private final String toNodeId;
    /**
     * 所属线路 id。
     */
    private final String lineId;
    /**
     * 边权（轨道区间长度，米/格）。
     */
    private final double distance;
    /**
     * 线路标志色（十六进制），仅供展示。
     */
    private final String color;
    /**
     * 本段物理出向（{@code e/s/w/n} 或 {@code f/b/l/r}）——离开 {@link #fromNodeId}（道岔）所走的方向。
     * 无道岔决策（platform 续行段 / 起点首段）时为 null。导航据此让道岔直接选向，消除共用 lineId 歧义。
     */
    private final String departDirection;
    /**
     * 到达<b>起点道岔</b>的允许到达面 key 集合（{@code "1_0"} 之类）。同一物理方块上有多块进入方向不同的
     * bcswitcher 时（图里塌缩为同一节点），本集合门控「从哪个方向到达起点道岔，才允许走本段」——
     * 寻路时只有入边到达面（{@link #enterFaceTo}）属于本集合的边才能续接本段，避免读到反向牌的出边、
     * 算出物理非法路线。一块牌可声明多进入方向（如 {@code [+train:lr]}），故为集合。
     * 旧 geojson 无该字段时为空集，寻路不加约束（向后兼容）。
     */
    private final Set<String> enterFacesFrom;
    /**
     * 沿本段到达<b>终点节点</b>时的到达面 key（几何唯一，单值）。供下游出边门控：下一段出边的
     * {@link #enterFacesFrom} 须含本值才可续接。无信息时为 null。
     */
    private final String enterFaceTo;

    /**
     * @param id         边 id
     * @param fromNodeId 起点节点 id
     * @param toNodeId   终点节点 id
     * @param lineId     所属线路 id
     * @param distance   边权（长度）
     * @param color      线路色
     * @param departDirection 物理出向（无道岔决策为 null）
     * @param enterFacesFrom  到达起点道岔的允许到达面集合（空集表示不门控）
     * @param enterFaceTo     到达终点节点的到达面（无则 null）
     */
    public GeoLink(String id, String fromNodeId, String toNodeId, String lineId, double distance, String color,
                   String departDirection, Set<String> enterFacesFrom, String enterFaceTo) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.distance = distance;
        this.color = color;
        this.departDirection = departDirection;
        this.enterFacesFrom = enterFacesFrom == null ? java.util.Collections.emptySet() : enterFacesFrom;
        this.enterFaceTo = enterFaceTo;
    }
}
