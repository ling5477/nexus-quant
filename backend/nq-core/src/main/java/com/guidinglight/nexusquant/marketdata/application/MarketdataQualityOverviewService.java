package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityBarScopeFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDataOriginSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIssue;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityMetric;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverview;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewScope;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSourceHealth;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataQualityOverviewRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MarketdataQualityOverviewService 编排 GateP Batch 2 Data Quality Center 只读聚合。
 * <p>
 * Why:
 * overview 需要组合 bars、dataset coverage 和 ingestion run 三类本地事实。Service 层集中业务语义：
 * expected/gap/stale/source health 都在 core 中计算，infra 只做只读 SQL；整个链路不写库、不调用 adapter、
 * 不读取 credential，也不把数据质量诊断提升成交易授权。
 */
@Service
public class MarketdataQualityOverviewService {

    private static final Duration MIN_FRESHNESS_THRESHOLD = Duration.ofMinutes(5);
    private static final String LOCAL_DB = "LOCAL_DB";
    private static final String LOCAL_DB_ONLY_SUPPORT = "LOCAL_DB_ONLY_READ_MODEL";

    private final MarketdataQualityOverviewRepository overviewRepository;
    private final Clock clock;

    @Autowired
    public MarketdataQualityOverviewService(MarketdataQualityOverviewRepository overviewRepository) {
        this(overviewRepository, Clock.systemUTC());
    }

