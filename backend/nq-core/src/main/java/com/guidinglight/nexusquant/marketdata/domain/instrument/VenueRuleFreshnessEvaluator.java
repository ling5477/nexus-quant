package com.guidinglight.nexusquant.marketdata.domain.instrument;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * VenueRuleFreshnessEvaluator 使用注入 Clock 计算 venue-rule freshness。
 *
 * <p>配置、时间、source/version/checksum 或官方上限 facts 缺失时一律 fail-closed。syncedAt 仅是数据库
 * 写入时间，不参与 freshUntil；preview/controller 的请求时间也不得替代 observedAt。</p>
 */
public final class VenueRuleFreshnessEvaluator {

    private static final long MIN_STALE_AFTER_SECONDS = 60L;
    private static final long MAX_STALE_AFTER_SECONDS = 86_400L;

    private final Clock clock;
    private final Long staleAfterSeconds;
    private final VenueRuleChecksumCalculator checksumCalculator;

    public VenueRuleFreshnessEvaluator(Clock clock, Long staleAfterSeconds) {
        this(clock, staleAfterSeconds, new VenueRuleChecksumCalculator());
    }

    VenueRuleFreshnessEvaluator(
            Clock clock,
            Long staleAfterSeconds,
            VenueRuleChecksumCalculator checksumCalculator
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.staleAfterSeconds = staleAfterSeconds;
        this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator must not be null");
    }

    /**
     * 计算 snapshot 的 availability/freshness；该方法只读、无 IO、无交易副作用。
     */
    public VenueRuleFreshness evaluate(InstrumentCatalogItem item) {
        return evaluateAt(item, Instant.now(clock));
    }

    /**
     * 以显式 evaluationTime 计算 snapshot freshness。
     *
     * <p>该入口用于 deterministic diagnostic preview；evaluationTime 仅是 freshness 的比较基准，
     * 不会替代 provider observation time，也不会刷新或持久化 snapshot。</p>
     *
     * @param item           本地持久化的 venue-rule snapshot
     * @param evaluationTime 调用方冻结的评估时间
     * @return fail-closed availability/freshness 结果
     */
    public VenueRuleFreshness evaluateAt(InstrumentCatalogItem item, Instant evaluationTime) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
        if (!OkxVenueRuleContract.SOURCE.equals(item.source())) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "SOURCE_MISMATCH");
        }
        if (!OkxVenueRuleContract.SOURCE_SCHEMA_VERSION.equals(item.sourceSchemaVersion())) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "SCHEMA_VERSION_MISSING_OR_MISMATCH");
        }
        if (item.ruleChecksum() == null
                || !item.ruleChecksum().equals(checksumCalculator.calculate(item))) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "CHECKSUM_MISSING_OR_CONFLICT");
        }
        if (!"LIVE".equals(item.status())) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "INSTRUMENT_NOT_LIVE");
        }
        if (!hasCompletePreviewFacts(item)) {
            return result(
                    item,
                    null,
                    VenueRuleFreshness.Availability.UNAVAILABLE,
                    VenueRuleFreshness.FreshnessStatus.UNKNOWN,
                    "OFFICIAL_VENUE_FACTS_INCOMPLETE"
            );
        }
        if (item.observedAt() == null) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "OBSERVED_AT_MISSING");
        }
        if (item.observedAt().isAfter(evaluationTime)) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "OBSERVED_AT_IN_FUTURE");
        }
        if (staleAfterSeconds == null
                || staleAfterSeconds < MIN_STALE_AFTER_SECONDS
                || staleAfterSeconds > MAX_STALE_AFTER_SECONDS) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.UNKNOWN, null, "STALE_AFTER_INVALID");
        }
        Instant freshUntil = item.observedAt().plusSeconds(staleAfterSeconds);
        if (item.nextRuleEffectiveAt() != null && item.nextRuleEffectiveAt().isBefore(freshUntil)) {
            freshUntil = item.nextRuleEffectiveAt();
        }
        if (evaluationTime.isAfter(freshUntil)) {
            return blocked(item, VenueRuleFreshness.FreshnessStatus.STALE, freshUntil, "FRESH_UNTIL_EXCEEDED");
        }
        return result(
                item,
                freshUntil,
                VenueRuleFreshness.Availability.AVAILABLE,
                VenueRuleFreshness.FreshnessStatus.FRESH,
                null
        );
    }

    private static boolean hasCompletePreviewFacts(InstrumentCatalogItem item) {
        return item.tickSize() != null
                && item.stepSize() != null
                && item.minQuantity() != null
                && item.maxLimitQuantity() != null
                && item.maxMarketSize() != null
                && "USDT".equals(item.maxMarketSizeUnit())
                && item.maxLimitNotionalUsd() != null
                && item.maxMarketNotionalUsd() != null;
    }

    private static VenueRuleFreshness blocked(
            InstrumentCatalogItem item,
            VenueRuleFreshness.FreshnessStatus freshnessStatus,
            Instant freshUntil,
            String reason
    ) {
        return result(item, freshUntil, VenueRuleFreshness.Availability.BLOCKED, freshnessStatus, reason);
    }

    private static VenueRuleFreshness result(
            InstrumentCatalogItem item,
            Instant freshUntil,
            VenueRuleFreshness.Availability availability,
            VenueRuleFreshness.FreshnessStatus freshnessStatus,
            String reason
    ) {
        return new VenueRuleFreshness(
                item.source(),
                item.sourceSchemaVersion(),
                item.observedAt(),
                freshUntil,
                item.ruleChecksum(),
                availability,
                freshnessStatus,
                reason
        );
    }
}
