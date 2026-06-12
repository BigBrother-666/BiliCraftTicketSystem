package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.signactions.component.CommonRouteBossbar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 普通车 bossbar 滚动站名带标题逻辑测试（{@link CommonRouteBossbar#scrollTitle}）。
 * 覆盖环线环绕越界回归、非环线起始站前导箭头细节。
 */
public class BossbarTitleTest {
    private static final NamedTextColor P = NamedTextColor.GRAY;   // 已过
    private static final NamedTextColor N = NamedTextColor.WHITE;  // 未过

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static String title(List<String> stations, boolean ring, int idx) {
        return plain(CommonRouteBossbar.scrollTitle(stations, ring, idx, P, N, 2, 3));
    }

    @Test
    void ringDoesNotThrowAcrossAllPositions() {
        // 回归：环线（首尾同名）在任意 nextStationIdx 下都不得越界
        List<String> ring = List.of("A", "B", "C", "A"); // unique = A,B,C
        for (int idx = 0; idx <= 10; idx++) {
            int finalIdx = idx;
            assertDoesNotThrow(() -> title(ring, true, finalIdx),
                    "环线在 idx=" + idx + " 时不应抛异常");
        }
    }

    @Test
    void ringWrapsAroundUniqueStations() {
        // unique = A,B,C；当前站 C(idx=2)，窗口应环绕回 A、B
        String t = title(List.of("A", "B", "C", "A"), true, 2);
        assertTrue(t.startsWith("..."), "环线两端恒显省略号");
        assertTrue(t.endsWith("..."), "环线两端恒显省略号");
        assertTrue(t.contains("C"), "应包含当前站");
        // 环绕：C 之后回到 A
        assertTrue(t.contains("A") && t.contains("B"));
    }

    @Test
    void startStationHasNoLeadingArrow() {
        // 非环线起始站：不得在第一个站名前出现箭头
        String t = title(List.of("A", "B", "C", "D", "E", "F"), false, 0);
        assertFalse(t.startsWith(" → "), "起始站前不应有前导箭头");
        assertFalse(t.startsWith("→"), "起始站前不应有前导箭头");
        assertTrue(t.startsWith("A"), "应以起始站名开头，实际：" + t);
        assertTrue(t.endsWith("..."), "尾部被截断应显示省略号");
    }

    @Test
    void shortLineShowsAllWithoutEllipsis() {
        // 站数 <= 窗口，全部显示，无省略号，无前导箭头
        String t = title(List.of("A", "B", "C"), false, 0);
        assertEquals("A → B → C", t);
    }

    @Test
    void middleStationHasLeadingEllipsis() {
        // 长线路中段：两端都应有省略号
        String t = title(List.of("A", "B", "C", "D", "E", "F", "G", "H"), false, 4);
        assertTrue(t.startsWith("..."), "中段前应有省略号，实际：" + t);
        assertTrue(t.endsWith("..."), "中段后应有省略号，实际：" + t);
    }

    @Test
    void endStationNoTrailingEllipsis() {
        // 终到站：尾部不应再有省略号
        List<String> s = List.of("A", "B", "C", "D", "E");
        String t = title(s, false, 4);
        assertFalse(t.endsWith("→ ..."), "终到站尾部不应有省略号，实际：" + t);
        assertTrue(t.contains("E"), "应包含终到站");
    }

    @Test
    void ringSingleUniqueStationDoesNotThrow() {
        // 极端：环线只有一个唯一站（A,A）
        assertDoesNotThrow(() -> title(List.of("A", "A"), true, 0));
    }
}
