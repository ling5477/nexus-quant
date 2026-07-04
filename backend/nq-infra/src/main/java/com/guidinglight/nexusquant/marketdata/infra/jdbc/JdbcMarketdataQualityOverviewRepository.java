package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityBarScopeFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataQualityOverviewRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcMarketdataQualityOverviewRepository 只读聚合 Data Quality Center 所需本地 DB 事实。
 * <p>
 * Why:
 * GateP Batch 2 的 overview 必须复用现有 `marketdata_bars`、dataset coverage 与 ingestion run 表；
 * 本实现只有 SELECT，不写库、不调用 adapter、不读取 credential，也不会创建真实 public outbound provider。
 */
@Repository
public class JdbcMarketdataQualityOverviewRepository implements MarketdataQualityOverviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketdataQualityOverviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<MarketdataQualityBarScopeFacts> loadBarScopeFacts(MarketdataQualityOverviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        QuerySpec querySpec = barsQuerySpec(query);
        return jdbcTemplate.query(
                """
                        SELECT b.exchange_code,
                               b.market_type,
                               b.symbol,
                               b."interval",
                               b.source,
                               COUNT(*) AS bar_count,
                               MIN(b.open_time) AS first_open_time,
                               MAX(b.open_time) AS last_open_time,
                               MAX(b.close_time) AS last_close_time,
                               COALESCE(SUM(CASE WHEN quality_key = 'OK' THEN 1 ELSE 0 END), 0) AS ok_count,
                               COALESCE(SUM(CASE
                                   WHEN quality_key IN ('GAP', 'GAP_DETECTED', 'INCOMPLETE', 'DUPLICATE_SKIPPED')
                                   THEN 1 ELSE 0 END), 0) AS gap_count,
                               COALESCE(SUM(CASE WHEN quality_key = 'UNKNOWN' THEN 1 ELSE 0 END), 0) AS unknown_count,
                               COALESCE(SUM(CASE
                                   WHEN quality_key NOT IN (
                                       'OK', 'GAP', 'GAP_DETECTED', 'INCOMPLETE', 'DUPLICATE_SKIPPED', 'UNKNOWN'
                                   )
                                   THEN 1 ELSE 0 END), 0) AS invalid_count
                        FROM (
                            SELECT *,
                                   COALESCE(NULLIF(BTRIM(UPPER(quality_status)), ''), 'UNKNOWN') AS quality_key
                            FROM marketdata_bars
                        ) b
                        %s
                        GROUP BY b.exchange_code, b.market_type, b.symbol, b."interval", b.source
                        ORDER BY b.exchange_code, b.market_type, b.symbol, b."interval", b.source
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new MarketdataQualityBarScopeFacts(
                        rs.getString("exchange_code"),
                        rs.getString("market_type"),
                        rs.getString("symbol"),
                        BarInterval.fromWireValue(rs.getString("interval")),
                        rs.getString("source"),
                        rs.getLong("bar_count"),
                        toInstant(rs.getTimestamp("first_open_time")),
                        toInstant(rs.getTimestamp("last_open_time")),
                        toInstant(rs.getTimestamp("last_close_time")),
                        qualitySummary(
                                rs.getLong("ok_count"),
                                rs.getLong("gap_count"),
                                rs.getLong("invalid_count"),
                                rs.getLong("unknown_count")
                        )
                ),
                querySpec.args()
        );
    }

    @Override
    public MarketdataQualityIngestionFacts loadIngestionFacts(MarketdataQualityOverviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        QuerySpec querySpec = ingestionQuerySpec(query);
        MarketdataQualityIngestionFacts aggregateFacts = jdbcTemplate.queryForObject(
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
                (rs, rowNum) -> new MarketdataQualityIngestionFacts(
                        toInstant(rs.getTimestamp("last_success_at")),
                        toInstant(rs.getTimestamp("last_failure_at")),
                        null,
                        null
                ),
                querySpec.args()
        );
        List<LatestRunFacts> latestRuns = jdbcTemplate.query(
                """
                        SELECT r.run_id,
                               r.status
                        FROM marketdata_ingestion_jobs j
                        JOIN marketdata_ingestion_runs r ON r.job_id = j.job_id
                        %s
                        ORDER BY COALESCE(r.finished_at, r.started_at, r.created_at) DESC, r.run_id DESC
                        LIMIT 1
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new LatestRunFacts(
                        rs.getObject("run_id", UUID.class),
                        rs.getString("status")
                ),
                querySpec.args()
        );
        LatestRunFacts latestRun = latestRuns.stream().findFirst().orElse(null);
        return new MarketdataQualityIngestionFacts(
                aggregateFacts == null ? null : aggregateFacts.lastSuccessAt(),
                aggregateFacts == null ? null : aggregateFacts.lastFailureAt(),
                latestRun == null ? null : latestRun.runId(),
                latestRun == null ? null : latestRun.status()
        );
    }

    @Override
    public MarketdataQualityDatasetCoverageFacts loadDatasetCoverageFacts(MarketdataQualityOverviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        QuerySpec querySpec = datasetCoverageQuerySpec(query);
        MarketdataQualityDatasetCoverageFacts facts = jdbcTemplate.queryForObject(
                """
                        WITH latest_coverage AS (
                            SELECT DISTINCT ON (c.dataset_id)
                                   c.dataset_id,
                                   c.expected_bars,
                                   c.actual_bars,
                                   c.missing_bars,
                                   c.duplicate_bars,
                                   c.invalid_bars,
                                   c.created_at
                            FROM marketdata_dataset_coverage c
                            JOIN marketdata_datasets d ON d.dataset_id = c.dataset_id
                            %s
                            ORDER BY c.dataset_id, c.created_at DESC
                        )
                        SELECT COUNT(*) AS dataset_count,
                               SUM(expected_bars) AS expected_bars,
                               SUM(actual_bars) AS actual_bars,
                               SUM(missing_bars) AS missing_bars,
                               SUM(duplicate_bars) AS duplicate_bars,
                               SUM(invalid_bars) AS invalid_bars,
                               (ARRAY_AGG(dataset_id ORDER BY created_at DESC))[1] AS latest_dataset_id,
                               MAX(created_at) AS latest_coverage_at
                        FROM latest_coverage
                        """.formatted(querySpec.whereClause()),
                (rs, rowNum) -> new MarketdataQualityDatasetCoverageFacts(
                        rs.getLong("dataset_count"),
                        nullableLong(rs.getObject("expected_bars")),
                        nullableLong(rs.getObject("actual_bars")),
                        nullableLong(rs.getObject("missing_bars")),
                        nullableLong(rs.getObject("duplicate_bars")),
                        nullableLong(rs.getObject("invalid_bars")),
                        rs.getObject("latest_dataset_id", UUID.class),
                        toInstant(rs.getTimestamp("latest_coverage_at"))
                ),
                querySpec.args()
        );
        return facts == null ? MarketdataQualityDatasetCoverageFacts.empty() : facts;
    }

    private QuerySpec barsQuerySpec(MarketdataQualityOverviewQuery query) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        List<Object> args = new ArrayList<>();
        appendFilter(where, args, "b.exchange_code", query.exchangeCode());
        appendFilter(where, args, "b.market_type", query.marketType());
        appendFilter(where, args, "b.symbol", query.symbol());
        if (query.interval() != null) {
            appendFilter(where, args, "b.\"interval\"", query.interval().wireValue());
        }
        appendFilter(where, args, "b.source", query.sourceType());
        appendTimeLowerBound(where, args, "b.open_time", query.from());
        appendTimeUpperBound(where, args, "b.close_time", query.to());
        appendDatasetScopeExists(where, args, query.datasetId(), "b", "b.open_time", "b.close_time");
        return new QuerySpec(where.toString(), args.toArray());
    }

    private QuerySpec ingestionQuerySpec(MarketdataQualityOverviewQuery query) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        List<Object> args = new ArrayList<>();
        appendFilter(where, args, "j.exchange_code", query.exchangeCode());
        appendFilter(where, args, "j.market_type", query.marketType());
        appendFilter(where, args, "j.symbol", query.symbol());
        if (query.interval() != null) {
            appendFilter(where, args, "j.\"interval\"", query.interval().wireValue());
        }
        appendFilter(where, args, "j.source", query.sourceType());
        appendTimeOverlap(where, args, "j.start_time", "j.end_time", query.from(), query.to());
        appendDatasetScopeExists(where, args, query.datasetId(), "j", "j.start_time", "j.end_time");
        return new QuerySpec(where.toString(), args.toArray());
    }

    private QuerySpec datasetCoverageQuerySpec(MarketdataQualityOverviewQuery query) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        List<Object> args = new ArrayList<>();
        appendFilter(where, args, "d.dataset_id", query.datasetId());
        appendFilter(where, args, "d.exchange_code", query.exchangeCode());
        appendFilter(where, args, "d.market_type", query.marketType());
        appendFilter(where, args, "d.symbol", query.symbol());
        if (query.interval() != null) {
            appendFilter(where, args, "d.\"interval\"", query.interval().wireValue());
        }
        appendFilter(where, args, "d.source", query.sourceType());
        appendTimeOverlap(where, args, "c.range_start_time", "c.range_end_time", query.from(), query.to());
        return new QuerySpec(where.toString(), args.toArray());
    }

    private void appendFilter(StringBuilder where, List<Object> args, String columnName, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        where.append("  AND ").append(columnName).append(" = ?\n");
        args.add(value);
    }

    private void appendTimeLowerBound(StringBuilder where, List<Object> args, String columnName, Instant value) {
        if (value == null) {
            return;
        }
        where.append("  AND ").append(columnName).append(" >= ?\n");
        args.add(Timestamp.from(value));
    }

    private void appendTimeUpperBound(StringBuilder where, List<Object> args, String columnName, Instant value) {
        if (value == null) {
            return;
        }
        where.append("  AND ").append(columnName).append(" <= ?\n");
        args.add(Timestamp.from(value));
    }

    private void appendTimeOverlap(
            StringBuilder where,
            List<Object> args,
            String startColumn,
            String endColumn,
            Instant from,
            Instant to
    ) {
        if (from != null) {
            where.append("  AND ").append(endColumn).append(" >= ?\n");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append("  AND ").append(startColumn).append(" <= ?\n");
            args.add(Timestamp.from(to));
        }
    }

    private void appendDatasetScopeExists(
            StringBuilder where,
            List<Object> args,
            UUID datasetId,
            String alias,
            String scopeStartColumn,
            String scopeEndColumn
    ) {
        if (datasetId == null) {
            return;
        }
        where.append("""
                  AND EXISTS (
                      SELECT 1
                      FROM marketdata_datasets d
                      WHERE d.dataset_id = ?
                        AND d.exchange_code = %1$s.exchange_code
                        AND d.market_type = %1$s.market_type
                        AND d.symbol = %1$s.symbol
                        AND d."interval" = %1$s."interval"
                        AND %2$s <= d.end_time
                        AND %3$s >= d.start_time
                  )
                """.formatted(alias, scopeStartColumn, scopeEndColumn));
        args.add(datasetId);
    }

    private MarketdataQualityStatusSummary qualitySummary(long okCount, long gapCount, long invalidCount, long unknownCount) {
        Map<String, Long> statuses = new LinkedHashMap<>();
        statuses.put("OK", okCount);
        statuses.put("GAP_DETECTED", gapCount);
        statuses.put("INVALID", invalidCount);
        statuses.put("UNKNOWN", unknownCount);
        return new MarketdataQualityStatusSummary(okCount, gapCount, invalidCount, unknownCount, statuses);
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QuerySpec(String whereClause, Object[] args) {
    }

    private record LatestRunFacts(UUID runId, String status) {
        private LatestRunFacts {
            status = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        }
    }
}
