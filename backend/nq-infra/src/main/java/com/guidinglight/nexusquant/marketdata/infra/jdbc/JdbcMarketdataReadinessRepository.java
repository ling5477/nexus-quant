package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessBarFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessQuery;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataReadinessRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcMarketdataReadinessRepository aggregates GateM-2E readiness facts from existing local tables.
 * <p>
 * Why: readiness must use bounded DB reads only. This repository never calls exchange adapters,
 * WebSocket clients, ingestion commands or credential paths.
 */
@Repository
public class JdbcMarketdataReadinessRepository implements MarketdataReadinessRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketdataReadinessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public MarketdataReadinessBarFacts loadBarFacts(MarketdataReadinessQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        QuerySpec querySpec = barsQuerySpec(query);
        MarketdataReadinessBarFacts baseFacts = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) AS bar_count,
                               MIN(open_time) AS first_open_time,
                               MAX(open_time) AS last_open_time,
                               MAX(close_time) AS last_close_time
                        FROM marketdata_bars
                        %s
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new MarketdataReadinessBarFacts(
                        rs.getLong("bar_count"),
                        toInstant(rs.getTimestamp("first_open_time")),
                        toInstant(rs.getTimestamp("last_open_time")),
                        toInstant(rs.getTimestamp("last_close_time")),
                        MarketdataQualityStatusSummary.empty()
                ),
                querySpec.args()
        );
        Map<String, Long> statusCounts = loadQualityStatusCounts(querySpec);
        return new MarketdataReadinessBarFacts(
                baseFacts == null ? 0 : baseFacts.barCount(),
                baseFacts == null ? null : baseFacts.firstOpenTime(),
                baseFacts == null ? null : baseFacts.lastOpenTime(),
                baseFacts == null ? null : baseFacts.lastCloseTime(),
                toQualitySummary(statusCounts)
        );
    }

    @Override
    public MarketdataReadinessIngestionFacts loadIngestionFacts(MarketdataReadinessQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        QuerySpec querySpec = ingestionScopeQuerySpec(query);
        MarketdataReadinessIngestionFacts aggregateFacts = jdbcTemplate.queryForObject(
                """
                        SELECT MAX(r.finished_at) FILTER (
                                   WHERE r.status IN ('SUCCEEDED', 'PARTIAL') AND r.finished_at IS NOT NULL
                               ) AS last_success_at,
                               MAX(r.finished_at) FILTER (
                                   WHERE r.status = 'FAILED' AND r.finished_at IS NOT NULL
                               ) AS last_failure_at
                        FROM marketdata_ingestion_jobs j
                        LEFT JOIN marketdata_ingestion_runs r ON r.job_id = j.job_id
                        %s
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new MarketdataReadinessIngestionFacts(
                        toInstant(rs.getTimestamp("last_success_at")),
                        toInstant(rs.getTimestamp("last_failure_at")),
                        null,
                        null
                ),
                querySpec.args()
        );
        List<LatestRunFacts> latestRuns = jdbcTemplate.query(
                """
                        SELECT r.status,
                               r.started_at,
                               r.finished_at
                        FROM marketdata_ingestion_jobs j
                        JOIN marketdata_ingestion_runs r ON r.job_id = j.job_id
                        %s
                        ORDER BY COALESCE(r.finished_at, r.started_at, r.created_at) DESC
                        LIMIT 1
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new LatestRunFacts(
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("started_at")),
                        toInstant(rs.getTimestamp("finished_at"))
                ),
                querySpec.args()
        );
        LatestRunFacts latestRun = latestRuns.stream().findFirst().orElse(null);
        Long latestLatencyMs = latestRun == null ? null : latestRun.latencyMs();
        String latestRunStatus = latestRun == null ? null : latestRun.status();
        return new MarketdataReadinessIngestionFacts(
                aggregateFacts == null ? null : aggregateFacts.lastSuccessAt(),
                aggregateFacts == null ? null : aggregateFacts.lastFailureAt(),
                latestLatencyMs,
                latestRunStatus
        );
    }

    private Map<String, Long> loadQualityStatusCounts(QuerySpec querySpec) {
        List<StatusCount> rows = jdbcTemplate.query(
                """
                        SELECT COALESCE(NULLIF(BTRIM(UPPER(quality_status)), ''), 'UNKNOWN') AS quality_status,
                               COUNT(*) AS status_count
                        FROM marketdata_bars
                        %s
                        GROUP BY COALESCE(NULLIF(BTRIM(UPPER(quality_status)), ''), 'UNKNOWN')
                        ORDER BY quality_status
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new StatusCount(
                        rs.getString("quality_status"),
                        rs.getLong("status_count")
                ),
                querySpec.args()
        );
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StatusCount row : rows) {
            counts.put(normalizeStatus(row.status()), row.count());
        }
        return counts;
    }

    private MarketdataQualityStatusSummary toQualitySummary(Map<String, Long> statusCounts) {
        long okCount = 0;
        long gapSignalCount = 0;
        long invalidCount = 0;
        long unknownCount = 0;
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            String status = normalizeStatus(entry.getKey());
            long count = entry.getValue() == null ? 0 : entry.getValue();
            if ("OK".equals(status)) {
                okCount += count;
            } else if (isGapStatus(status)) {
                gapSignalCount += count;
            } else if (isUnknownStatus(status)) {
                unknownCount += count;
            } else {
                invalidCount += count;
            }
        }
        return new MarketdataQualityStatusSummary(okCount, gapSignalCount, invalidCount, unknownCount, statusCounts);
    }

    private QuerySpec barsQuerySpec(MarketdataReadinessQuery query) {
        StringBuilder whereClause = new StringBuilder("""
                WHERE exchange_code = ?
                  AND market_type = ?
                  AND symbol = ?
                  AND "interval" = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(query.exchangeCode());
        args.add(query.marketType());
        args.add(query.symbol());
        args.add(query.interval().wireValue());
        if (query.from() != null) {
            whereClause.append("  AND open_time >= ?\n");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            whereClause.append("  AND close_time <= ?\n");
            args.add(Timestamp.from(query.to()));
        }
        return new QuerySpec(whereClause.toString(), args.toArray());
    }

    private QuerySpec ingestionScopeQuerySpec(MarketdataReadinessQuery query) {
        String whereClause = """
                WHERE j.exchange_code = ?
                  AND j.market_type = ?
                  AND j.symbol = ?
                  AND j."interval" = ?
                """;
        return new QuerySpec(
                whereClause,
                new Object[]{
                        query.exchangeCode(),
                        query.marketType(),
                        query.symbol(),
                        query.interval().wireValue()
                }
        );
    }

    private boolean isGapStatus(String status) {
        return "GAP".equals(status)
                || "GAP_DETECTED".equals(status)
                || "INCOMPLETE".equals(status)
                || "DUPLICATE_SKIPPED".equals(status);
    }

    private boolean isUnknownStatus(String status) {
        return "UNKNOWN".equals(status);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QuerySpec(String whereClause, Object[] args) {
    }

    private record StatusCount(String status, long count) {
    }

    private record LatestRunFacts(String status, Instant startedAt, Instant finishedAt) {
        private Long latencyMs() {
            if (startedAt == null || finishedAt == null) {
                return null;
            }
            return Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
        }
    }
}
