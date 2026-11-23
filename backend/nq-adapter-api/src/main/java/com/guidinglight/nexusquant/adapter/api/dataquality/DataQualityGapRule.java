package com.guidinglight.nexusquant.adapter.api.dataquality;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * DataQualityGapRule 计算 expected candles 与 actual candles 的最小 gap 诊断。
 *
 * <p>Why: O-2 只需要可测试的缺口规则，不做真实补洞、不接 DB、不修改 ingestion job。gap=NONE 只表示
 * 当前输入窗口没有缺口证据，不代表数据可以交易。</p>
 */
public final class DataQualityGapRule {

    private DataQualityGapRule() {
    }

    /**
     * 基于 expected / actual 计数评估 gap。
     *
     * @param expectedCandles 期望 candle 数；未知时为 null
     * @param actualCandles   实际 candle 数；未知时为 null
     * @return gap status 与 gap count
     */
    public static GapEvaluation evaluate(Long expectedCandles, Long actualCandles) {
        if (actualCandles != null && actualCandles < 0) {
            throw new IllegalArgumentException("actualCandles must not be negative");
        }
        if (expectedCandles == null) {
            if (actualCandles != null && actualCandles > 0) {
                return new GapEvaluation(DataQualitySummary.GapStatus.PARTIAL, null, actualCandles, 0);
            }
            return new GapEvaluation(DataQualitySummary.GapStatus.UNKNOWN, null, actualCandles, 0);
        }
        if (expectedCandles < 0) {
            throw new IllegalArgumentException("expectedCandles must not be negative");
        }
        long actual = actualCandles == null ? 0 : actualCandles;
        long gapCount = Math.max(0, expectedCandles - actual);
        if (gapCount > 0) {
            return new GapEvaluation(DataQualitySummary.GapStatus.GAP, expectedCandles, actual, gapCount);
        }
        if (actual > expectedCandles) {
            return new GapEvaluation(DataQualitySummary.GapStatus.PARTIAL, expectedCandles, actual, 0);
        }
        return new GapEvaluation(DataQualitySummary.GapStatus.NONE, expectedCandles, actual, 0);
    }

    /**
     * 从闭区间时间窗口计算 expected candles。
     *
     * @param startInclusive 起始 open time
     * @param endInclusive   结束 open time
     * @param interval       candle 周期
     * @return 按 interval 推导出的 expected count
     */
    public static long expectedCandles(Instant startInclusive, Instant endInclusive, Duration interval) {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        if (endInclusive.isBefore(startInclusive)) {
            return 0;
        }
        return Math.floorDiv(Duration.between(startInclusive, endInclusive).toNanos(), interval.toNanos()) + 1;
    }

    /**
     * gap 评估结果；UNKNOWN/PARTIAL 不能被解释为无缺口。
     *
     * @param status          gap status
     * @param expectedCandles 期望数量；未知时为 null
     * @param actualCandles   实际数量；未知时为 null
     * @param gapCount        缺口数量；UNKNOWN/PARTIAL 可为 0
     */
    public record GapEvaluation(
            DataQualitySummary.GapStatus status,
            Long expectedCandles,
            Long actualCandles,
            long gapCount
    ) {
        public GapEvaluation {
            status = Objects.requireNonNull(status, "status must not be null");
            if (expectedCandles != null && expectedCandles < 0) {
                throw new IllegalArgumentException("expectedCandles must not be negative");
            }
            if (actualCandles != null && actualCandles < 0) {
                throw new IllegalArgumentException("actualCandles must not be negative");
            }
            if (gapCount < 0) {
                throw new IllegalArgumentException("gapCount must not be negative");
            }
        }
    }
}
