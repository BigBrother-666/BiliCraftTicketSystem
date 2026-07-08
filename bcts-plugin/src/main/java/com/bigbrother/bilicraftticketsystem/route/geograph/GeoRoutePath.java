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
     * 每段轨道的长度（km），与 {@link #lineIdSequence} 平行、一一对应（size = nodes.size()-1）。
     * 用于按段所属铁路系统分别计费。
     */
    private final List<Double> distanceSequence;
    /**
     * 总距离（沿途各段边权之和），单位：km。
     */
    private final double distance;

    /**
     * @param nodes                   有序节点列表
     * @param lineIdSequence          逐段 lineId 序列
     * @param departDirectionSequence 逐段物理出向序列（与 lineIdSequence 平行）
     * @param distanceSequence        逐段长度序列（km，与 lineIdSequence 平行）
     * @param distance                总距离（km）
     */
    public GeoRoutePath(List<GeoNode> nodes, List<String> lineIdSequence, List<String> departDirectionSequence,
                        List<Double> distanceSequence, double distance) {
        this.nodes = nodes;
        this.lineIdSequence = lineIdSequence;
        this.departDirectionSequence = departDirectionSequence;
        this.distanceSequence = distanceSequence;
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
            String nodeId = node.getId();
            if (node.isStation()) {
                result.add(ROUTE_STEP_PLATFORM + ROUTE_STEP_NODE_SEP + nodeId);
            } else {
                String depart = i < departDirectionSequence.size() && departDirectionSequence.get(i) != null
                        ? departDirectionSequence.get(i) : "";
                result.add(ROUTE_STEP_SWITCH_PREFIX + depart + ROUTE_STEP_NODE_SEP + nodeId);
            }
        }
        return result;
    }

    /**
     * {@link #routeSteps()} 中车站（platform）步骤的载荷编码。
     */
    public static final String ROUTE_STEP_PLATFORM = "P";
    /**
     * {@link #routeSteps()} 中道岔（switch）步骤载荷的前缀，其后接驶出段<b>物理出向</b>（e/s/w/n 或 f/b/l/r）。
     */
    public static final String ROUTE_STEP_SWITCH_PREFIX = "S:";
    /**
     * {@link #routeSteps()} 中「步骤载荷」与「节点 id」的分隔符。
     * <p>
     * 每一步编码为 {@code <载荷><SEP><节点id>}：载荷为 {@code "P"} 或 {@code "S:<出向>"}，
     * 节点 id 为 {@link com.bigbrother.bilicraftticketsystem.route.NodeId#ofBlock}（形如 {@code n.world.x.y.z}，
     * 不含 {@code |}）。列车运行时据此把「实际到达的物理节点」与「当前步骤应到的节点」逐个比对：不符即
     * 说明遇到新放置的控制牌（节点不在图中→跳过不推进）或走错方向（节点在图中但顺序不符→按终点重算路线）。
     * <p>
     * 旧存档的步骤不含分隔符，解析节点 id 时返回 null，对齐校验自动降级为「不校验、照常推进」，向后兼容。
     */
    public static final String ROUTE_STEP_NODE_SEP = "|";

    /**
     * 取步骤的「载荷」部分（{@code "P"} 或 {@code "S:<出向>"}），即分隔符之前的内容。
     * 旧格式（无分隔符）整串即载荷。
     *
     * @param step 节点步骤编码
     * @return 载荷；step 为 null 返回 null
     */
    public static String stepPayload(String step) {
        if (step == null) {
            return null;
        }
        int sep = step.indexOf(ROUTE_STEP_NODE_SEP);
        return sep < 0 ? step : step.substring(0, sep);
    }

    /**
     * 取步骤编码里的节点 id（分隔符之后的内容）。
     *
     * @param step 节点步骤编码
     * @return 节点 id；无分隔符（旧格式）或 step 为 null 返回 null
     */
    public static String stepNodeId(String step) {
        if (step == null) {
            return null;
        }
        int sep = step.indexOf(ROUTE_STEP_NODE_SEP);
        return sep < 0 ? null : step.substring(sep + ROUTE_STEP_NODE_SEP.length());
    }

    /**
     * 步骤是否为道岔步骤（载荷以 {@link #ROUTE_STEP_SWITCH_PREFIX} 开头）。
     *
     * @param step 节点步骤编码
     * @return true 表示道岔步骤
     */
    public static boolean stepIsSwitch(String step) {
        String payload = stepPayload(step);
        return payload != null && payload.startsWith(ROUTE_STEP_SWITCH_PREFIX);
    }

    /**
     * 取道岔步骤的物理出向。
     *
     * @param step 节点步骤编码
     * @return 出向（e/s/w/n 或 f/b/l/r）；非道岔步骤 / 出向为空返回 null
     */
    public static String stepDirection(String step) {
        String payload = stepPayload(step);
        if (payload == null || !payload.startsWith(ROUTE_STEP_SWITCH_PREFIX)) {
            return null;
        }
        String dir = payload.substring(ROUTE_STEP_SWITCH_PREFIX.length());
        return dir.isEmpty() ? null : dir;
    }

    /**
     * 路径上的车站名有序序列（仅 station 节点，按经过顺序）。
     *
     * @return 车站名列表
     */
    public List<String> stationSequence() {
        List<String> result = new ArrayList<>();
        for (StationStep stationStep : stationSteps()) {
            result.add(stationStep.stationName());
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
        List<StationStep> result = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            GeoNode node = nodes.get(i);
            if (node.isStation() && node.getName() != null) {
                String lineId = i < lineIdSequence.size() ? lineIdSequence.get(i) : (i > 0 ? lineIdSequence.get(i - 1) : null);
                if (!result.isEmpty() && !result.getLast().stationName.equals(node.getName())) {
                    result.add(new StationStep(node.getName(), lineId));
                } else if (result.isEmpty()) {
                    result.add(new StationStep(node.getName(), lineId));
                }
            } else {
                // 含有正线的车站获车站名
                // 寻找进站道岔直接出边连接的车站节点
                String stationName = GeoRouteEngine.getGraph().platformNameOfMainlineSwitch(node);
                if (stationName != null) {
                    String lineId = i < lineIdSequence.size() ? lineIdSequence.get(i) : null;
                    if (lineId != null) {
                        if (!result.isEmpty() && !result.getLast().stationName.equals(stationName)) {
                            result.add(new StationStep(stationName, lineId));
                        } else if (result.isEmpty()) {
                            result.add(new StationStep(node.getName(), lineId));
                        }
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
     * 本次行程开始时所属的营运线路 id：取逐段 lineId 序列中第一个非空的 lineId。
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
     * 本次行程结束时所属的营运线路 id：倒序取逐段 lineId 序列中第一个非空的 lineId。
     *
     * @return 营运线路 id；无有效线段时返回 null
     */
    public String getEndLineId() {
        for (int i = lineIdSequence.size() - 1; i >= 0; i--) {
            if (lineIdSequence.get(i) != null && !lineIdSequence.get(i).isEmpty()) {
                return lineIdSequence.get(i);
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
