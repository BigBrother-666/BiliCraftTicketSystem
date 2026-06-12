package com.bigbrother.bilicraftticketsystem.route.geograph;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

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
     * 总距离（沿途各段边权之和），单位：km。
     */
    private final double distance;

    /**
     * @param nodes          有序节点列表
     * @param lineIdSequence 逐段 lineId 序列
     * @param distance       总距离（km）
     */
    public GeoRoutePath(List<GeoNode> nodes, List<String> lineIdSequence, double distance) {
        this.nodes = nodes;
        this.lineIdSequence = lineIdSequence;
        this.distance = distance;
    }

    /**
     * 起点节点。
     *
     * @return 路径首节点
     */
    public GeoNode getStartNode() {
        return nodes.get(0);
    }

    /**
     * 终点节点。
     *
     * @return 路径尾节点
     */
    public GeoNode getEndNode() {
        return nodes.get(nodes.size() - 1);
    }

    /**
     * 导出「列车依次经过的各 bcswitcher 应选的 lineId」序列，供导航使用。
     * <p>
     * 遍历路径节点，每遇到一个 switch（道岔）节点，取其<b>驶出段</b>的 lineId
     * （即该节点在路径中对应的下一段 {@link #lineIdSequence}）。平台节点不产生道岔决策，跳过。
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
     * @param stationName   车站名
     * @param departLineId  从该站驶出的那段轨道的 lineId（终到站为 null）
     */
    public record StationStep(String stationName, String departLineId) {
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
            if (!node.isStation() || node.getName() == null) {
                continue;
            }
            // 该站驶出段 = lineIdSequence[i]（i 为最后一个节点时无驶出段）
            String departLineId = i < lineIdSequence.size() ? lineIdSequence.get(i) : null;
            result.add(new StationStep(node.getName(), departLineId));
        }
        return result;
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
     * 本次行程所属的营运线路 id：取逐段 lineId 序列中第一个非特殊（非 default / contact）的 lineId。
     * <p>
     * 用于上车校验：车票 / 交通卡据此比对列车所属线路（列车的营运线 tag）。出站初段可能先走
     * 到发线（default），故跳过特殊段取第一条真正的营运线。全程都是特殊线时返回 null。
     *
     * @return 营运线路 id；无营运线段时返回 null
     */
    public String getStartLineId() {
        for (String lineId : lineIdSequence) {
            if (lineId != null && !LineInfo.isSpecialId(lineId)) {
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
