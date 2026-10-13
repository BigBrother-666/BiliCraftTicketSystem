package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.coasters.CoasterRailType;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 定位 TCCoasters 云轨节点的<b>真实位置</b>（{@link TrackNode#getPosition()}）。
 * <p>
 * 云轨曲线从节点真实位置出发、在节点真实位置结束；若把节点画在铁轨方块整数坐标上，与真实轨道
 * 横向差约半格，线路在节点处会出现尖刺。故 geojson 中云轨节点的 Point 坐标与边的首尾点都用本类给出的位置。
 * <p>
 * 单独成类：TCCoasters 是可选插件（softdepend），本类引用其类型，只能在确认当前铁轨是云轨
 * （{@link TrackWalker#isCoasterRail}）之后调用，避免未安装 TCCoasters 时加载失败。
 */
final class CoasterNodeLocator {
    private CoasterNodeLocator() {
    }

    /**
     * 在铁轨方块上的云轨节点中，找出离给定采样路径最近的那个，返回其真实位置。
     * <p>
     * 一个铁轨方块可能挂多个云轨节点（如上下两层轨道），取离本次行走路径最近的节点，即列车实际经过的那个。
     *
     * @param railType  当前铁轨类型（须为 TCC 云轨）
     * @param railBlock 节点铁轨方块
     * @param samples   本次行走在节点附近的采样点（{@code [x, y, z, ...]}）
     * @return 节点真实位置；不是云轨类型或该方块上没有节点时返回 null
     */
    static Vector findNodePosition(RailType railType, Block railBlock, List<double[]> samples) {
        if (!(railType instanceof CoasterRailType coasterType) || samples.isEmpty()) {
            return null;
        }
        Vector best = null;
        double bestSq = Double.MAX_VALUE;
        for (TrackNode node : coasterType.getNodes(railBlock)) {
            Vector pos = node.getPosition();
            for (double[] s : samples) {
                double dx = s[0] - pos.getX();
                double dy = s[1] - pos.getY();
                double dz = s[2] - pos.getZ();
                double sq = dx * dx + dy * dy + dz * dz;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = pos;
                }
            }
        }
        return best == null ? null : best.clone();
    }
}
