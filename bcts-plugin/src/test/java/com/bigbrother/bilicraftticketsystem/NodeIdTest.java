package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.NodeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NodeIdTest {

    @Test
    void coordsAreDeterministic() {
        String a = NodeId.ofCoords("world", 10, 64, -20);
        String b = NodeId.ofCoords("world", 10, 64, -20);
        assertEquals(a, b, "相同坐标必须生成相同节点 id");
    }

    @Test
    void differentCoordsDiffer() {
        String a = NodeId.ofCoords("world", 10, 64, -20);
        String b = NodeId.ofCoords("world", 10, 64, -21);
        assertNotEquals(a, b);
        String c = NodeId.ofCoords("world_nether", 10, 64, -20);
        assertNotEquals(a, c, "不同世界必须区分");
    }

    @Test
    void edgeIsDeterministicAndDirectional() {
        String from = NodeId.ofCoords("world", 0, 64, 0);
        String to = NodeId.ofCoords("world", 5, 64, 0);

        String e1 = NodeId.ofEdge(from, to, "pr-cw");
        String e2 = NodeId.ofEdge(from, to, "pr-cw");
        assertEquals(e1, e2, "相同端点+线路必须生成相同线段 id");

        // 有向：反向是不同线段
        assertNotEquals(e1, NodeId.ofEdge(to, from, "pr-cw"));
    }

    @Test
    void sharedTrackDistinguishedByLine() {
        String from = NodeId.ofCoords("world", 0, 64, 0);
        String to = NodeId.ofCoords("world", 5, 64, 0);

        // 同一物理区间被不同线路共用 -> 不同线段 id（geojson 各占一条 feature 叠层）
        assertNotEquals(
                NodeId.ofEdge(from, to, "pr-cw"),
                NodeId.ofEdge(from, to, "pr-ccw")
        );
    }

    @Test
    void worldOfRoundTrip() {
        assertEquals("world", NodeId.worldOf(NodeId.ofCoords("world", 10, 64, -20)));
        // 负坐标也能正确剥离
        assertEquals("world", NodeId.worldOf(NodeId.ofCoords("world", -1, -64, -1)));
    }

    @Test
    void worldOfHandlesDottedWorldName() {
        // 世界名本身含 '.'，末尾三段恒为坐标，中间整体为世界名
        String id = NodeId.ofCoords("world.nether", 1, 2, 3);
        assertEquals("world.nether", NodeId.worldOf(id));
    }

    @Test
    void worldOfRejectsMalformed() {
        assertNull(NodeId.worldOf(null));
        assertNull(NodeId.worldOf("not-a-node-id"));
        assertNull(NodeId.worldOf("n.world.1.2")); // 段数不足
    }

    @Test
    void parseCoordsRoundTrip() {
        NodeId.Coords c = NodeId.parseCoords(NodeId.ofCoords("world", 10, 64, -20));
        assertNotNull(c);
        assertEquals("world", c.world());
        assertEquals(10, c.x());
        assertEquals(64, c.y());
        assertEquals(-20, c.z());
    }

    @Test
    void parseCoordsHandlesDottedWorldName() {
        NodeId.Coords c = NodeId.parseCoords(NodeId.ofCoords("world.nether", 1, -2, 3));
        assertNotNull(c);
        assertEquals("world.nether", c.world());
        assertEquals(1, c.x());
        assertEquals(-2, c.y());
        assertEquals(3, c.z());
    }

    @Test
    void parseCoordsRejectsMalformed() {
        assertNull(NodeId.parseCoords(null));
        assertNull(NodeId.parseCoords("not-a-node-id"));
        assertNull(NodeId.parseCoords("n.world.1.2"));        // 段数不足
        assertNull(NodeId.parseCoords("n.world.a.b.c"));      // 末三段非整数
        assertNull(NodeId.parseCoords("x.world.1.2.3"));      // 前缀错误
    }
}
