package com.guidinglight.nexusquant.strategy.application.readmodel;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ReadModelEvidenceMetadataCalculator 根据真实事实时间和显式阈值计算统一证据元数据。
 *
 * <p>Why: 计算集中在可注入 {@link Clock} 的纯组件中，保证测试可重复；缺失时间戳、未来时间戳、
 * 缺失阈值或不可用来源都必须返回 UNKNOWN，绝不使用请求时间伪造 lastCalculatedAt 或 FRESH。
 * 本组件无数据库、网络、credential 或写侧副作用，线程安全。
 */
public final class ReadModelEvidenceMetadataCalculator {

    private final Clock clock;

    /**
     * 创建证据元数据计算器。
     *
     * @param clock 当前 UTC 时间来源；测试应传固定 Clock
     */
    public ReadModelEvidenceMetadataCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 计算只读证据元数据。
     *
     * <p>阈值为空表示当前 read model 尚无权威 stale policy，此时保留可计算的 ageSeconds，
     * 但 freshnessStatus 固定为 UNKNOWN。该方法幂等、线程安全、无副作用。
     *
     * @param source 真实本地事实来源
     * @param availability 事实可用性
     * @param lastCalculatedAt 权威事实时间，可空
     * @param staleAfter 显式 stale 阈值，可空，但非空时必须大于等于 0
     * @return fail-closed 的统一元数据
     */
    public ReadModelEvidenceMetadata calculate(
            String source,
            Availability availability,
            Instant lastCalculatedAt,
            Duration staleAfter
    ) {
        Objects.requireNonNull(availability, "availability must not be null");
        if (staleAfter != null && staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative");
        }

        Long staleAfterSeconds = staleAfter == null ? null : staleAfter.getSeconds();
        if (lastCalculatedAt == null) {
            return metadata(source, availability, null, FreshnessStatus.UNKNOWN, null,
                    staleAfterSeconds, "LAST_CALCULATED_AT_MISSING");
        }

        Instant now = clock.instant();
        if (lastCalculatedAt.isAfter(now)) {
            return metadata(source, availability, lastCalculatedAt, FreshnessStatus.UNKNOWN, null,
                    staleAfterSeconds, "LAST_CALCULATED_AT_IN_FUTURE");
        }

        long ageSeconds = Duration.between(lastCalculatedAt, now).getSeconds();
        if (availability != Availability.AVAILABLE) {
            return metadata(source, availability, lastCalculatedAt, FreshnessStatus.UNKNOWN, ageSeconds,
                    staleAfterSeconds, "SOURCE_" + availability.name());
        }
        if (staleAfter == null) {
            return metadata(source, availability, lastCalculatedAt, FreshnessStatus.UNKNOWN, ageSeconds,
                    null, "STALE_THRESHOLD_NOT_DEFINED");
        }
        if (ageSeconds > staleAfter.getSeconds()) {
            return metadata(source, availability, lastCalculatedAt, FreshnessStatus.STALE, ageSeconds,
                    staleAfterSeconds, "STALE_THRESHOLD_EXCEEDED");
        }
        return metadata(source, availability, lastCalculatedAt, FreshnessStatus.FRESH, ageSeconds,
                staleAfterSeconds, null);
    }

    private ReadModelEvidenceMetadata metadata(
            String source,
            Availability availability,
            Instant lastCalculatedAt,
            FreshnessStatus freshnessStatus,
            Long ageSeconds,
            Long staleAfterSeconds,
            String staleReason
    ) {
        return new ReadModelEvidenceMetadata(
                source,
                availability,
                lastCalculatedAt,
                freshnessStatus,
                ageSeconds,
                staleAfterSeconds,
                staleReason,
                true,
                true,
                true,
                true
        );
    }
}
