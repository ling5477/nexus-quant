package com.guidinglight.nexusquant.strategy.application.readmodel;

import java.time.Instant;
import java.util.Objects;

/**
 * ReadModelEvidenceMetadata 统一描述只读诊断模型的证据来源、可用性与新鲜度。
 *
 * <p>Why: GateU 需要让多个 read model 使用同一组可审计语义，避免把 HTTP 响应生成时间误写为
 * 底层证据时间，也避免在缺少时间戳或阈值时默认展示为 FRESH。该值对象不访问数据库或网络，
 * 不携带 credential、真实账户/订单信息，也不表达交易授权。
 *
 * @param source 真实本地事实来源标识
 * @param availability 证据可用性；缺失或不完整时必须 fail-closed
 * @param lastCalculatedAt 底层事实的权威时间戳，可空
 * @param freshnessStatus 证据新鲜度；无规则或无时间戳时为 UNKNOWN
 * @param ageSeconds 证据年龄秒数，可空
 * @param staleAfterSeconds 明确的新鲜度阈值秒数，可空
 * @param staleReason STALE/UNKNOWN 的机器可读原因，可空
 * @param diagnosticOnly 固定为 true，仅用于诊断
 * @param noSideEffect 固定为 true，不产生副作用
 * @param notTradingAuthorization 固定为 true，不代表交易授权
 * @param liveDisabled 固定为 true，LIVE 保持关闭
 */
public record ReadModelEvidenceMetadata(
        String source,
        Availability availability,
        Instant lastCalculatedAt,
        FreshnessStatus freshnessStatus,
        Long ageSeconds,
        Long staleAfterSeconds,
        String staleReason,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled
) {

    public ReadModelEvidenceMetadata {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        source = source.trim();
        availability = Objects.requireNonNull(availability, "availability must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        if (ageSeconds != null && ageSeconds < 0) {
            throw new IllegalArgumentException("ageSeconds must not be negative");
        }
        if (staleAfterSeconds != null && staleAfterSeconds < 0) {
            throw new IllegalArgumentException("staleAfterSeconds must not be negative");
        }
        if (lastCalculatedAt == null && ageSeconds != null) {
            throw new IllegalArgumentException("ageSeconds requires lastCalculatedAt");
        }
        if (lastCalculatedAt == null && freshnessStatus != FreshnessStatus.UNKNOWN) {
            throw new IllegalArgumentException("missing lastCalculatedAt requires UNKNOWN freshness");
        }
        if (freshnessStatus == FreshnessStatus.FRESH && availability != Availability.AVAILABLE) {
            throw new IllegalArgumentException("FRESH requires AVAILABLE evidence");
        }
        staleReason = staleReason == null || staleReason.isBlank() ? null : staleReason.trim();
        if (!diagnosticOnly || !noSideEffect || !notTradingAuthorization || !liveDisabled) {
            throw new IllegalArgumentException("read-model evidence safety flags must remain fail-closed");
        }
    }

    /** 证据来源的可用性，不表示业务或交易可用性。 */
    public enum Availability {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE,
        UNKNOWN
    }

    /** 证据时间的新鲜度；UNKNOWN 必须按无法判断展示。 */
    public enum FreshnessStatus {
        FRESH,
        STALE,
        UNKNOWN
    }
}
