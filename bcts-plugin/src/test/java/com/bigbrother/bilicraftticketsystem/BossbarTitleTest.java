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
        return plain(CommonRouteBossbar.scrollTitle(stations, ring, idx, P, N, 2, 3, false));
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

    private static int stationCount(String t) {
        // 去掉两端省略号后按 " → " 分隔统计站名个数
        String core = t;
        if (core.startsWith("...")) {
            core = core.substring(3);
        }
        if (core.endsWith(" → ...")) {
            core = core.substring(0, core.length() - " → ...".length());
        }
        core = core.strip();
        if (core.startsWith("→")) {
            core = core.substring(1).strip();
        }
        return core.isEmpty() ? 0 : core.split(" → ").length;
    }

    @Test
    void fixedWindowSizeAcrossWholeLine() {
        // P=2,N=3 → 固定显示 min(5, size) 个车站，全程不增不减
        List<String> s = List.of("A", "B", "C", "D", "E", "F", "G", "H");
        for (int idx = 0; idx < s.size(); idx++) {
            assertEquals(5, stationCount(title(s, false, idx)),
                    "idx=" + idx + " 应固定显示 5 个车站，实际：" + title(s, false, idx));
        }
    }

    @Test
    void startWindowDoesNotSlideUntilPassedNumExceeded() {
        // 前 passedNum(=2) 个站都经过前，窗口锁定在 [A..E]，不滑动
        List<String> s = List.of("A", "B", "C", "D", "E", "F", "G", "H");
        assertEquals("A → B → C → D → E → ...", title(s, false, 0));
        assertEquals("A → B → C → D → E → ...", title(s, false, 1));
        assertEquals("A → B → C → D → E → ...", title(s, false, 2));
        // idx=3 起开始滑动
        assertTrue(title(s, false, 3).startsWith("..."), "idx=3 应开始滑动，出现前导省略号");
    }

    @Test
    void endWindowStopsSlidingAndAccumulatesPassed() {
        // 滑到尾部后窗口锁定 [D..H]，站数不减，已过站越来越多
        List<String> s = List.of("A", "B", "C", "D", "E", "F", "G", "H");
        assertEquals(5, stationCount(title(s, false, 6)));
        assertEquals(5, stationCount(title(s, false, 7)));
        assertTrue(title(s, false, 7).endsWith("H"), "终到站尾部不应有省略号");
        assertFalse(title(s, false, 7).endsWith("..."));
    }

    /**
     * 收集 component 树中所有含文本的叶子节点（含拼接顺序），便于逐段核对颜色。
     */
    private static void flatten(Component c, List<Component> out) {
        if (c instanceof net.kyori.adventure.text.TextComponent tc && !tc.content().isEmpty()) {
            out.add(c);
        }
        for (Component child : c.children()) {
            flatten(child, out);
        }
    }

    @Test
    void arrivedArrowToCurrentStationUsesPassedColor() {
        // 到站(arrived=true)：通向当前站的箭头用已过色 P，当前站名仍为未过色 N
        List<String> s = List.of("A", "B", "C", "D", "E", "F", "G", "H");
        Component c = CommonRouteBossbar.scrollTitle(s, false, 4, P, N, 2, 3, true);
        List<Component> parts = new java.util.ArrayList<>();
        flatten(c, parts);
        // 找到当前站名 "E" 的段，其紧前一段应是 " → " 且为已过色
        int eIdx = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof net.kyori.adventure.text.TextComponent tc && tc.content().equals("E")) {
                eIdx = i;
                break;
            }
        }
        assertTrue(eIdx > 0, "应找到当前站 E 且其前有箭头段");
        Component arrow = parts.get(eIdx - 1);
        assertEquals(" → ", ((net.kyori.adventure.text.TextComponent) arrow).content());
        assertEquals(P, arrow.color(), "到站时通向当前站的箭头应为已过色");
        assertEquals(N, parts.get(eIdx).color(), "当前站名仍应为未过色");
    }

    @Test
    void notArrivedArrowToCurrentStationUsesNotPassedColor() {
        // 未到站(arrived=false)：通向当前站的箭头仍为未过色 N
        List<String> s = List.of("A", "B", "C", "D", "E", "F", "G", "H");
        Component c = CommonRouteBossbar.scrollTitle(s, false, 4, P, N, 2, 3, false);
        List<Component> parts = new java.util.ArrayList<>();
        flatten(c, parts);
        int eIdx = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof net.kyori.adventure.text.TextComponent tc && tc.content().equals("E")) {
                eIdx = i;
                break;
            }
        }
        assertTrue(eIdx > 0);
        assertEquals(N, parts.get(eIdx - 1).color(), "未到站时通向当前站的箭头应为未过色");
    }

    @Test
    void ringSingleUniqueStationDoesNotThrow() {
        // 极端：环线只有一个唯一站（A,A）
        assertDoesNotThrow(() -> title(List.of("A", "A"), true, 0));
    }
}
