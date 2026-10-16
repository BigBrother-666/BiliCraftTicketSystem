package com.bigbrother.bilicraftticketsystem.web.outbound;

import java.util.Map;

/** 测试辅助：访问 LegacyRevenueBackfill 的包级静态方法。 */
public final class LegacyRevenueBackfillAccess {
    private LegacyRevenueBackfillAccess() {
    }

    public static Map<String, Double> parsePriceJson(String json) {
        return LegacyRevenueBackfill.parsePriceJson(json);
    }

    public static long parseTime(String s) {
        return LegacyRevenueBackfill.parseTime(s);
    }
}
