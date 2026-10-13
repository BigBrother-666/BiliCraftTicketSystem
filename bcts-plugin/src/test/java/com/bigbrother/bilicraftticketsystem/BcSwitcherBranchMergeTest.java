package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.signactions.component.BcSwitcherBranch;
import com.bigbrother.bilicraftticketsystem.utils.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道岔出向合并测试：一块牌写共用与多块牌分开写，合并后结果应一致。
 */
public class BcSwitcherBranchMergeTest {

    /**
     * 两块牌分写同一出向（r@pr-s3 + r@pr-cw）应与一块牌写 r@pr-s3;pr-cw 合并结果相同。
     */
    @Test
    void separateSignsMergeLikeSingleSign() {
        List<BcSwitcherBranch> separate = GeoUtils.mergeBranchesByDirection(List.of(
                GeoUtils.parseBcSwitcherBranch("e@pr-s3"),
                GeoUtils.parseBcSwitcherBranch("e@pr-cw")));
        List<BcSwitcherBranch> single = GeoUtils.mergeBranchesByDirection(List.of(
                GeoUtils.parseBcSwitcherBranch("e@pr-s3;pr-cw")));
        assertEquals(1, separate.size());
        assertEquals(single.getFirst().toString(), separate.getFirst().toString());
        assertEquals(List.of("pr-s3", "pr-cw"), separate.getFirst().getLineIds());
    }

    /**
     * 不同出向保持各自独立；同一线路重复声明只保留一次。
     */
    @Test
    void differentDirectionsStaySeparateAndLineIdsDedup() {
        List<BcSwitcherBranch> merged = GeoUtils.mergeBranchesByDirection(List.of(
                GeoUtils.parseBcSwitcherBranch("e@pr-s3"),
                GeoUtils.parseBcSwitcherBranch("s@contact"),
                GeoUtils.parseBcSwitcherBranch("e@pr-s3;pr-cw")));
        assertEquals(2, merged.size());
        assertEquals("e@pr-s3;pr-cw", merged.get(0).toString());
        assertEquals("s@contact", merged.get(1).toString());
    }

    /**
     * 同一块牌第三、四行声明同一 lineId：第三行为该线停靠线、第四行为正线；只在一行出现的线路不标记。
     */
    @Test
    void sameLineOnBothRowsMarksSidingAndMainline() {
        List<BcSwitcherBranch> branches = GeoUtils.parseSignBranches("s@L1;L2", "e@L1;L3");
        assertEquals(2, branches.size());
        BcSwitcherBranch siding = branches.get(0);
        BcSwitcherBranch main = branches.get(1);
        assertTrue(siding.isSidingOf("L1"));
        assertFalse(siding.isMainlineOf("L1"));
        assertTrue(main.isMainlineOf("L1"));
        assertFalse(main.isSidingOf("L1"));
        assertFalse(siding.isSidingOf("L2"));
        assertFalse(main.isMainlineOf("L3"));

        // 按出向合并后保留标记
        List<BcSwitcherBranch> merged = GeoUtils.mergeBranchesByDirection(branches);
        assertTrue(merged.get(0).isSidingOf("L1"));
        assertTrue(merged.get(1).isMainlineOf("L1"));
    }

    /**
     * 只有一行有效时不标记。
     */
    @Test
    void singleRowHasNoTrackMarks() {
        List<BcSwitcherBranch> branches = GeoUtils.parseSignBranches("s@L1", "");
        assertEquals(1, branches.size());
        assertFalse(branches.getFirst().isSidingOf("L1"));
        assertFalse(branches.getFirst().isMainlineOf("L1"));
    }
}
