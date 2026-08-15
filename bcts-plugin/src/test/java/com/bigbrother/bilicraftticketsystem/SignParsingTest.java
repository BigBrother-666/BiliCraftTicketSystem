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

    @Test
    void bcswitcherBranchCoasterJunctionName() {
        // TCC 云轨用数字标记出向：出向须原样保留为节点名，不能被解析成方向
        BcSwitcherBranch b = GeoUtils.parseBcSwitcherBranch("1@pr-cw");
        assertNotNull(b);
        assertEquals("1", b.getDirectionStr());
        assertEquals(java.util.List.of("pr-cw"), b.getLineIds());

        // 多位数节点名与共用轨道
        BcSwitcherBranch b2 = GeoUtils.parseBcSwitcherBranch("12@pr-cw;pr-s1");
        assertNotNull(b2);
        assertEquals("12", b2.getDirectionStr());
        assertEquals(java.util.List.of("pr-cw", "pr-s1"), b2.getLineIds());
    }

    @Test
    void bcswitcherBranchSingleOutDirectionOnly() {
        // @ 前只能是一个出向：逗号不是出向分隔符，会被原样当成节点名，运行时匹配不到任何 junction。
        // 想声明两个出向应写在第三、四行各一行。
        BcSwitcherBranch b = GeoUtils.parseBcSwitcherBranch("2,3@pr-cw");
        assertNotNull(b);
        assertEquals("2,3", b.getDirectionStr());
    }

    @Test
    void bcswitcherEnterDirectionAcceptsDirectionChars() {
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:l]"));
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:lf]"));
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:eswn]"));
        // 大小写不敏感、允许空白
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train: LF ]"));
    }

    @Test
    void bcswitcherEnterDirectionAcceptsCoasterJunctionNames() {
        // TCC 云轨：进入方向写道岔节点名（数字）
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:1]"));
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:12]"));
        // 多个节点名用逗号分隔
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:1,3]"));
        assertTrue(GeoUtils.hasValidBcSwitcherEnterDirection("[+train: 1 , 12 ]"));
    }

    @Test
    void bcswitcherEnterDirectionInvalid() {
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection(null));
        // 未指定进入方向
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train]"));
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:]"));
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train: ]"));
        // 任意方向不允许
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:*]"));
        // 非法方向字符
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:xy]"));
        // 方向字符与节点名不能混写
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:1l]"));
        // 逗号分隔里有空项
        assertFalse(GeoUtils.hasValidBcSwitcherEnterDirection("[+train:1,]"));
    }
}
