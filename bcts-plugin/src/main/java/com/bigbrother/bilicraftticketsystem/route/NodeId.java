package com.bigbrother.bilicraftticketsystem.route;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
     * 节点 id 解析出的坐标（世界名 + 整数方块坐标）。纯数据，便于单元测试
     * （不依赖 Bukkit）。
     *
     * @param world 世界名
     * @param x     方块 x
     * @param y     方块 y
     * @param z     方块 z
     */
    public record Coords(String world, int x, int y, int z) {
    }

    /**
     * 从节点 id 解析出世界名与整数方块坐标。
     * <p>
     * 节点 id 形如 {@code n.<world>.<x>.<y>.<z>}，末尾三段恒为整数坐标，world 名本身可能含
     * {@code .}（如 {@code world.nether}），解析思路与 {@link #worldOf(String)} 一致。
     * 纯逻辑、不依赖 Bukkit，供 {@link #toLocation(String)} 复用与单元测试。
     *
     * @param nodeId 节点 id
     * @return 解析结果；格式非法（前缀不为 {@code n}、段数不足、末三段非整数）返回 null
     */
    public static Coords parseCoords(String nodeId) {
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
        try {
            int x = Integer.parseInt(parts[parts.length - 3]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int z = Integer.parseInt(parts[parts.length - 1]);
            return new Coords(world.toString(), x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从节点 id 还原方块中心 {@link Location}。
     * <p>
     * 节点 id 已编码世界名 + 方块坐标（见 {@link #ofCoords}），故无需额外存储坐标即可定位。
     * 返回的位置取方块中心（各坐标 +0.5）。
     *
     * @param nodeId 节点 id
     * @return 方块中心位置；格式非法或世界未加载返回 null
     */
    public static Location toLocation(String nodeId) {
        Coords coords = parseCoords(nodeId);
        if (coords == null) {
            return null;
        }
        World world = Bukkit.getWorld(coords.world());
        if (world == null) {
            return null;
        }
        return new Location(world, coords.x() + 0.5, coords.y() + 0.5, coords.z() + 0.5);
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
