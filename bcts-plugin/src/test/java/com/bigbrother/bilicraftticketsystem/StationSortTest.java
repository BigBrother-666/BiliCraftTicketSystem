package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 车站名拼音排序的纯逻辑单测（不依赖 Bukkit / 路由图）。
 */
public class StationSortTest {

    @Test
    void sortsChineseByPinyin() {
        // 北(b) / 长(c) / 安(a) -> 安 / 北 / 长
        List<String> sorted = StationProvider.sortByPinyin(List.of("北京站", "长沙站", "安阳站"));
        assertEquals(List.of("安阳站", "北京站", "长沙站"), sorted, "应按拼音首字母正序：安<北<长");
    }

    @Test
    void sortIsStableAndComplete() {
        List<String> input = List.of("广州", "杭州", "成都", "深圳");
        List<String> sorted = StationProvider.sortByPinyin(input);
        // 拼音正序：成(cheng) < 广(guang) < 杭(hang) < 深(shen)
        assertEquals(List.of("成都", "广州", "杭州", "深圳"), sorted);
        assertEquals(input.size(), sorted.size(), "排序不应丢失元素");
    }

    @Test
    void doesNotMutateInput() {
        List<String> input = List.of("北京", "安阳");
        StationProvider.sortByPinyin(input);
        assertEquals(List.of("北京", "安阳"), input, "原列表不应被修改");
    }
}
