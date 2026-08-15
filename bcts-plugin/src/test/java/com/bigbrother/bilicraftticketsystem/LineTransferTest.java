package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.signactions.component.LineTransfer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转线目标写法（{@code <线路id>[:<进入站名>]}）解析测试。
 * <p>
 * 该写法同时用于 {@code railway_routes.yml} 的 {@code bossbar-stations} 末项与 switchline 控制牌第三行，
 * 两处必须一致。
 */
public class LineTransferTest {

    @Test
    void parseLineIdWithEntryStation() {
        LineTransfer.Target t = LineTransfer.parse("pr-s2:S2-B");
        assertNotNull(t);
        assertEquals("pr-s2", t.lineId());
        assertEquals("S2-B", t.entryStation());
    }

    @Test
    void parseLineIdOnly() {
        // 不写冒号：不指定进入站，entryStation 为空串（不是 null）
        LineTransfer.Target t = LineTransfer.parse("pr-s2");
        assertNotNull(t);
        assertEquals("pr-s2", t.lineId());
        assertEquals("", t.entryStation());
    }

    @Test
    void parseTrimsWhitespace() {
        LineTransfer.Target t = LineTransfer.parse("  pr-s2 : S2-B  ");
        assertNotNull(t);
        assertEquals("pr-s2", t.lineId());
        assertEquals("S2-B", t.entryStation());
    }

    @Test
    void parseColonWithEmptyStation() {
        // 写了冒号但站名为空：等价于不指定进入站
        LineTransfer.Target t = LineTransfer.parse("pr-s2:");
        assertNotNull(t);
        assertEquals("pr-s2", t.lineId());
        assertEquals("", t.entryStation());
    }

    @Test
    void parseStationNameMayContainColon() {
        // 只按第一个冒号切分，站名里含冒号时原样保留（与 LineConfig 用 indexOf 的口径一致）
        LineTransfer.Target t = LineTransfer.parse("pr-s2:A:B");
        assertNotNull(t);
        assertEquals("pr-s2", t.lineId());
        assertEquals("A:B", t.entryStation());
    }

    @Test
    void parseInvalid() {
        assertNull(LineTransfer.parse(null));
        assertNull(LineTransfer.parse(""));
        assertNull(LineTransfer.parse("   "));
        // 冒号前的线路 id 为空
        assertNull(LineTransfer.parse(":S2-B"));
        assertNull(LineTransfer.parse(" : "));
    }
}
