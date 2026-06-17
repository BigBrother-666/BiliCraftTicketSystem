package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.signactions.component.PlatformFeature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SignParsingTest {

    @Test
    void platformEmptyLineEnablesAll() {
        Set<PlatformFeature> enabled = PlatformFeature.parseEnabled("");
        assertEquals(4, enabled.size(), "第四行为空表示全部启用");
        assertTrue(enabled.contains(PlatformFeature.BOSSBAR));

        assertEquals(4, PlatformFeature.parseEnabled(null).size());
    }

    @Test
    void platformOptOut() {
        Set<PlatformFeature> enabled = PlatformFeature.parseEnabled("BB DN");
        assertFalse(enabled.contains(PlatformFeature.BOSSBAR));
        assertFalse(enabled.contains(PlatformFeature.DEPARTURE_NOTICE));
        assertTrue(enabled.contains(PlatformFeature.DESTROY));
        assertTrue(enabled.contains(PlatformFeature.ARRIVAL_NOTICE));
    }

    @Test
    void platformCodeCaseInsensitiveAndUnknownIgnored() {
        Set<PlatformFeature> enabled = PlatformFeature.parseEnabled("bb XYZ an");
        assertFalse(enabled.contains(PlatformFeature.BOSSBAR));
        assertFalse(enabled.contains(PlatformFeature.ARRIVAL_NOTICE));
        assertTrue(enabled.contains(PlatformFeature.DESTROY));
        assertTrue(enabled.contains(PlatformFeature.DEPARTURE_NOTICE));
    }

    @Test
    void bcswitcherBranchParse() {
        BcSwitcherBranch b = GeoUtils.parseBcSwitcherBranch("e@pr-cw");
        assertNotNull(b);
        assertEquals("e", b.getDirectionStr());
        assertEquals(java.util.List.of("pr-cw"), b.getLineIds());
        assertTrue(b.hasLineId("pr-cw"));
        assertFalse(b.hasLineId("pr-s1"));
    }

    @Test
    void bcswitcherBranchSharedTrack() {
        // 共用轨道：一个出向挂多条线路，分号分隔
        BcSwitcherBranch b = GeoUtils.parseBcSwitcherBranch("r@pr-cw;pr-s1");
        assertNotNull(b);
        assertEquals("r", b.getDirectionStr());
        assertEquals(java.util.List.of("pr-cw", "pr-s1"), b.getLineIds());
        assertTrue(b.hasLineId("pr-cw"));
        assertTrue(b.hasLineId("pr-s1"));
        // 多余空白与空段应被忽略
        BcSwitcherBranch b2 = GeoUtils.parseBcSwitcherBranch("l@ a ; ; b ");
        assertEquals(java.util.List.of("a", "b"), b2.getLineIds());
    }

    @Test
    void bcswitcherBranchInvalid() {
        assertNull(GeoUtils.parseBcSwitcherBranch(null));
        assertNull(GeoUtils.parseBcSwitcherBranch(""));
        assertNull(GeoUtils.parseBcSwitcherBranch("nopdelimiter"));
        assertNull(GeoUtils.parseBcSwitcherBranch("@pr-cw"));
        assertNull(GeoUtils.parseBcSwitcherBranch("e@"));
        assertNull(GeoUtils.parseBcSwitcherBranch("e@ ; "));
    }
}
