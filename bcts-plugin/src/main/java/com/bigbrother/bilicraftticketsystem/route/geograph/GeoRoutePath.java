package com.bigbrother.bilicraftticketsystem.route.geograph;

import lombok.Getter;

import java.util.*;

/**
 * 一次寻路得到的路径结果。
 * <p>
 * 含按经过顺序排列的节点列表、每段轨道的 lineId 序列（<b>逐段、不合并、不去重</b>，因此一条路径
 * 可多次经过同一线路、连续同线也分别列出，供列车导航按序消费），以及总距离与起终点节点。
 */
@Getter
public class GeoRoutePath {
    /**
     * 按经过顺序排列的节点（首=起点站台，尾=终点站台）。
     */
    private final List<GeoNode> nodes;
    /**
     * 每段轨道的 lineId，按经过顺序逐段列出（与 nodes 相邻对一一对应，size = nodes.size()-1）。
     * 不合并连续同线、不去重。
     */
    private final List<String> lineIdSequence;
    /**
     * 每段轨道的物理出向（{@code e/s/w/n} 或 {@code f/b/l/r}），与 {@link #lineIdSequence} 平行、
     * 一一对应（size = nodes.size()-1）。无道岔决策段为 null。导航的道岔步骤据此选向，消除共用 lineId 歧义。
     */
    private final List<String> departDirectionSequence;
    /**
     * 总距离（沿途各段边权之和），单位：km。
     */
    private final double distance;

    /**
     * @param nodes          有序节点列表
     * @param lineIdSequence 逐段 lineId 序列
     * @param departDirectionSequence 逐段物理出向序列（与 lineIdSequence 平行）
     * @param distance       总距离（km）
     */
    public GeoRoutePath(List<GeoNode> nodes, List<String> lineIdSequence, List<String> departDirectionSequence,
                        double distance) {
        this.nodes = nodes;
        this.lineIdSequence = lineIdSequence;
        this.departDirectionSequence = departDirectionSequence;
        this.distance = distance;
    }

    /**
     * 起点节点。
     *
     * @return 路径首节点
     */
    public GeoNode getStartNode() {
        return nodes.getFirst();
    }

    /**
     * 终点节点。
     *
     * @return 路径尾节点
     */
    public GeoNode getEndNode() {
        return nodes.getLast();
    }

    /**
     * 导出「列车依次经过的各 bcswitcher 应选的 lineId」序列，供导航使用。
     * <p>
     * 遍历路径节点，每遇到一个 switch（道岔）节点，取其<b>驶出段</b>的 lineId
     * （即该节点在路径中对应的下一段 {@link #lineIdSequence}）。站台节点不产生道岔决策，跳过。
     * 列车每经过一个 bcswitcher 推进一格，与本序列逐一对齐。
     * <p>
     * 注意：路径尾节点（终点站台）没有驶出段，自然不会被纳入。
     *
     * @return 各道岔 lineId 的有序序列（不去重，可重复）
     */
    public List<String> switcherLineIds() {
        List<String> result = new ArrayList<>();
        // nodes[i] 的驶出段是 lineIdSequence[i]（i < lineIdSequence.size()）
        for (int i = 0; i < lineIdSequence.size(); i++) {
            GeoNode node = nodes.get(i);
            if (!node.isStation()) {
                // switch 节点：该道岔应选其驶出段所属 lineId
                result.add(lineIdSequence.get(i));
            }
        }
        return result;
    }

