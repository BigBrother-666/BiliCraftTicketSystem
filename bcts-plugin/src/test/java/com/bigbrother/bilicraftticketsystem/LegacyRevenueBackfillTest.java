package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.web.outbound.LegacyRevenueBackfillAccess;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRevenueBackfillTest {
    @Test
    void parsesPriceJsonAllocations() {
        Map<String, Double> m = LegacyRevenueBackfillAccess.parsePriceJson(
                "{\"sysA\":{\"price\":1.50,\"distance\":3.00},\"sysB\":{\"price\":0.00,\"distance\":1.00}}");
        assertEquals(1.5, m.get("sysA"));
        assertEquals(0.0, m.get("sysB"));
        assertEquals(2, m.size());
    }

    @Test
    void brokenPriceJsonYieldsEmpty() {
        assertTrue(LegacyRevenueBackfillAccess.parsePriceJson("not json").isEmpty());
        assertTrue(LegacyRevenueBackfillAccess.parsePriceJson(null).isEmpty());
    }

    @Test
    void parsesLocalTime() {
        long t = LegacyRevenueBackfillAccess.parseTime("2026-01-02 03:04:05");
        assertEquals(java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), t);
    }
}
