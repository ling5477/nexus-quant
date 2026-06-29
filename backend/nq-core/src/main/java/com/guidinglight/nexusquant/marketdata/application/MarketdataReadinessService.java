package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBackendSupportLevel;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessBarFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSummary;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataReadinessRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MarketdataReadinessService computes GateM-2E source health from local database facts only.
 * <p>
 * Why: the readiness endpoint must not trigger ingestion, call exchange adapters, read credentials or
 * infer live exchange health. Missing, stale or uncertain local evidence remains fail-closed.
 */
@Service
public class MarketdataReadinessService {

    private static final Duration MIN_FRESHNESS_THRESHOLD = Duration.ofMinutes(5);

    private final MarketdataReadinessRepository readinessRepository;
    private final Clock clock;

    @Autowired
    public MarketdataReadinessService(MarketdataReadinessRepository readinessRepository) {
        this(readinessRepository, Clock.systemUTC());
    }

    MarketdataReadinessService(MarketdataReadinessRepository readinessRepository, Clock clock) {
        this.readinessRepository = Objects.requireNonNull(
                readinessRepository,
                "readinessRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Summarize marketdata readiness for one bounded local DB scope.
     *
     * @param query exchange/market/symbol/interval scope and optional query window
     * @return fail-closed readiness summary derived from local bars and ingestion records
     */
    @Transactional(readOnly = true)
    public MarketdataReadinessSummary summarize(MarketdataReadinessQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        MarketdataReadinessBarFacts barFacts = readinessRepository.loadBarFacts(query);
        MarketdataReadinessIngestionFacts ingestionFacts = readinessRepository.loadIngestionFacts(query);
        Long expectedBarCount = expectedBarCount(query, barFacts);
        Long gapCount = gapCount(expectedBarCount, barFacts);
        MarketdataReadinessStatus freshnessStatus = resolveFreshnessStatus(query, barFacts, generatedAt);
        MarketdataReadinessStatus status = resolveOverallStatus(
                barFacts,
                ingestionFacts,
                gapCount,
                freshnessStatus
        );
        return new MarketdataReadinessSummary(
                query.exchangeCode(),
                query.marketType(),
                query.symbol(),
                query.symbol(),
                query.interval().wireValue(),
                status,
                freshnessStatus,
                status,
                sourceHealthReason(status, freshnessStatus, barFacts, ingestionFacts, gapCount),
                barFacts.qualityStatusSummary(),
                barFacts.barCount(),
                barFacts.firstOpenTime(),
                barFacts.lastCloseTime(),
                expectedBarCount,
                gapCount,
                barFacts.qualityStatusSummary().unknownQualityCount(),
                ingestionFacts.lastSuccessAt(),
                ingestionFacts.lastFailureAt(),
                MarketdataBackendSupportLevel.NO_MIGRATION_MVP,
                generatedAt
        );
    }

    private MarketdataReadinessStatus resolveOverallStatus(
            MarketdataReadinessBarFacts barFacts,
            MarketdataReadinessIngestionFacts ingestionFacts,
            Long gapCount,
            MarketdataReadinessStatus freshnessStatus
    ) {
        if (barFacts.barCount() == 0) {
            return MarketdataReadinessStatus.NO_DATA;
        }
        MarketdataQualityStatusSummary qualitySummary = barFacts.qualityStatusSummary();
        if (latestFailureAfterSuccess(ingestionFacts) || qualitySummary.invalidCount() > 0) {
            return MarketdataReadinessStatus.ERROR;
        }
        if (hasGapEvidence(gapCount, qualitySummary)) {
            return MarketdataReadinessStatus.GAP;
        }
        if (qualitySummary.unknownQualityCount() > 0) {
            return MarketdataReadinessStatus.UNKNOWN;
        }
        return freshnessStatus;
    }

    private MarketdataReadinessStatus resolveFreshnessStatus(
            MarketdataReadinessQuery query,
            MarketdataReadinessBarFacts barFacts,
            Instant generatedAt
    ) {
        if (barFacts.barCount() == 0) {
            return MarketdataReadinessStatus.NO_DATA;
        }
        if (barFacts.lastOpenTime() == null) {
            return MarketdataReadinessStatus.UNKNOWN;
        }
        if (query.to() != null) {
            Instant coveredUntil = barFacts.lastOpenTime().plus(query.interval().duration());
            return coveredUntil.isBefore(query.to())
                    ? MarketdataReadinessStatus.STALE
                    : MarketdataReadinessStatus.FRESH;
        }
        Instant lastTime = barFacts.lastCloseTime() == null ? barFacts.lastOpenTime() : barFacts.lastCloseTime();
        Duration age = Duration.between(lastTime, generatedAt);
        if (age.isNegative()) {
            return MarketdataReadinessStatus.FRESH;
        }
        return age.compareTo(freshnessThreshold(query.interval())) <= 0
                ? MarketdataReadinessStatus.FRESH
                : MarketdataReadinessStatus.STALE;
    }

    private Long expectedBarCount(MarketdataReadinessQuery query, MarketdataReadinessBarFacts barFacts) {
        if (query.from() != null && query.to() != null) {
            return expectedCount(query.from(), query.to(), query.interval().duration());
        }
        if (barFacts.firstOpenTime() != null && barFacts.lastOpenTime() != null) {
            return expectedCount(barFacts.firstOpenTime(), barFacts.lastOpenTime(), query.interval().duration());
        }
        return null;
    }

    private Long gapCount(Long expectedBarCount, MarketdataReadinessBarFacts barFacts) {
        long qualityGapSignals = barFacts.qualityStatusSummary().gapSignalCount();
        if (expectedBarCount == null) {
            return qualityGapSignals == 0 ? null : qualityGapSignals;
        }
        long sequenceGapCount = Math.max(0, expectedBarCount - barFacts.barCount());
        return Math.max(sequenceGapCount, qualityGapSignals);
    }

    private long expectedCount(Instant startInclusive, Instant endInclusive, Duration interval) {
        if (endInclusive.isBefore(startInclusive)) {
            return 0;
        }
        long intervals = Math.floorDiv(Duration.between(startInclusive, endInclusive).toNanos(), interval.toNanos());
        return intervals + 1;
    }

    private boolean latestFailureAfterSuccess(MarketdataReadinessIngestionFacts ingestionFacts) {
        Instant lastFailureAt = ingestionFacts.lastFailureAt();
        if (lastFailureAt == null) {
            return false;
        }
        Instant lastSuccessAt = ingestionFacts.lastSuccessAt();
        return lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt);
    }

    private boolean hasGapEvidence(Long gapCount, MarketdataQualityStatusSummary qualitySummary) {
        return (gapCount != null && gapCount > 0) || qualitySummary.gapSignalCount() > 0;
    }

    private Duration freshnessThreshold(BarInterval interval) {
        Duration doubleInterval = interval.duration().multipliedBy(2);
        return doubleInterval.compareTo(MIN_FRESHNESS_THRESHOLD) >= 0 ? doubleInterval : MIN_FRESHNESS_THRESHOLD;
    }

    private String sourceHealthReason(
            MarketdataReadinessStatus status,
            MarketdataReadinessStatus freshnessStatus,
            MarketdataReadinessBarFacts barFacts,
            MarketdataReadinessIngestionFacts ingestionFacts,
            Long gapCount
    ) {
        if (status == MarketdataReadinessStatus.NO_DATA) {
            return "No local bars found for the requested scope; backend did not call external exchange.";
        }
        if (status == MarketdataReadinessStatus.ERROR) {
            if (latestFailureAfterSuccess(ingestionFacts)) {
                return "Latest local ingestion run failed after the latest success; backend did not call external exchange.";
            }
            return "Local bars contain invalid qualityStatus evidence; backend did not call external exchange.";
        }
        if (status == MarketdataReadinessStatus.GAP) {
            return "Local bar sequence or qualityStatus evidence indicates a gap; gapCount="
                    + (gapCount == null ? "unknown" : gapCount) + ".";
        }
        if (status == MarketdataReadinessStatus.UNKNOWN) {
            return "Local bars contain UNKNOWN qualityStatus evidence; backend did not mark source as ready.";
        }
        if (freshnessStatus == MarketdataReadinessStatus.STALE) {
            return "Local bars exist but the latest local bar does not satisfy the freshness window.";
        }
        if (status == MarketdataReadinessStatus.FRESH) {
            return "Local bars satisfy the requested readiness window using DB-only aggregation.";
        }
        if (barFacts.barCount() > 0) {
            return "Local bars exist, but backend support remains limited to no-migration MVP aggregation.";
        }
        return "Readiness is unavailable from local evidence.";
    }
}
