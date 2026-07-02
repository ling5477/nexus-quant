package com.guidinglight.nexusquant.adapter.api.dataquality;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * DataQualityFreshnessRule 维护 GateO O-2 的 freshness baseline。
 *
 * <p>Why: O-2 需要一个可测试、可复用的后端规则来判断 1m/5m/1h/1d 数据是否陈旧；这些阈值是 NQ
 * safety baseline，不是交易所官方协议事实，也不产生交易授权。UI 或文档只能消费结果，不能各自写死阈值。</p>
 */
public final class DataQualityFreshnessRule {

    private static final Map<String, Duration> STALE_THRESHOLDS = Map.of(
            "1m", Duration.ofMinutes(3),
            "5m", Duration.ofMinutes(10),
            "1h", Duration.ofHours(2),
            "1d", Duration.ofDays(2)
    );

    private DataQualityFreshnessRule() {
    }

    /**
     * 按 timeframe freshness baseline 评估最新数据时间。
     *
     * @param timeframe      K 线周期；支持 1m/5m/1h/1d
     * @param latestDataAt   最新行情数据业务时间；无数据时为 null
     * @param evaluatedAt    评估时间
     * @param sourceDisabled source disabled 时不做 freshness 推断
     * @param sourceError    source error 时 freshness 标为 ERROR
     * @return freshness status、阈值与 age summary
     */
    public static FreshnessEvaluation evaluate(
            String timeframe,
            Instant latestDataAt,
            Instant evaluatedAt,
            boolean sourceDisabled,
            boolean sourceError
    ) {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (sourceDisabled) {
            return new FreshnessEvaluation(
                    DataQualitySummary.FreshnessStatus.DISABLED,
                    thresholdFor(timeframe),
                    null);
        }
        if (sourceError) {
            return new FreshnessEvaluation(
                    DataQualitySummary.FreshnessStatus.ERROR,
                    thresholdFor(timeframe),
                    null);
        }
        if (latestDataAt == null) {
            return new FreshnessEvaluation(
                    DataQualitySummary.FreshnessStatus.NO_DATA,
                    thresholdFor(timeframe),
                    null);
        }

        Duration threshold = thresholdFor(timeframe);
        Duration age = Duration.between(latestDataAt, evaluatedAt);
        if (age.isNegative() || age.compareTo(threshold) <= 0) {
            return new FreshnessEvaluation(DataQualitySummary.FreshnessStatus.FRESH, threshold, age);
        }
        if (age.compareTo(threshold.multipliedBy(3)) > 0) {
            return new FreshnessEvaluation(DataQualitySummary.FreshnessStatus.VERY_STALE, threshold, age);
        }
        return new FreshnessEvaluation(DataQualitySummary.FreshnessStatus.STALE, threshold, age);
    }

    /**
     * 返回 timeframe 的 stale threshold。
     *
     * @param timeframe K 线周期
     * @return NQ safety baseline threshold
     */
    public static Duration thresholdFor(String timeframe) {
        String key = timeframe == null ? "" : timeframe.trim().toLowerCase(Locale.ROOT);
        Duration threshold = STALE_THRESHOLDS.get(key);
        if (threshold == null) {
            throw new IllegalArgumentException("unsupported timeframe for data quality freshness: " + timeframe);
        }
        return threshold;
    }

    /**
     * freshness 评估结果；age 只来自入参时间差，不包含任何外部调用证据。
     *
     * @param status    freshness status
     * @param threshold 判定阈值
     * @param age       数据年龄；NO_DATA / DISABLED / ERROR 时为 null
     */
    public record FreshnessEvaluation(
            DataQualitySummary.FreshnessStatus status,
            Duration threshold,
            Duration age
    ) {
        public FreshnessEvaluation {
            status = Objects.requireNonNull(status, "status must not be null");
            threshold = Objects.requireNonNull(threshold, "threshold must not be null");
            if (age != null && age.isNegative()) {
                age = Duration.ZERO;
            }
        }
    }
}
