package com.bigbrother.bilicraftticketsystem.route;

import org.bukkit.block.Block;

/**
 * geojson 节点 / 线段 id 的确定性生成算法。
 * <p>
 * 铁路拓扑以 geojson 为唯一数据源。为保证多次遍历
 * 产出可对齐、可幂等更新，节点 id 必须由控制牌的世界坐标确定性生成，线段 id 必须由
 * 端点 + 线路 id 确定性生成。相同输入永远得到相同 id。
 * <p>
 * 节点 id 形如 {@code n.world.x.y.z}，线段 id 形如 {@code e.lineId.fromNodeId__toNodeId}。
 */
public final class NodeId {
    private NodeId() {
    }

    /**
     * 根据控制牌（platform / bcswitcher）所在方块坐标生成节点 id。
     *
     * @param block 控制牌或其所在铁轨方块
     * @return 确定性节点 id
     */
    public static String ofBlock(Block block) {
        return ofCoords(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * 根据世界名和整数方块坐标生成节点 id。
     *
     * @param world 世界名
     * @param x     方块 x
     * @param y     方块 y
     * @param z     方块 z
     * @return 确定性节点 id
     */
    public static String ofCoords(String world, int x, int y, int z) {
        return "n." + world + "." + x + "." + y + "." + z;
    }

    /**
     * 从节点 id 中解析世界名。
     * <p>
     * 节点 id 形如 {@code n.<world>.<x>.<y>.<z>}，末尾三段恒为整数坐标，world 名本身可能含
     * {@code .}（如 {@code world.nether}），因此从右侧剥掉坐标三段、左侧剥掉前缀 {@code n}，
     * 中间剩余部分即世界名。
     *
     * @param nodeId 节点 id
     * @return 世界名，格式不符返回 null
     */
    public static String worldOf(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String[] parts = nodeId.split("\\.");
        // n + world(>=1 段) + x + y + z，至少 5 段
        if (parts.length < 5 || !"n".equals(parts[0])) {
            return null;
        }
        StringBuilder world = new StringBuilder();
        for (int i = 1; i <= parts.length - 4; i++) {
            if (i > 1) {
                world.append('.');
            }
            world.append(parts[i]);
        }
        return world.toString();
    }

    /**
     * 根据起点节点 id、终点节点 id 和线路 id 生成线段 id。
     * <p>
     * 线段是有向的（from -> to），同一物理区间被不同线路共用时会产生不同的线段 id
     * （lineId 不同），从而在 geojson 中各占一条 feature（叠层显示）。
     *
     * @param fromNodeId 起点节点 id
     * @param toNodeId   终点节点 id
     * @param lineId     线路 id
     * @return 确定性线段 id
     */
    public static String ofEdge(String fromNodeId, String toNodeId, String lineId) {
        return "e." + lineId + "." + fromNodeId + "__" + toNodeId;
    }
}