    MarketdataQualityOverviewService(MarketdataQualityOverviewRepository overviewRepository, Clock clock) {
        this.overviewRepository = Objects.requireNonNull(
                overviewRepository,
                "overviewRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 MarketData Data Quality Center overview。
     *
     * @param query 只读筛选条件；可为空值表示跨 scope 聚合，但不会触发任何外部读取
     * @return 本地 DB-only 数据质量 overview
     */
    @Transactional(readOnly = true)
    public MarketdataQualityOverview summarize(MarketdataQualityOverviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        List<MarketdataQualityBarScopeFacts> barScopes = overviewRepository.loadBarScopeFacts(query);
        MarketdataQualityDatasetCoverageFacts coverageFacts = overviewRepository.loadDatasetCoverageFacts(query);
        MarketdataQualityIngestionFacts ingestionFacts = overviewRepository.loadIngestionFacts(query);

        long totalBars = barScopes.stream().mapToLong(MarketdataQualityBarScopeFacts::barCount).sum();
        MarketdataQualityStatusSummary qualitySummary = aggregateQualitySummary(barScopes);
        Long scopeExpectedBars = expectedBarsFromScopes(query, barScopes);
        Long expectedBars = firstNonNull(coverageFacts.expectedBars(), scopeExpectedBars);
        Long gapCount = gapCount(expectedBars, totalBars, qualitySummary, coverageFacts);
        long staleScopeCount = staleScopeCount(barScopes, generatedAt);
        MarketdataReadinessStatus freshnessStatus = freshnessStatus(totalBars, staleScopeCount);
        MarketdataQualityStatus qualityStatus = qualityStatus(totalBars, gapCount, qualitySummary, coverageFacts);
        MarketdataReadinessSourceHealth sourceHealth = sourceHealth(
                totalBars,
                gapCount,
                staleScopeCount,
                qualitySummary,
                coverageFacts,
                ingestionFacts
        );

        return new MarketdataQualityOverview(
                scope(query),
                totalBars,
                expectedBars,
                gapCount,
                duplicateMetric(coverageFacts),
                MarketdataQualityMetric.notAvailable(
                        "当前 schema 未持久化跨 scope out-of-order 诊断；本轮不新增 migration。"
                ),
                staleMetric(barScopes, staleScopeCount),
                latestBarTime(barScopes),
                earliestBarTime(barScopes),
                ingestionFacts.lastSuccessAt(),
                ingestionFacts.lastFailureAt(),
                ingestionFacts.lastIngestionRunId(),
                sourceHealth,
                freshnessStatus,
                qualityStatus,
                dataOriginSummary(query, barScopes),
                datasetCoverageSummary(coverageFacts),
                topIssues(totalBars, gapCount, staleScopeCount, qualitySummary, ingestionFacts),
                generatedAt
        );
    }

    private MarketdataQualityOverviewScope scope(MarketdataQualityOverviewQuery query) {
        return new MarketdataQualityOverviewScope(
                query.exchangeCode(),
                query.marketType(),
                query.symbol(),
                query.interval() == null ? null : query.interval().wireValue(),
                query.sourceType(),
                query.dataOrigin(),
                query.datasetId(),
                query.from(),
                query.to()
        );
    }

    private MarketdataQualityStatusSummary aggregateQualitySummary(List<MarketdataQualityBarScopeFacts> scopes) {
        long okCount = 0;
        long gapSignalCount = 0;
        long invalidCount = 0;
        long unknownCount = 0;
        Map<String, Long> statuses = new LinkedHashMap<>();
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            MarketdataQualityStatusSummary summary = scope.qualityStatusSummary();
            okCount += summary.okCount();
            gapSignalCount += summary.gapSignalCount();
            invalidCount += summary.invalidCount();
            unknownCount += summary.unknownQualityCount();
            for (Map.Entry<String, Long> entry : summary.statuses().entrySet()) {
                statuses.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return new MarketdataQualityStatusSummary(okCount, gapSignalCount, invalidCount, unknownCount, statuses);
    }

    private Long expectedBarsFromScopes(
            MarketdataQualityOverviewQuery query,
            List<MarketdataQualityBarScopeFacts> scopes
    ) {
        if (scopes.isEmpty() && query.interval() != null && query.from() != null && query.to() != null) {
            return expectedCount(query.from(), query.to(), query.interval().duration());
        }
        long totalExpected = 0;
        boolean hasExpected = false;
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            Instant start = query.from() == null ? scope.firstOpenTime() : query.from();
            Instant end = query.to() == null ? scope.lastOpenTime() : query.to();
            if (start == null || end == null) {
                continue;
            }
            totalExpected += expectedCount(start, end, scope.interval().duration());
            hasExpected = true;
        }
        return hasExpected ? totalExpected : null;
    }

    private long expectedCount(Instant startInclusive, Instant endInclusive, Duration interval) {
        if (endInclusive.isBefore(startInclusive)) {
            return 0;
        }
        long intervals = Math.floorDiv(Duration.between(startInclusive, endInclusive).toNanos(), interval.toNanos());
        return intervals + 1;
    }

    private Long gapCount(
            Long expectedBars,
            long totalBars,
            MarketdataQualityStatusSummary qualitySummary,
            MarketdataQualityDatasetCoverageFacts coverageFacts
    ) {
        long qualityGapSignals = qualitySummary.gapSignalCount();
        long coverageMissing = coverageFacts.missingBars() == null ? 0 : coverageFacts.missingBars();
        if (expectedBars == null) {
            long knownGap = Math.max(qualityGapSignals, coverageMissing);
            return knownGap == 0 ? null : knownGap;
        }
        long sequenceGap = Math.max(0, expectedBars - totalBars);
        return Math.max(Math.max(sequenceGap, qualityGapSignals), coverageMissing);
    }

    private long staleScopeCount(List<MarketdataQualityBarScopeFacts> scopes, Instant generatedAt) {
        long staleCount = 0;
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            Instant latest = scope.lastCloseTime() == null ? scope.lastOpenTime() : scope.lastCloseTime();
            if (latest == null) {
                continue;
            }
            Duration age = Duration.between(latest, generatedAt);
            if (!age.isNegative() && age.compareTo(freshnessThreshold(scope.interval())) > 0) {
                staleCount++;
            }
        }
        return staleCount;
    }

    private Duration freshnessThreshold(BarInterval interval) {
        Duration doubleInterval = interval.duration().multipliedBy(2);
        return doubleInterval.compareTo(MIN_FRESHNESS_THRESHOLD) >= 0 ? doubleInterval : MIN_FRESHNESS_THRESHOLD;
    }

    private MarketdataReadinessStatus freshnessStatus(long totalBars, long staleScopeCount) {
        if (totalBars == 0) {
            return MarketdataReadinessStatus.NO_DATA;
        }
        return staleScopeCount > 0 ? MarketdataReadinessStatus.STALE : MarketdataReadinessStatus.FRESH;
    }

    private MarketdataQualityStatus qualityStatus(
            long totalBars,
            Long gapCount,
            MarketdataQualityStatusSummary qualitySummary,
            MarketdataQualityDatasetCoverageFacts coverageFacts
    ) {
        long invalidBars = qualitySummary.invalidCount()
                + (coverageFacts.invalidBars() == null ? 0 : coverageFacts.invalidBars());
        if (invalidBars > 0) {
            return MarketdataQualityStatus.INVALID;
        }
        if (gapCount != null && gapCount > 0) {
            return MarketdataQualityStatus.GAP_DETECTED;
        }
        if (totalBars == 0 || qualitySummary.unknownQualityCount() > 0) {
            return MarketdataQualityStatus.INCOMPLETE;
        }
        return MarketdataQualityStatus.OK;
    }

    private MarketdataReadinessSourceHealth sourceHealth(
            long totalBars,
            Long gapCount,
            long staleScopeCount,
            MarketdataQualityStatusSummary qualitySummary,
            MarketdataQualityDatasetCoverageFacts coverageFacts,
            MarketdataQualityIngestionFacts ingestionFacts
    ) {
        long coverageInvalidBars = coverageFacts.invalidBars() == null ? 0 : coverageFacts.invalidBars();
        if (latestFailureAfterSuccess(ingestionFacts)
                || qualitySummary.invalidCount() > 0
                || coverageInvalidBars > 0) {
            return MarketdataReadinessSourceHealth.ERROR;
        }
        if (totalBars == 0) {
            return MarketdataReadinessSourceHealth.UNKNOWN;
        }
        if ((gapCount != null && gapCount > 0) || staleScopeCount > 0 || qualitySummary.unknownQualityCount() > 0) {
            return MarketdataReadinessSourceHealth.DEGRADED;
        }
        return MarketdataReadinessSourceHealth.HEALTHY;
    }

    private boolean latestFailureAfterSuccess(MarketdataQualityIngestionFacts ingestionFacts) {
        Instant lastFailureAt = ingestionFacts.lastFailureAt();
        if (lastFailureAt == null) {
            return false;
        }
        Instant lastSuccessAt = ingestionFacts.lastSuccessAt();
        return lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt);
    }

