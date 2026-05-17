package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataIngestionJobRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcMarketdataIngestionJobRepository 提供 GateH-2 ingestion jobs/runs 的 JDBC 实现。
 * <p>
 * Why:
 * application service 只处理任务语义；所有 SQL、JSONB cast、状态持久化和断点查询都集中在 infra。
 */
@Repository
public class JdbcMarketdataIngestionJobRepository implements MarketdataIngestionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketdataIngestionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public MarketdataIngestionJob createJob(MarketdataIngestionJob job) {
        jdbcTemplate.update(
                """
                        INSERT INTO marketdata_ingestion_jobs (
                            job_id, exchange_code, market_type, symbol, "interval", start_time, end_time,
                            status, source, created_by, created_at, updated_at, request_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """,
                job.jobId(),
                job.exchangeCode(),
                job.marketType(),
                job.symbol(),
                job.interval().wireValue(),
                Timestamp.from(job.startTime()),
                Timestamp.from(job.endTime()),
                job.status().name(),
                job.source(),
                job.createdBy(),
                Timestamp.from(job.createdAt()),
                Timestamp.from(job.updatedAt()),
                job.requestJson()
        );
        return job;
    }

    @Override
    public List<MarketdataIngestionJob> listJobs() {
        return jdbcTemplate.query(
                """
                        SELECT job_id, exchange_code, market_type, symbol, "interval", start_time, end_time,
                               status, source, created_by, created_at, updated_at, request_json
                        FROM marketdata_ingestion_jobs
                        ORDER BY updated_at DESC
                        LIMIT 100
                        """,
                (rs, rowNum) -> mapJob(rs)
        );
    }

    @Override
    public Optional<MarketdataIngestionJob> findJob(UUID jobId) {
        List<MarketdataIngestionJob> jobs = jdbcTemplate.query(
                """
                        SELECT job_id, exchange_code, market_type, symbol, "interval", start_time, end_time,
                               status, source, created_by, created_at, updated_at, request_json
                        FROM marketdata_ingestion_jobs
                        WHERE job_id = ?
                        """,
                (rs, rowNum) -> mapJob(rs),
                jobId
        );
        return jobs.stream().findFirst();
    }

    @Override
    public Optional<Instant> findLatestSuccessfulActualEnd(UUID jobId) {
        List<Instant> values = jdbcTemplate.query(
                """
                        SELECT actual_end_time
                        FROM marketdata_ingestion_runs
                        WHERE job_id = ?
                          AND status IN ('SUCCEEDED', 'PARTIAL')
                          AND actual_end_time IS NOT NULL
                        ORDER BY actual_end_time DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getTimestamp("actual_end_time").toInstant(),
                jobId
        );
        return values.stream().findFirst();
    }

    @Override
    public void updateJobStatus(UUID jobId, MarketdataIngestionStatus status, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        UPDATE marketdata_ingestion_jobs
                        SET status = ?, updated_at = ?
                        WHERE job_id = ?
                        """,
                status.name(),
                Timestamp.from(updatedAt),
                jobId
        );
    }

    @Override
    public MarketdataIngestionRun createRun(MarketdataIngestionRun run) {
        jdbcTemplate.update(
                """
                        INSERT INTO marketdata_ingestion_runs (
                            run_id, job_id, status, started_at, finished_at, requested_start_time,
                            requested_end_time, actual_start_time, actual_end_time, fetched_bars,
                            inserted_bars, updated_bars, skipped_bars, error_message, raw_summary_json, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                run.runId(),
                run.jobId(),
                run.status().name(),
                Timestamp.from(run.startedAt()),
                toTimestamp(run.finishedAt()),
                Timestamp.from(run.requestedStartTime()),
                Timestamp.from(run.requestedEndTime()),
                toTimestamp(run.actualStartTime()),
                toTimestamp(run.actualEndTime()),
                run.fetchedBars(),
                run.insertedBars(),
                run.updatedBars(),
                run.skippedBars(),
                run.errorMessage(),
                run.rawSummaryJson(),
                Timestamp.from(run.createdAt())
        );
        return run;
    }

    @Override
    public MarketdataIngestionRun finishRun(MarketdataIngestionRun run) {
        jdbcTemplate.update(
                """
                        UPDATE marketdata_ingestion_runs
                        SET status = ?,
                            finished_at = ?,
                            actual_start_time = ?,
                            actual_end_time = ?,
                            fetched_bars = ?,
                            inserted_bars = ?,
                            updated_bars = ?,
                            skipped_bars = ?,
                            error_message = ?,
                            raw_summary_json = ?::jsonb
                        WHERE run_id = ?
                        """,
                run.status().name(),
                toTimestamp(run.finishedAt()),
                toTimestamp(run.actualStartTime()),
                toTimestamp(run.actualEndTime()),
                run.fetchedBars(),
                run.insertedBars(),
                run.updatedBars(),
                run.skippedBars(),
                run.errorMessage(),
                run.rawSummaryJson(),
                run.runId()
        );
        return run;
    }

    @Override
    public List<MarketdataIngestionRun> listRuns(UUID jobId) {
        return jdbcTemplate.query(
                """
                        SELECT run_id, job_id, status, started_at, finished_at, requested_start_time,
                               requested_end_time, actual_start_time, actual_end_time, fetched_bars,
                               inserted_bars, updated_bars, skipped_bars, error_message, raw_summary_json, created_at
                        FROM marketdata_ingestion_runs
                        WHERE job_id = ?
                        ORDER BY started_at DESC
                        LIMIT 100
                        """,
                (rs, rowNum) -> mapRun(rs),
                jobId
        );
    }

    private MarketdataIngestionJob mapJob(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MarketdataIngestionJob(
                rs.getObject("job_id", UUID.class),
                rs.getString("exchange_code"),
                rs.getString("market_type"),
                rs.getString("symbol"),
                BarInterval.fromWireValue(rs.getString("interval")),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                MarketdataIngestionStatus.valueOf(rs.getString("status")),
                rs.getString("source"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("request_json")
        );
    }

    private MarketdataIngestionRun mapRun(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MarketdataIngestionRun(
                rs.getObject("run_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                MarketdataIngestionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                toInstant(rs.getTimestamp("finished_at")),
                rs.getTimestamp("requested_start_time").toInstant(),
                rs.getTimestamp("requested_end_time").toInstant(),
                toInstant(rs.getTimestamp("actual_start_time")),
                toInstant(rs.getTimestamp("actual_end_time")),
                rs.getInt("fetched_bars"),
                rs.getInt("inserted_bars"),
                rs.getInt("updated_bars"),
                rs.getInt("skipped_bars"),
                rs.getString("error_message"),
                rs.getString("raw_summary_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
