package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcMarketdataDatasetRepository 是 GateH-3 dataset 和 coverage 的 JDBC 实现。
 * <p>
 * Why:
 * 覆盖率、缺口和非法 bar 统计依赖 `marketdata_bars` 聚合 SQL，必须留在 infra 层；
 * core 只通过端口拿到统计结果，避免把数据库实现细节泄漏到应用服务。
 */
@Repository
public class JdbcMarketdataDatasetRepository implements MarketdataDatasetRepository {

    private static final String BASE_SELECT = """
            SELECT dataset_id, dataset_name, exchange_code, market_type, symbol, "interval",
                   start_time, end_time, status, quality_status, bar_count, gap_count,
                   source, created_by, created_at, updated_at, request_json::text AS request_json
            FROM marketdata_datasets
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMarketdataDatasetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(MarketdataDataset dataset) {
        jdbcTemplate.update(
                """
                        INSERT INTO marketdata_datasets (
                            dataset_id, dataset_name, exchange_code, market_type, symbol, "interval",
                            start_time, end_time, status, quality_status, bar_count, gap_count,
                            source, created_by, created_at, updated_at, request_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                        """,
                dataset.datasetId(),
                dataset.datasetName(),
                dataset.exchangeCode(),
                dataset.marketType(),
                dataset.symbol(),
                dataset.interval().wireValue(),
                Timestamp.from(dataset.startTime()),
                Timestamp.from(dataset.endTime()),
                dataset.status().name(),
                dataset.qualityStatus().name(),
                dataset.barCount(),
                dataset.gapCount(),
                dataset.source(),
                dataset.createdBy(),
                Timestamp.from(dataset.createdAt()),
                Timestamp.from(dataset.updatedAt()),
                dataset.requestJson()
        );
    }

    @Override
    public Optional<MarketdataDataset> findByDatasetId(UUID datasetId) {
        List<MarketdataDataset> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE dataset_id = ?",
                rowMapper(),
                datasetId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<MarketdataDataset> list(String exchangeCode, String marketType, String symbol, String interval) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "exchange_code", exchangeCode);
        appendFilter(sql, args, "market_type", marketType);
        appendFilter(sql, args, "symbol", symbol);
        appendFilter(sql, args, "\"interval\"", interval);
        sql.append(" ORDER BY updated_at DESC, dataset_id DESC");
        return jdbcTemplate.query(sql.toString(), rowMapper(), args.toArray());
    }

    @Override
    public MarketdataDatasetCoverage calculateCoverage(MarketdataDataset dataset, Instant calculatedAt) {
        CoverageCounts counts = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) AS actual_bars,
                               COALESCE(SUM(CASE
                                   WHEN quality_status <> 'OK'
                                     OR open_price <= 0
                                     OR high_price <= 0
                                     OR low_price <= 0
                                     OR close_price <= 0
                                     OR volume < 0
                                   THEN 1 ELSE 0 END), 0) AS invalid_bars
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND market_type = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time >= ?
                          AND close_time <= ?
                        """,
                (resultSet, rowNum) -> new CoverageCounts(
                        resultSet.getLong("actual_bars"),
                        resultSet.getLong("invalid_bars")
                ),
                dataset.exchangeCode(),
                dataset.marketType(),
                dataset.symbol(),
                dataset.interval().wireValue(),
                Timestamp.from(dataset.startTime()),
                Timestamp.from(dataset.endTime())
        );
        long expectedBars = expectedBars(dataset.interval(), dataset.startTime(), dataset.endTime());
        long actualBars = counts == null ? 0 : counts.actualBars();
        long invalidBars = counts == null ? 0 : counts.invalidBars();
        long missingBars = Math.max(0, expectedBars - actualBars);
        MarketdataQualityStatus qualityStatus = resolveQualityStatus(expectedBars, actualBars, missingBars, invalidBars);
        String summaryJson = buildSummaryJson(dataset, expectedBars, actualBars, missingBars, invalidBars);
        return new MarketdataDatasetCoverage(
                UUID.randomUUID(),
                dataset.datasetId(),
                dataset.startTime(),
                dataset.endTime(),
                expectedBars,
                actualBars,
                missingBars,
                0,
                invalidBars,
                qualityStatus,
                summaryJson,
                calculatedAt
        );
    }

    @Override
    public void insertCoverage(MarketdataDatasetCoverage coverage) {
        jdbcTemplate.update(
                """
                        INSERT INTO marketdata_dataset_coverage (
                            coverage_id, dataset_id, range_start_time, range_end_time,
                            expected_bars, actual_bars, missing_bars, duplicate_bars, invalid_bars,
                            quality_status, summary_json, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                        """,
                coverage.coverageId(),
                coverage.datasetId(),
                Timestamp.from(coverage.rangeStartTime()),
                Timestamp.from(coverage.rangeEndTime()),
                coverage.expectedBars(),
                coverage.actualBars(),
                coverage.missingBars(),
                coverage.duplicateBars(),
                coverage.invalidBars(),
                coverage.qualityStatus().name(),
                coverage.summaryJson(),
                Timestamp.from(coverage.createdAt())
        );
    }

    @Override
    public boolean updateQuality(
            UUID datasetId,
            MarketdataDatasetStatus status,
            MarketdataQualityStatus qualityStatus,
            long barCount,
            long gapCount,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE marketdata_datasets
                        SET status = ?,
                            quality_status = ?,
                            bar_count = ?,
                            gap_count = ?,
                            updated_at = ?
                        WHERE dataset_id = ?
                        """,
                status.name(),
                qualityStatus.name(),
                barCount,
                gapCount,
                Timestamp.from(updatedAt),
                datasetId
        ) > 0;
    }

    private RowMapper<MarketdataDataset> rowMapper() {
        return this::mapRow;
    }

    private MarketdataDataset mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new MarketdataDataset(
                resultSet.getObject("dataset_id", UUID.class),
                resultSet.getString("dataset_name"),
                resultSet.getString("exchange_code"),
                resultSet.getString("market_type"),
                resultSet.getString("symbol"),
                BarInterval.fromWireValue(resultSet.getString("interval")),
                resultSet.getTimestamp("start_time").toInstant(),
                resultSet.getTimestamp("end_time").toInstant(),
                MarketdataDatasetStatus.valueOf(resultSet.getString("status")),
                MarketdataQualityStatus.valueOf(resultSet.getString("quality_status")),
                resultSet.getLong("bar_count"),
                resultSet.getLong("gap_count"),
                resultSet.getString("source"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("request_json")
        );
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String columnName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(args.isEmpty() ? " WHERE " : " AND ");
        sql.append(columnName).append(" = ?");
        args.add(value);
    }

    private long expectedBars(BarInterval interval, Instant startTime, Instant endTime) {
        long intervalMillis = intervalDuration(interval).toMillis();
        long rangeMillis = Duration.between(startTime, endTime).toMillis();
        if (rangeMillis < 0) {
            return 0;
        }
        return rangeMillis / intervalMillis + 1;
    }

    private Duration intervalDuration(BarInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> Duration.ofMinutes(1);
            case FIVE_MINUTES -> Duration.ofMinutes(5);
            case FIFTEEN_MINUTES -> Duration.ofMinutes(15);
            case ONE_HOUR -> Duration.ofHours(1);
            case FOUR_HOURS -> Duration.ofHours(4);
            case ONE_DAY -> Duration.ofDays(1);
        };
    }

    private MarketdataQualityStatus resolveQualityStatus(
            long expectedBars,
            long actualBars,
            long missingBars,
            long invalidBars
    ) {
        if (actualBars == 0 || expectedBars == 0) {
            return MarketdataQualityStatus.INCOMPLETE;
        }
        if (invalidBars > 0) {
            return MarketdataQualityStatus.INVALID;
        }
        if (missingBars > 0) {
            return MarketdataQualityStatus.GAP_DETECTED;
        }
        return MarketdataQualityStatus.OK;
    }

    private String buildSummaryJson(
            MarketdataDataset dataset,
            long expectedBars,
            long actualBars,
            long missingBars,
            long invalidBars
    ) {
        try {
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("datasetId", dataset.datasetId().toString());
            summary.put("exchangeCode", dataset.exchangeCode());
            summary.put("marketType", dataset.marketType());
            summary.put("symbol", dataset.symbol());
            summary.put("interval", dataset.interval().wireValue());
            summary.put("expectedBars", expectedBars);
            summary.put("actualBars", actualBars);
            summary.put("missingBars", missingBars);
            summary.put("duplicateBars", 0);
            summary.put("invalidBars", invalidBars);
            summary.put("source", dataset.source());
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build dataset coverage summary", ex);
        }
    }

    private record CoverageCounts(long actualBars, long invalidBars) {
    }
}