    private MarketdataQualityMetric duplicateMetric(MarketdataQualityDatasetCoverageFacts coverageFacts) {
        if (coverageFacts.datasetCount() == 0 || coverageFacts.duplicateBars() == null) {
            return MarketdataQualityMetric.notAvailable(
                    "仅 dataset coverage 表保存 duplicate_bars；当前筛选没有可用 coverage 事实。"
            );
        }
        return MarketdataQualityMetric.available(
                coverageFacts.duplicateBars(),
                "duplicate_bars 来源于最新 dataset coverage 聚合。"
        );
    }

    private MarketdataQualityMetric staleMetric(List<MarketdataQualityBarScopeFacts> scopes, long staleScopeCount) {
        if (scopes.isEmpty()) {
            return MarketdataQualityMetric.unknown("没有本地 bar scope，无法计算 stale scope 数。");
        }
        return MarketdataQualityMetric.available(staleScopeCount, "staleCount 表示最新 bar 超过本地 freshness 阈值的 scope 数。");
    }

    private MarketdataQualityDataOriginSummary dataOriginSummary(
            MarketdataQualityOverviewQuery query,
            List<MarketdataQualityBarScopeFacts> scopes
    ) {
        long fixtureBars = 0;
        long unknownOriginBars = 0;
        long localBars = 0;
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            if (scope.source() == null) {
                unknownOriginBars += scope.barCount();
            } else if (scope.source().toUpperCase(Locale.ROOT).contains("FIXTURE")) {
                fixtureBars += scope.barCount();
            } else {
                localBars += scope.barCount();
            }
        }
        return new MarketdataQualityDataOriginSummary(
                query.dataOrigin(),
                LOCAL_DB,
                localBars,
                fixtureBars,
                unknownOriginBars,
                LOCAL_DB_ONLY_SUPPORT
        );
    }

    private MarketdataQualityDatasetCoverageSummary datasetCoverageSummary(
            MarketdataQualityDatasetCoverageFacts facts
    ) {
        return new MarketdataQualityDatasetCoverageSummary(
                facts.datasetCount(),
                facts.expectedBars(),
                facts.actualBars(),
                facts.missingBars(),
                facts.duplicateBars(),
                facts.invalidBars(),
                facts.latestDatasetId(),
                facts.latestCoverageAt()
        );
    }

    private List<MarketdataQualityIssue> topIssues(
            long totalBars,
            Long gapCount,
            long staleScopeCount,
            MarketdataQualityStatusSummary qualitySummary,
            MarketdataQualityIngestionFacts ingestionFacts
    ) {
        List<MarketdataQualityIssue> issues = new ArrayList<>();
        if (totalBars == 0) {
            issues.add(new MarketdataQualityIssue(
                    "NO_DATA",
                    "WARNING",
                    1,
                    "当前筛选范围没有本地 marketdata_bars 事实。"
            ));
        }
        if (latestFailureAfterSuccess(ingestionFacts)) {
            issues.add(new MarketdataQualityIssue(
                    "INGESTION_FAILURE",
                    "ERROR",
                    1,
                    "最新本地 ingestion run 失败时间晚于最近成功时间。"
            ));
        }
        if (gapCount != null && gapCount > 0) {
            issues.add(new MarketdataQualityIssue(
                    "GAP_DETECTED",
                    "WARNING",
                    gapCount,
                    "本地 bar 序列、quality_status 或 dataset coverage 表明存在缺口。"
            ));
        }
        if (staleScopeCount > 0) {
            issues.add(new MarketdataQualityIssue(
                    "STALE_DATA",
                    "WARNING",
                    staleScopeCount,
                    "存在超过 freshness 阈值的本地 bar scope。"
            ));
        }
        if (qualitySummary.invalidCount() > 0) {
            issues.add(new MarketdataQualityIssue(
                    "INVALID_BARS",
                    "ERROR",
                    qualitySummary.invalidCount(),
                    "本地 quality_status 或 OHLCV 质量证据显示存在非法 bar。"
            ));
        }
        return issues;
    }

    private Instant latestBarTime(List<MarketdataQualityBarScopeFacts> scopes) {
        Instant latest = null;
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            latest = maxInstant(latest, scope.lastCloseTime());
            latest = maxInstant(latest, scope.lastOpenTime());
        }
        return latest;
    }

    private Instant earliestBarTime(List<MarketdataQualityBarScopeFacts> scopes) {
        Instant earliest = null;
        for (MarketdataQualityBarScopeFacts scope : scopes) {
            Instant candidate = scope.firstOpenTime();
            if (candidate == null) {
                continue;
            }
            earliest = earliest == null || candidate.isBefore(earliest) ? candidate : earliest;
        }
        return earliest;
    }

    private Instant maxInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private Long firstNonNull(Long first, Long second) {
        return first == null ? second : first;
    }
}
