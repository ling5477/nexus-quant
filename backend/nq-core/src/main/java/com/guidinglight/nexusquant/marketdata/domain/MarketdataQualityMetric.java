package com.guidinglight.nexusquant.marketdata.domain;

import java.util.Objects;

/**
 * MarketdataQualityMetric 是数据质量中心对单个计数指标的安全包装。
 * <p>
 * Why:
 * 数据质量 API 需要返回 gap、duplicate、out-of-order、stale 等指标，但当前本地表并不总能稳定
 * 支撑每个指标。该模型强制携带 status / reason，防止调用方把 null 或 0 误解为质量已通过。
 */
public record MarketdataQualityMetric(
        Long value,
        MarketdataQualityMetricStatus status,
        String reason
) {
    public MarketdataQualityMetric {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("metric value must not be negative");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        reason = normalizeReason(reason);
    }

    public static MarketdataQualityMetric available(long value, String reason) {
        return new MarketdataQualityMetric(value, MarketdataQualityMetricStatus.AVAILABLE, reason);
    }

    public static MarketdataQualityMetric unknown(String reason) {
        return new MarketdataQualityMetric(null, MarketdataQualityMetricStatus.UNKNOWN, reason);
    }

    public static MarketdataQualityMetric notAvailable(String reason) {
        return new MarketdataQualityMetric(null, MarketdataQualityMetricStatus.NOT_AVAILABLE, reason);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
