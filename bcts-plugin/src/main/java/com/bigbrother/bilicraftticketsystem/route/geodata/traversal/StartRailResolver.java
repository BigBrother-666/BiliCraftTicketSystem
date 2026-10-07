package com.bigbrother.bilicraftticketsystem.route.geodata.traversal;

import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * 遍历起点的铁轨解析器：把一个「起点坐标 + 方向」解析成实际的铁轨方块。
 * <p>
 * 同时支持<b>实体铁轨</b>（原版轨等，按方块材质判断）与 <b>TCC 云轨</b>（虚拟曲线轨，没有实体方块，
 * 必须按位置查 traincarts 的轨道注册表才能找到）。
 * <p>
 * 由两处共用，保证「登记时校验通过」与「遍历时能从该起点出发」判据完全一致：
 * <ul>
 *   <li>{@code /railgeo setStartPos} 登记前校验玩家确实站在轨道上
 *       （见 {@code GeoCommand#setStartPos}）——否则问题要到下次遍历才暴露；</li>
 *   <li>{@link GeoTraversalTask#seedStarts} 遍历开始时解析登记起点。</li>
 * </ul>
 * 必须在主线程调用（读取实时轨道数据 / 区块）。
 */
public final class StartRailResolver {

    private StartRailResolver() {
    }

    /**
     * 解析起点铁轨方块（起点坐标即铁轨方块；若该处不是铁轨再看下方一格）。
     * <p>
     * 先按<b>方块材质</b>判实体铁轨（原版轨等，行为与历史一致）；都不是时再交给 traincarts 的轨道查找
     * （{@link #resolveVirtual}），从而<b>支持把起点登记在 TCC 云轨（虚拟轨）上</b>——云轨不是方块，
     * 材质判断永远失败，必须按位置查轨道类型才能找到。
     * <p>
     * 起点之后的遍历本就与轨道类型无关（{@link TrackWalker} 已按云轨 / 普通轨分别采样），故只需放开这一步。
     *
     * @param loc       起点坐标
     * @param direction 起点行走方向（云轨查找时用于同格多轨取最近的一条）
     * @return 铁轨方块，找不到返回 null
     */
    public static Block resolve(Location loc, Vector direction) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        Block block = loc.getBlock();
        if (GeoUtils.isRail(block.getType())) {
            return block;
        }
        Block below = block.getRelative(0, -1, 0);
        if (GeoUtils.isRail(below.getType())) {
            return below;
        }
        // 两格都不是实体铁轨：可能登记在 TCC 云轨上，按位置查 traincarts 轨道（含所有已注册轨道类型）
        Block virtual = resolveVirtual(block, direction);
        return virtual != null ? virtual : resolveVirtual(below, direction);
    }

    /**
     * 按位置查 traincarts 轨道，解析该方块位置处的铁轨方块——<b>不限轨道类型</b>，因此能解析 TCC 云轨
     * 这类没有实体方块的虚拟轨（云轨的「铁轨方块」是其节点所在方块，可能与传入的位置方块不是同一格）。
     * <p>
     * 构造 {@link RailState} 的方式与 {@link TrackWalkingPoint#TrackWalkingPoint(Location, Vector)} 一致
     * （设位置 + 行走方向后调 {@link RailType#loadRailInformation}），故这里解析出的铁轨与随后
     * {@link TrackWalker} 起步时解析到的是同一条；带上行走方向也让「同一格存在多条轨道」时按真实去向取最近的那条。
     * <p>
     * 取方块中心而非方块角作为探测位置：中心必定落在该方块内，避免边界浮点歧义。调用方已先读过该方块的材质
     * （区块因此已加载），故此处不会碰到未加载区块。
     *
     * @param positionBlock 探测位置所在方块
     * @param direction     起点行走方向（用于同格多轨时取最近的一条）
     * @return 该位置处的铁轨方块；无轨道返回 null
     */
    private static Block resolveVirtual(Block positionBlock, Vector direction) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.createWorldPlaceholder(positionBlock.getWorld()));
        state.position().setLocation(positionBlock.getLocation().add(0.5, 0.5, 0.5));
        state.position().setMotion(direction);
        if (!RailType.loadRailInformation(state)) {
            return null;
        }
        RailPiece piece = state.railPiece();
        return (piece == null || piece.isNone()) ? null : piece.block();
    }
}
