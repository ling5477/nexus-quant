package com.guidinglight.nexusquant.adapter.api.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * DataQualityFreshnessRuleTest 验证 O-2 freshness baseline。
 *
 * <p>Why: 1m/5m/1h/1d freshness 阈值必须集中在后端规则中，并保持可测试；这些阈值是 NQ safety
 * baseline，不是交易所官方协议事实，也不能被 UI 当作 trading-ready 信号。</p>
 */
class DataQualityFreshnessRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void oneMinuteRuleShouldMarkStaleAfterThreeMinutes() {
        assertEquals(DataQualitySummary.FreshnessStatus.FRESH, DataQualityFreshnessRule.evaluate(
                "1m",
                Instant.parse("2026-07-01T23:57:00Z"),
                NOW,
                false,
                false).status());
        assertEquals(DataQualitySummary.FreshnessStatus.STALE, DataQualityFreshnessRule.evaluate(
                "1m",
                Instant.parse("2026-07-01T23:56:59Z"),
                NOW,
                false,
                false).status());
    }

    @Test
    void fiveMinuteRuleShouldMarkStaleAfterTenMinutes() {
        assertEquals(DataQualitySummary.FreshnessStatus.STALE, DataQualityFreshnessRule.evaluate(
                "5m",
                Instant.parse("2026-07-01T23:49:59Z"),
                NOW,
                false,
                false).status());
    }

    @Test
    void oneHourRuleShouldMarkStaleAfterTwoHours() {
        assertEquals(DataQualitySummary.FreshnessStatus.STALE, DataQualityFreshnessRule.evaluate(
                "1h",
                Instant.parse("2026-07-01T21:59:59Z"),
                NOW,
                false,
                false).status());
    }

    @Test
    void oneDayRuleShouldMarkStaleAfterTwoDays() {
        assertEquals(DataQualitySummary.FreshnessStatus.STALE, DataQualityFreshnessRule.evaluate(
                "1d",
                Instant.parse("2026-06-29T23:59:59Z"),
                NOW,
                false,
                false).status());
    }

    @Test
    void noDataDisabledAndErrorShouldBeExplicitStates() {
        assertEquals(DataQualitySummary.FreshnessStatus.NO_DATA, DataQualityFreshnessRule.evaluate(
                "1m",
                null,
                NOW,
                false,
                false).status());
        assertEquals(DataQualitySummary.FreshnessStatus.DISABLED, DataQualityFreshnessRule.evaluate(
                "1m",
                Instant.parse("2026-07-01T23:59:00Z"),
                NOW,
                true,
                false).status());
        assertEquals(DataQualitySummary.FreshnessStatus.ERROR, DataQualityFreshnessRule.evaluate(
                "1m",
                Instant.parse("2026-07-01T23:59:00Z"),
                NOW,
                false,
                true).status());
    }

    @Test
    void unsupportedTimeframeShouldFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> DataQualityFreshnessRule.evaluate(
                "15m",
                NOW,
                NOW,
                false,
                false));
    }
}
