package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 连续 SIM 的 run-local 进度与已观察 bar；经济事实仍由 canonical 路径拥有。 */
@Repository
public class ContinuousSimRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ContinuousSimRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public boolean exists(String runId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM continuous_sim_runs WHERE paper_run_id=?)",
                Boolean.class, runId));
    }

    public void create(String runId, String versionId, String checksum, String costSha,
                       List<HistoricalBar> seed, Instant now) {
        HistoricalBar last = seed.getLast();
        jdbc.update("""
                INSERT INTO continuous_sim_runs(paper_run_id,status,strategy_version_id,strategy_checksum,
                    cost_sha256,seed_last_open_time,last_processed_open_time,last_processed_close_time,
                    last_processed_sha256,last_observed_open_time,started_at,updated_at)
                VALUES (?,'RUNNING',?,?,?,?,?,?,?,?,?,?)
                """, runId, versionId, checksum, costSha,
                Timestamp.from(last.openTime()), Timestamp.from(last.openTime()),
                Timestamp.from(last.closeTime()), contentSha(last), Timestamp.from(last.openTime()),
                Timestamp.from(now), Timestamp.from(now));
        for (HistoricalBar bar : seed) insertBar(runId, bar, now);
    }

    public Cursor get(String runId) {
        return jdbc.query("""
                SELECT paper_run_id,status,strategy_version_id,strategy_checksum,cost_sha256,
                       seed_last_open_time,last_processed_open_time,last_processed_close_time,
                       last_processed_sha256,last_observed_open_time,gap_start_open_time,started_at,last_poll_at,
                       last_successful_poll_at,updated_at,block_reason,consecutive_poll_failures
                FROM continuous_sim_runs WHERE paper_run_id=?
                """, (rs, row) -> new Cursor(rs.getString("paper_run_id"), rs.getString("status"),
                rs.getString("strategy_version_id"), rs.getString("strategy_checksum"),
                rs.getString("cost_sha256"), instant(rs, "seed_last_open_time"),
                instant(rs, "last_processed_open_time"), instant(rs, "last_processed_close_time"),
                rs.getString("last_processed_sha256"), instant(rs, "last_observed_open_time"),
                instant(rs, "gap_start_open_time"),
                instant(rs, "started_at"), instant(rs, "last_poll_at"),
                instant(rs, "last_successful_poll_at"), instant(rs, "updated_at"),
                rs.getString("block_reason"), rs.getInt("consecutive_poll_failures")), runId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("CONTINUOUS_SIM_RUN_NOT_FOUND"));
    }

    public List<String> activeIds() {
        return jdbc.queryForList("""
                SELECT paper_run_id FROM continuous_sim_runs WHERE status <> 'STOPPED'
                ORDER BY started_at LIMIT 8
                """, String.class);
    }

    public void requireAdmissionCapacity() {
        // start 外层事务持有同一把 PG 事务锁直到 run 创建完成，避免跨实例并发超过轮询上限。
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended('continuous-sim-admission',9253))");
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM continuous_sim_runs WHERE status <> 'STOPPED'
                """, Integer.class);
        if (active == null || active >= 8) {
            throw new IllegalStateException("CONTINUOUS_SIM_CAPACITY_EXCEEDED");
        }
    }

    public void stop(String runId, Instant now) {
        if (jdbc.update("""
                UPDATE continuous_sim_runs SET status='STOPPED',block_reason='STOPPED',updated_at=?
                WHERE paper_run_id=? AND status <> 'STOPPED'
                """, Timestamp.from(now), runId) != 1) {
            throw new IllegalStateException("CONTINUOUS_SIM_ALREADY_STOPPED");
        }
    }

    public void resume(String runId, Instant now) {
        if (jdbc.update("""
                UPDATE continuous_sim_runs SET status='RUNNING',block_reason=NULL,
                    consecutive_poll_failures=0,updated_at=?
                WHERE paper_run_id=? AND status='STOPPED'
                """, Timestamp.from(now), runId) != 1) {
            throw new IllegalStateException("CONTINUOUS_SIM_NOT_STOPPED");
        }
    }

    public void pollResult(String runId, Instant now, Instant latestObserved, Instant gapStart,
                           String status, String reason, boolean success) {
        jdbc.update("""
                UPDATE continuous_sim_runs SET status=?,block_reason=?,gap_start_open_time=?,last_poll_at=?,
                    last_successful_poll_at=CASE WHEN ? THEN ? ELSE last_successful_poll_at END,
                    last_observed_open_time=GREATEST(last_observed_open_time,?),
                    consecutive_poll_failures=CASE WHEN ? THEN 0 ELSE consecutive_poll_failures+1 END,
                    updated_at=? WHERE paper_run_id=? AND status <> 'STOPPED'
                """, status, reason, gapStart == null ? null : Timestamp.from(gapStart),
                Timestamp.from(now), success, Timestamp.from(now),
                Timestamp.from(latestObserved), success, Timestamp.from(now), runId);
    }

    public void advanceCursor(String runId, Instant expected, HistoricalBar bar, Instant now) {
        if (jdbc.update("""
                UPDATE continuous_sim_runs SET last_processed_open_time=?,last_processed_close_time=?,
                    last_processed_sha256=?,last_observed_open_time=GREATEST(last_observed_open_time,?),
                    updated_at=?
                WHERE paper_run_id=? AND last_processed_open_time=? AND status <> 'STOPPED'
                """, Timestamp.from(bar.openTime()), Timestamp.from(bar.closeTime()),
                contentSha(bar), Timestamp.from(bar.openTime()), Timestamp.from(now),
                runId, Timestamp.from(expected)) != 1) {
            throw new IllegalStateException("CONTINUOUS_SIM_CURSOR_CONFLICT");
        }
    }

    public void insertBar(String runId, HistoricalBar bar, Instant observedAt) {
        String digest = contentSha(bar);
        var existing = jdbc.query("""
                SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?
                """, (rs, row) -> rs.getString(1), runId, Timestamp.from(bar.openTime()));
        if (!existing.isEmpty()) {
            if (!existing.getFirst().equals(digest)) {
                throw new IllegalStateException("DATA_REVISION_DETECTED");
            }
            return;
        }
        String json = SpotBarIdentity.capture(List.of(bar), mapper).canonicalJson();
        jdbc.update("""
                INSERT INTO continuous_sim_bars(paper_run_id,open_time,close_time,available_at,
                    bar_sha256,bar_json,observed_at) VALUES (?,?,?,?,?,?::jsonb,?)
                """, runId, Timestamp.from(bar.openTime()), Timestamp.from(bar.closeTime()),
                Timestamp.from(bar.availableAt()), digest, json, Timestamp.from(observedAt));
    }

    public List<HistoricalBar> recentBars(String runId) {
        var rows = jdbc.queryForList("""
                SELECT bar_json::text FROM continuous_sim_bars WHERE paper_run_id=?
                ORDER BY open_time DESC LIMIT 500
                """, String.class, runId);
        List<HistoricalBar> result = new ArrayList<>(rows.size());
        for (int index = rows.size() - 1; index >= 0; index--) {
            try {
                JsonNode bar = mapper.readTree(rows.get(index)).get(0);
                result.add(new HistoricalBar(bar.path("exchangeCode").asText(),
                        bar.path("marketType").asText(), bar.path("symbol").asText(),
                        BarInterval.fromWireValue(bar.path("interval").asText()),
                        Instant.parse(bar.path("openTime").asText()),
                        Instant.parse(bar.path("closeTime").asText()),
                        decimal(bar, "openPrice"), decimal(bar, "highPrice"),
                        decimal(bar, "lowPrice"), decimal(bar, "closePrice"),
                        decimal(bar, "volume"), bar.path("quoteVolume").isNull()
                                ? null : decimal(bar, "quoteVolume"),
                        bar.path("tradeCount").isNull() ? null : bar.path("tradeCount").asLong(),
                        bar.path("qualityStatus").asText(), bar.path("rawPayloadJson").asText(),
                        Instant.parse(bar.path("availableAt").asText())));
            } catch (Exception ex) {
                throw new IllegalStateException("CONTINUOUS_SIM_BAR_CORRUPT", ex);
            }
        }
        return List.copyOf(result);
    }

    public static String contentSha(HistoricalBar bar) {
        String value = bar.openTime() + "|" + normalized(bar.openPrice()) + "|"
                + normalized(bar.highPrice()) + "|" + normalized(bar.lowPrice()) + "|"
                + normalized(bar.closePrice()) + "|" + normalized(bar.volume()) + "|"
                + (bar.quoteVolume() == null ? "" : normalized(bar.quoteVolume()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalized(BigDecimal value) {
        return Objects.requireNonNull(value).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText());
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Cursor(String runId, String status, String strategyVersionId,
                         String strategyChecksum, String costSha256, Instant seedLastOpenTime,
                         Instant lastProcessedOpenTime, Instant lastProcessedCloseTime,
                         String lastProcessedSha256, Instant lastObservedOpenTime,
                         Instant gapStartOpenTime,
                         Instant startedAt, Instant lastPollAt, Instant lastSuccessfulPollAt,
                         Instant updatedAt, String blockReason, int consecutivePollFailures) { }
}
