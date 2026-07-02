package com.guidinglight.nexusquant.adapter.api.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * DataQualityGapRuleTest 固化 O-2 expected candles vs actual candles 的缺口规则。
 *
 * <p>Why: O-2 只做纯函数诊断，不做真实补洞、不改 ingestion job、不接 DB。gap status 不能被解释成
 * trading authorization。</p>
 */
class DataQualityGapRuleTest {

    @Test
    void missingCandlesShouldMapToGapWithPositiveGapCount() {
        DataQualityGapRule.GapEvaluation evaluation = DataQualityGapRule.evaluate(3L, 2L);

        assertEquals(DataQualitySummary.GapStatus.GAP, evaluation.status());
        assertEquals(1L, evaluation.gapCount());
    }

    @Test
    void completeCandlesShouldMapToNone() {
        DataQualityGapRule.GapEvaluation evaluation = DataQualityGapRule.evaluate(3L, 3L);

        assertEquals(DataQualitySummary.GapStatus.NONE, evaluation.status());
        assertEquals(0L, evaluation.gapCount());
    }

    @Test
    void unknownExpectedShouldNotBeCollapsedToNoGap() {
        DataQualityGapRule.GapEvaluation empty = DataQualityGapRule.evaluate(null, null);
        DataQualityGapRule.GapEvaluation partial = DataQualityGapRule.evaluate(null, 2L);

        assertEquals(DataQualitySummary.GapStatus.UNKNOWN, empty.status());
        assertEquals(DataQualitySummary.GapStatus.PARTIAL, partial.status());
    }

    @Test
    void expectedCandlesShouldUseInclusiveOpenTimeWindow() {
        long expected = DataQualityGapRule.expectedCandles(
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-07-02T00:02:00Z"),
                Duration.ofMinutes(1));

        assertEquals(3L, expected);
    }

    @Test
    void invalidCountsAndIntervalsShouldFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> DataQualityGapRule.evaluate(-1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> DataQualityGapRule.evaluate(1L, -1L));
        assertThrows(IllegalArgumentException.class, () -> DataQualityGapRule.expectedCandles(
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-07-02T00:01:00Z"),
                Duration.ZERO));
    }
}
