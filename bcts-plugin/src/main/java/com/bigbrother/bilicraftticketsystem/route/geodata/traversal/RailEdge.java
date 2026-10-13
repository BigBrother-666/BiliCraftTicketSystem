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
     * {@link #track} 取值：正线（进站道岔第四行出向，跨站直通到出站道岔）。
     */
    public static final String TRACK_MAIN = "main";
    /**
     * {@link #track} 取值：停靠线（进站道岔第三行出向起，直到第一个 platform 为止的每一段）。
     */
    public static final String TRACK_SIDING = "siding";

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
     * 本段在<b>起点节点（道岔）</b>的到达面 key，形如 {@code "1_0"}）——
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
     * 本段在<b>终点节点</b>的到达面 key——列车沿本段到达终点节点时的方向。
     * 由几何唯一确定，故为单值。供下游出边门控：下一段出边的 {@link #enterFacesFrom} 须含本值。
     * 无信息时为 null。
     */
    private final String enterFaceTo;

    /**
     * 产出本段的<b>遍历单元 id</b>（即某条营运线的 lineId）——也就是本段写进哪个 geojson 文件。
     * <p>
     * <b>与 {@link #lineId} 的区别</b>：{@code lineId} 是本段「属于哪条线」（由 bcswitcher 的出向声明
     * 决定，决定列车怎么走、前端怎么着色）；{@code owner} 是本段「由哪次行走走出来」（决定存在哪个文件）。
     * 两者通常相同，但在下列情形不同：
     * <ul>
     *   <li><b>联络线段</b>：{@code lineId == "contact"}，owner = 带出这条联络线的营运线；</li>
     *   <li><b>越界边</b>：L 的道岔上声明了通往 L' 的出向，该段 {@code lineId == L'}、owner == L；</li>
     *   <li><b>转线段</b>：终点站转入下一条线的那一段，{@code lineId ==} 下一条线、owner == 本线。</li>
     * </ul>
     * 按 owner 分文件使<b>每个文件只有一个写者</b>，保存即整体覆盖，无需读回旧文件做任何合并
     * （详见 {@link TraversalCollector} 的类注释）。
     * <p>
     * 读回旧 geojson 时若无该属性则为 null（只用于 layer 重算，不影响保存）。
     */
    private final String ownerLineId;

    /**
     * 正线 / 停靠线标记：{@link #TRACK_MAIN}、{@link #TRACK_SIDING}，普通区间为 null。
     * <p>
     * 由遍历按 bcswitcher 牌面标记（同一块牌第三、四行声明同一 lineId 即进站道岔，第三行为停靠线、
     * 第四行为正线），寻路 / 前端据此判定正线跨站，不再做结构推断。
     */
    @Setter
    private String track;

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
     * @param ownerLineId     归属线路 id（见 {@link #ownerLineId}；旧文件读回为 null）
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
                    String enterFaceTo,
                    String ownerLineId) {
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
        this.ownerLineId = ownerLineId;
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
