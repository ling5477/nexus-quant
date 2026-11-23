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
import org.springframework.transaction.annotation.Transactional;

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
                "SELECT EXISTS(SELECT 1 FROM (SELECT tr.* FROM continuous_sim_runs tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) continuous_sim_runs WHERE paper_run_id=?)",
                Boolean.class, runId));
    }

    public void create(String runId, String versionId, String checksum, String costSha,
                       List<HistoricalBar> seed, Instant now) {
        create(runId, versionId, checksum, costSha, seed, now, null);
    }

    public void create(String runId, String versionId, String checksum, String costSha,
                       List<HistoricalBar> seed, Instant now, Instant endAt) {
        HistoricalBar last = seed.getLast();
        jdbc.update("""
                INSERT INTO continuous_sim_runs(paper_run_id,status,strategy_version_id,strategy_checksum,
                    cost_sha256,seed_last_open_time,last_processed_open_time,last_processed_close_time,
                    last_processed_sha256,last_observed_open_time,started_at,updated_at,
                    end_at,lifecycle_status,final_valuation_status)
                VALUES (?,'RUNNING',?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, runId, versionId, checksum, costSha,
                Timestamp.from(last.openTime()), Timestamp.from(last.openTime()),
                Timestamp.from(last.closeTime()), contentSha(last), Timestamp.from(last.openTime()),
                Timestamp.from(now), Timestamp.from(now), endAt == null ? null : Timestamp.from(endAt),
                endAt == null ? null : "RUNNING", endAt == null ? null : "PENDING");
        for (HistoricalBar bar : seed) insertBar(runId, bar, now);
    }

    public Cursor get(String runId) {
        return jdbc.query("""
                SELECT paper_run_id,status,strategy_version_id,strategy_checksum,cost_sha256,
                       seed_last_open_time,last_processed_open_time,last_processed_close_time,
                       last_processed_sha256,last_observed_open_time,gap_start_open_time,started_at,last_poll_at,
                       last_successful_poll_at,updated_at,block_reason,consecutive_poll_failures,
                       end_at,manual_end_at,revision_detected_at,lifecycle_status,final_valuation_status,
                       last_valuation_boundary,pending_orders,clock_timestamp()>=end_at AS due,
                       (SELECT p.publish_id FROM paper_trading_runs p WHERE p.paper_run_id=continuous_sim_runs.paper_run_id) AS publish_id
                FROM (SELECT tr.* FROM continuous_sim_runs tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) continuous_sim_runs WHERE paper_run_id=?
                """, (rs, row) -> new Cursor(rs.getString("paper_run_id"), rs.getString("status"),
                rs.getString("strategy_version_id"), rs.getString("strategy_checksum"),
                rs.getString("cost_sha256"), instant(rs, "seed_last_open_time"),
                instant(rs, "last_processed_open_time"), instant(rs, "last_processed_close_time"),
                rs.getString("last_processed_sha256"), instant(rs, "last_observed_open_time"),
                instant(rs, "gap_start_open_time"),
                instant(rs, "started_at"), instant(rs, "last_poll_at"),
                instant(rs, "last_successful_poll_at"), instant(rs, "updated_at"),
                rs.getString("block_reason"), rs.getInt("consecutive_poll_failures"),
                instant(rs, "manual_end_at"), instant(rs, "revision_detected_at"),
                rs.getTimestamp("end_at") == null ? null : new ContinuousSimRunService.Bounded(
                    instant(rs, "end_at"), rs.getBoolean("due") && "RUNNING".equals(rs.getString("lifecycle_status"))
                            ? "ADMISSION_CLOSED" : rs.getString("lifecycle_status"),
                    rs.getString("final_valuation_status"), instant(rs, "last_valuation_boundary"),
                    rs.getObject("pending_orders", Integer.class), rs.getString("block_reason"),
                    instant(rs, "updated_at"), rs.getString("publish_id"), rs.getString("strategy_version_id"),
                    instant(rs, "manual_end_at"), "STOPPED".equals(rs.getString("status")) ? instant(rs, "updated_at") : null)), runId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("CONTINUOUS_SIM_RUN_NOT_FOUND"));
    }

    public boolean requestEnd(String runId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_request_continuous_end(?,?)",
                Boolean.class, runId, Timestamp.from(now)));
    }

    /** 先取得与订单准入互斥的行锁，再按此刻的数据库时钟裁决，不能沿用请求或事务开始时间。 */
    @Transactional(timeout = 10)
    public boolean closeAdmissionIfDue(String runId) {
        String state = jdbc.queryForObject("""
                SELECT lifecycle_status FROM continuous_sim_runs WHERE paper_run_id=? AND end_at IS NOT NULL FOR UPDATE
                """, String.class, runId);
        if (!"RUNNING".equals(state)) return true;
        return jdbc.update("""
                UPDATE continuous_sim_runs SET lifecycle_status='ADMISSION_CLOSED',block_reason='DEADLINE_REACHED',
                    updated_at=clock_timestamp() WHERE paper_run_id=? AND end_at<=clock_timestamp()
                """, runId) == 1;
    }

    public void recordRevision(String runId, Instant now) {
        jdbc.update("""
            UPDATE continuous_sim_runs SET lifecycle_status='BLOCKED',status='STALLED',block_reason='DATA_REVISION_DETECTED',
                revision_detected_at=?,updated_at=? WHERE paper_run_id=? AND end_at IS NOT NULL
                AND revision_detected_at IS NULL AND status<>'STOPPED'
            """, Timestamp.from(now), Timestamp.from(now), runId);
    }

    String closingPriceFailure(String runId, Instant endAt, Instant asOf) {
        return new ClosedBarValuation(jdbc, mapper).read(runId, endAt, endAt, asOf).reason();
    }

    /** 人工意图优先；父运行与容量槽在同一事务内退出，避免父状态过早取消后台收敛资格。 */
    @Transactional(timeout = 10)
    public void recordSettlement(String runId, Instant now, String lifecycle, String valuation,
                                 Integer pending, String reason, boolean terminal) {
        jdbc.queryForObject("SELECT paper_run_id FROM continuous_sim_runs WHERE paper_run_id=? FOR UPDATE", String.class, runId);
        Cursor latest = get(runId);
        if ("STOPPED".equals(latest.status())) return;
        if (latest.manualEndAt() != null && terminal && pending != null && pending == 0) {
            lifecycle = "MANUALLY_STOPPED";
            reason = "MANUAL_END";
        }
        Instant boundary = "VERIFIED".equals(valuation) ? latest.bounded().endAt() : null;
        jdbc.update("""
            UPDATE continuous_sim_runs SET lifecycle_status=?,final_valuation_status=?,last_valuation_boundary=?,
                pending_orders=?,block_reason=?,status=?,updated_at=?,last_poll_at=?
            WHERE paper_run_id=? AND end_at IS NOT NULL
            """, lifecycle, valuation, boundary == null ? null : Timestamp.from(boundary), pending, reason,
                terminal ? "STOPPED" : "STALLED", Timestamp.from(now), Timestamp.from(now), runId);
        if (terminal && jdbc.update("""
            UPDATE paper_trading_runs SET status='STOPPED',stopped_at=?,updated_at=?
            WHERE paper_run_id=? AND status='RUNNING'
            """, Timestamp.from(now), Timestamp.from(now), runId) != 1)
            throw new IllegalStateException("BOUNDED_PARENT_STATE_CONFLICT");
    }

    public List<String> activeIds() {
        return jdbc.queryForList("""
                SELECT paper_run_id FROM (SELECT tr.* FROM continuous_sim_runs tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) continuous_sim_runs WHERE status <> 'STOPPED'
                ORDER BY started_at LIMIT 8
                """, String.class);
    }

    public void requireAdmissionCapacity() {
        // start 外层事务持有同一把 PG 事务锁直到 run 创建完成，避免跨实例并发超过轮询上限。
        jdbc.queryForObject("SELECT nq_require_continuous_capacity()", Object.class);
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
                      AND (end_at IS NULL OR lifecycle_status='RUNNING')
                """, status, reason, gapStart == null ? null : Timestamp.from(gapStart),
                Timestamp.from(now), success, Timestamp.from(now),
                Timestamp.from(latestObserved), success, Timestamp.from(now), runId);
    }

    /** 调用方已在同一事务验证并解除人工暂停；仍保留原运行、账户、游标和容量槽。 */
    public void resumeAfterSuspension(String runId, Instant now) {
        if (jdbc.update("""
                UPDATE continuous_sim_runs SET status='RUNNING',block_reason=NULL,
                    consecutive_poll_failures=0,updated_at=?
                WHERE paper_run_id=? AND status IN ('RUNNING','STALLED')
                """, Timestamp.from(now), runId) != 1) {
            throw new IllegalStateException("CONTINUOUS_SIM_NOT_STOPPED");
        }
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

    /** 经济收敛已经在账户锁内证实，只能重放紧邻游标且有原始决策、原始信号 bar 的确认。 */
    void recoverPenultimateCursor(String runId, long accountId) {
        jdbc.update("""
                UPDATE continuous_sim_runs c SET last_processed_open_time=b.open_time,last_processed_close_time=b.close_time,
                    last_processed_sha256=b.bar_sha256,last_observed_open_time=GREATEST(c.last_observed_open_time,b.open_time),
                    updated_at=clock_timestamp()
                FROM continuous_sim_bars b JOIN strategy_sim_decisions d
                    ON d.paper_run_id=b.paper_run_id AND d.signal_open_time=b.open_time
                WHERE c.paper_run_id=? AND b.paper_run_id=c.paper_run_id AND d.canonical_account_id=?
                    AND c.status<>'STOPPED' AND c.manual_end_at IS NULL AND c.revision_detected_at IS NULL
                    AND c.lifecycle_status<>'RUNNING' AND b.open_time=c.end_at-interval '2 hours'
                    AND b.open_time=c.last_processed_open_time+interval '1 hour'
                    AND d.strategy_version_id=c.strategy_version_id AND d.strategy_checksum=c.strategy_checksum
                    AND d.reason<>'DECIDING' AND (d.input_snapshot_json->'signalBars'->-1)=(b.bar_json->0)
                """, runId, accountId);
    }

    public void insertBar(String runId, HistoricalBar bar, Instant observedAt) {
        String digest = contentSha(bar);
        if (verifyExistingBar(runId, bar.openTime(), digest)) return;
        String json = SpotBarIdentity.capture(List.of(bar), mapper).canonicalJson();
        jdbc.update("""
                INSERT INTO continuous_sim_bars(paper_run_id,open_time,close_time,available_at,
                    bar_sha256,bar_json,observed_at) VALUES (?,?,?,?,?,?::jsonb,?)
                """, runId, Timestamp.from(bar.openTime()), Timestamp.from(bar.closeTime()),
                Timestamp.from(bar.availableAt()), digest, json, Timestamp.from(observedAt));
    }

    boolean verifyExistingBar(String runId, Instant open, String digest) {
        var existing = jdbc.query("""
                SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?
                """, (rs, row) -> rs.getString(1), runId, Timestamp.from(open));
        if (existing.isEmpty()) return false;
        if (!existing.getFirst().equals(digest)) throw new IllegalStateException("DATA_REVISION_DETECTED");
        return true;
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
                         Instant updatedAt, String blockReason, int consecutivePollFailures, Instant manualEndAt, Instant revisionDetectedAt,
                         ContinuousSimRunService.Bounded bounded) {
        public Cursor(String runId, String status, String strategyVersionId, String strategyChecksum, String costSha256,
                      Instant seedLastOpenTime, Instant lastProcessedOpenTime, Instant lastProcessedCloseTime,
                      String lastProcessedSha256, Instant lastObservedOpenTime, Instant gapStartOpenTime,
                      Instant startedAt, Instant lastPollAt, Instant lastSuccessfulPollAt, Instant updatedAt,
                      String blockReason, int consecutivePollFailures) {
            this(runId, status, strategyVersionId, strategyChecksum, costSha256, seedLastOpenTime, lastProcessedOpenTime,
                 lastProcessedCloseTime, lastProcessedSha256, lastObservedOpenTime, gapStartOpenTime, startedAt,
                 lastPollAt, lastSuccessfulPollAt, updatedAt, blockReason, consecutivePollFailures, null, null, null);
        }
    }
}