    /**
     * 导出整条路径的「节点步骤序列」，供列车导航单指针消费（替代仅含道岔的
     * {@link #switcherLineIds()}）。按经过顺序列出<b>每一个</b>路径节点：
     * <ul>
     *   <li>道岔（switch）节点编码为 {@code "S:" + 驶出段物理出向}：bcswitcher 据此直接选向（消除共用
     *       lineId 歧义，如进站道岔正线/到发线同 lineId）。出向缺失（旧数据无 departDir）时载荷为空，
     *       道岔回退按 lineId / tag 选向。</li>
     *   <li>车站（station / platform）节点编码为 {@code "P"}：仅用于推进指针与进度，不选向。</li>
     * </ul>
     * 列车每物理经过一个节点控制牌推进一格：bcswitcher 进站推进、platform 出站推进。
     * 由此即便整条线路没有任何 bcswitcher（全是无正线车站），指针也能随 platform 推进直到终点。
     *
     * @return 节点步骤有序序列（含起点 / 终点站台，size = 路径节点数）
     */
    public List<String> routeSteps() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            GeoNode node = nodes.get(i);
            if (node.isStation()) {
                result.add(ROUTE_STEP_PLATFORM);
            } else {
                String depart = i < departDirectionSequence.size() && departDirectionSequence.get(i) != null
                        ? departDirectionSequence.get(i) : "";
                result.add(ROUTE_STEP_SWITCH_PREFIX + depart);
            }
        }
        return result;
    }

    /**
     * {@link #routeSteps()} 中车站（platform）步骤的编码。
     */
    public static final String ROUTE_STEP_PLATFORM = "P";
    /**
     * {@link #routeSteps()} 中道岔（switch）步骤的前缀，其后接驶出段<b>物理出向</b>（e/s/w/n 或 f/b/l/r）。
     */
    public static final String ROUTE_STEP_SWITCH_PREFIX = "S:";

    /**
     * 路径上的车站名有序序列（仅 station 节点，按经过顺序）。
     *
     * @return 车站名列表
     */
    public List<String> stationSequence() {
        List<String> result = new ArrayList<>();
        for (GeoNode node : nodes) {
            if (node.isStation() && node.getName() != null) {
                result.add(node.getName());
            }
        }
        return result;
    }

    /**
     * 一个车站 + 其驶出段所属 lineId，用于 lore 显示（站名 + 箭头按该段线路上色）。
     *
     * @param stationName  车站名
     * @param departLineId 从该站驶出的那段轨道的 lineId（终到站为 null）
     */
    public record StationStep(String stationName, String departLineId) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StationStep that = (StationStep) o;
            return Objects.equals(stationName, that.stationName) && Objects.equals(departLineId, that.departLineId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stationName, departLineId);
        }
    }

    /**
     * 路径上的车站序列，每个车站附带其「驶出段」所属 lineId（供 lore 给箭头上色）。
     * <p>
     * 只保留 station 节点；某站的驶出段 lineId 取该站之后、下一个 station 之前最近一段轨道的 lineId
     * （即站点在 {@link #nodes} 中下标处的 {@link #lineIdSequence} 值）。终到站无驶出段，lineId 为 null。
     *
     * @return 车站步骤有序列表
     */
    public List<StationStep> stationSteps() {
        LinkedHashSet<StationStep> result = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            GeoNode node = nodes.get(i);
            if (node.isStation() && node.getName() != null) {
                String lineId = i < lineIdSequence.size() ? lineIdSequence.get(i) : (i > 0 ? lineIdSequence.get(i - 1) : null);
                result.add(new StationStep(node.getName(), lineId));
            } else {
                // 含有正线的车站获车站名
                // 寻找进站道岔直接出边连接的车站节点
                String stationName = GeoRouteEngine.getGraph().platformNameOfMainlineSwitch(node);
                if (stationName != null) {
                    String lineId = i < lineIdSequence.size() ? lineIdSequence.get(i) : null;
                    if (lineId != null) {
                        result.add(new StationStep(stationName, lineId));
                    }
                }
            }
        }
        return result.stream().toList();
    }

    /**
     * 起点站名（起点节点的车站名，非车站返回 null）。
     *
     * @return 起点站名
     */
    public String getStartStationName() {
        return getStartNode().getName();
    }

    /**
     * 本次行程所属的营运线路 id：取逐段 lineId 序列中第一个非空的 lineId。
     * <p>
     * 用于上车校验：车票 / 交通卡据此比对列车所属线路（列车的营运线 tag）。
     *
     * @return 营运线路 id；无有效线段时返回 null
     */
    public String getStartLineId() {
        for (String lineId : lineIdSequence) {
            if (lineId != null && !lineId.isEmpty()) {
                return lineId;
            }
        }
        return null;
    }

    /**
     * 终点站名（终点节点的车站名，非车站返回 null）。
     *
     * @return 终点站名
     */
    public String getEndStationName() {
        return getEndNode().getName();
    }
}
