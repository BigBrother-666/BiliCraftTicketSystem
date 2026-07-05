package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import lombok.Getter;
import lombok.Setter;
import org.geojson.LngLatAlt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 铁路遍历过程中发现的一条区间（从一个节点到相邻节点的轨道段）。
 * <p>
 * 区间是有向的（from -> to），线段 id 由两端节点 id 和线路 id 确定性生成。
 * 同一物理区间被多条线路共用时，按线路各产生一条 RailEdge（geojson 中各占一条 feature，
 * 靠 layer 叠层显示）。
 */
@Getter
public class RailEdge {
    /**
     * 线段唯一 id。
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
     * 所属铁路系统 id（来自该区间所属线路的 {@code railway-system}）。线路未配置系统时为 null。
     */
    private final String railwaySystemId;
    /**
     * 轨道坐标序列（经度=x, 纬度=z, 高度=y，沿用旧 geojson 约定）。
     */
    private final List<LngLatAlt> coordinates;
    /**
     * 显示颜色（来自线路配置）。
     */
    private final String color;
    /**
     * 区间长度（米，约等于轨道方块数）。
     */
    private final double length;
    /**
     * 叠层层级，越大越在上层。两条线在 XZ 平面发生空间交叉时，高度更高的一段必须位于更高 layer。
     * 初始记录时为 0，全部区间收集完毕后由 {@link LayerAssigner} 统一重算。
     */
    @Setter
    private int layer;
    /**
     * 本段的物理出向（{@code e/s/w/n} 或 {@code f/b/l/r}）——即离开起点节点（道岔）所走的方向。
     * platform 续行段 / 起点首段无道岔决策时为 null。运行时道岔据此对带导航的列车直接选向，消除
     * 同一出向多线路或正线/到发线共用 lineId 的歧义。
     */
    private final String departDirection;


    /**
     * 本区间所在的世界名
     * 保证一个区间只在一个世界
     */
    private final String world;

    /**
     * 本段在<b>起点节点（道岔）</b>的到达面 key（{@link GraphWalk#faceKey}，形如 {@code "1_0"}）——
     * 即列车沿本段离开起点道岔时，是从哪个方向到达该道岔的。
     * <p>
     * 同一物理方块上可能有多块进入方向不同的 bcswitcher（在图里塌缩成同一节点），本字段把「从哪个方向
     * 来 → 走本段」的门控关系保留在边上，供寻路（{@link com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine}）
     * 校验：只有到达起点道岔的入边到达面属于本集合，才允许接本段出边，避免读到另一块反向牌的出边、
     * 算出物理非法路线。
     * <p>
     * 一块牌可声明多个进入方向（如 {@code [+train:lr]}），故为集合：同一 {@code (from,to,lineId)} 边被
     * 多个到达面产出时合并。起点首段 / 无门控信息时为空集（寻路不加约束，向后兼容）。
     */
    private final Set<String> enterFacesFrom = new LinkedHashSet<>();

    /**
     * 本段在<b>终点节点</b>的到达面 key（{@link GraphWalk#faceKey}）——列车沿本段到达终点节点时的方向。
     * 由几何唯一确定，故为单值。供下游出边门控：下一段出边的 {@link #enterFacesFrom} 须含本值。
     * 无信息时为 null。
     */
    private final String enterFaceTo;

    /**
     * @param fromNodeId      起点节点 id
     * @param toNodeId        终点节点 id
     * @param lineId          所属线路 id
     * @param railwaySystemId 所属铁路系统 id（未配置为 null）
     * @param coordinates     轨道坐标序列
     * @param color           显示颜色
     * @param length          区间长度
     * @param layer           叠层层级
     * @param departDirection 物理出向（无道岔决策传 null）
     * @param world           区间所在世界名
     * @param enterFaceFrom   本段在起点道岔的到达面 key（起点首段 / 无门控传 null）
     * @param enterFaceTo     本段在终点节点的到达面 key（无门控传 null）
     */
    public RailEdge(String fromNodeId,
                    String toNodeId,
                    String lineId,
                    String railwaySystemId,
                    List<LngLatAlt> coordinates,
                    String color,
                    double length,
                    int layer,
                    String departDirection,
                    String world,
                    String enterFaceFrom,
                    String enterFaceTo) {
        this.id = NodeId.ofEdge(fromNodeId, toNodeId, lineId);
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.lineId = lineId;
        this.railwaySystemId = railwaySystemId;
        this.coordinates = coordinates == null ? new ArrayList<>() : coordinates;
        this.color = color;
        this.length = length;
        this.layer = layer;
        this.departDirection = departDirection;
        this.world = world;
        addEnterFaceFrom(enterFaceFrom);
        this.enterFaceTo = enterFaceTo;
    }

    /**
     * 合并一个起点道岔到达面到 {@link #enterFacesFrom}。同一 {@code (from,to,lineId)} 边被多个到达面
     * 产出（如一块 {@code [+train:lr]} 牌，两方向来车都续行本段）时，把各到达面累积进来。
     *
     * @param enterFaceFrom 到达面 key（null / 空忽略）
     */
    public void addEnterFaceFrom(String enterFaceFrom) {
        if (enterFaceFrom != null && !enterFaceFrom.isEmpty()) {
            this.enterFacesFrom.add(enterFaceFrom);
        }
    }
}
