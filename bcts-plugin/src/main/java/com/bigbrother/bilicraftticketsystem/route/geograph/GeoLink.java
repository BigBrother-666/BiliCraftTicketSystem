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
     * 正线 / 停靠线标记（geojson {@code track}）：{@code main} 正线、{@code siding} 停靠线，普通段为 null。
     */
    private String track;

    /**
     * 本段是否为停靠线（进站道岔第三行出向起到第一个 platform 为止）。
     *
     * @return true 表示停靠线
     */
    public boolean isSiding() {
        return "siding".equals(track);
    }

    /**
     * 本段是否为正线（进站道岔第四行出向，跨站直通到出站道岔）。
     *
     * @return true 表示正线
     */
    public boolean isMainline() {
        return "main".equals(track);
    }

    /**
     * 合并另一份同 id 边的正线 / 停靠线标记：本份缺失时取另一份的。
     *
     * @param track 另一份的标记（null 忽略）
     */
    public void mergeTrack(String track) {
        if (this.track == null) {
            this.track = track;
        }
    }

    /**
     * 合并另一份同 id 边的到达面集合。
     * <p>
     * 同一条边可能被<b>两个遍历单元</b>各产出一份（共用轨道 / 越界边），各自只记下了「自己那一侧
     * 能到达的面」。构图时按 id 合并、到达面取并集，门控集合才完整——否则某方向来车会被门控拒绝，
     * 表现为「汇入本线的线路断开」。
     *
     * @param faces 另一份的到达面集合（null / 空忽略）
     */
    public void mergeEnterFacesFrom(Set<String> faces) {
        if (faces != null) {
            this.enterFacesFrom.addAll(faces);
        }
    }

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
        this(id, fromNodeId, toNodeId, lineId, distance, color, departDirection, enterFacesFrom, enterFaceTo, null);
    }

    /**
     * @param id              边 id
     * @param fromNodeId      起点节点 id
     * @param toNodeId        终点节点 id
     * @param lineId          所属线路 id
     * @param distance        边权（长度）
     * @param color           线路色
     * @param departDirection 物理出向（无道岔决策为 null）
     * @param enterFacesFrom  到达起点道岔的允许到达面集合（空集表示不门控）
     * @param enterFaceTo     到达终点节点的到达面（无则 null）
     * @param track           正线 / 停靠线标记（{@code main} / {@code siding}，普通段 null）
     */
    public GeoLink(String id, String fromNodeId, String toNodeId, String lineId, double distance, String color,
                   String departDirection, Set<String> enterFacesFrom, String enterFaceTo, String track) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.distance = distance;
        this.color = color;
        this.departDirection = departDirection;
        // 可变副本：同 id 边合并时要往里并入另一份的到达面（见 mergeEnterFacesFrom）
        this.enterFacesFrom = enterFacesFrom == null
                ? new java.util.LinkedHashSet<>()
                : new java.util.LinkedHashSet<>(enterFacesFrom);
        this.enterFaceTo = enterFaceTo;
        this.track = track;
    }
}
