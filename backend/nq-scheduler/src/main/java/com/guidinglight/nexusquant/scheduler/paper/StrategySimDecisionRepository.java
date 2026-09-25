package com.guidinglight.nexusquant.scheduler.paper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 只保存策略决策、输入和 canonical 事实引用；Order/Trade/Ledger 仍由原 owner 写入。 */
@Repository
public class StrategySimDecisionRepository {
    private final JdbcTemplate jdbc;

    public StrategySimDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockAccount(long accountId) {
        jdbc.queryForObject("SELECT account_id FROM accounts WHERE account_id=? FOR NO KEY UPDATE",
                Long.class, accountId);
    }

    public <T> T withAccountMutex(long accountId, Supplier<T> action) {
        // 独立连接只持有事务级 advisory 锁，不持有 FK 行锁；canonical 准入会开独立事务。
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 9251))")) {
                lock.setQueryTimeout(10);
                lock.setString(1, "strategy-sim-account:" + accountId);
                lock.execute();
            }
            try {
                T result = action.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("SIM_ACCOUNT_MUTEX_UNAVAILABLE", failure);
        }
    }

    public Optional<DecisionView> findByWindow(String paperRunId, Instant signalOpenTime) {
        List<DecisionView> rows = jdbc.query("""
                SELECT decision_id,paper_run_id,strategy_version_id,input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time=?
                """, (rs, n) -> map(rs), paperRunId, Timestamp.from(signalOpenTime));
        return rows.stream().findFirst();
    }

    public Optional<DecisionView> findByOrderId(String orderId) {
        List<DecisionView> rows = jdbc.query("""
                SELECT decision_id,paper_run_id,strategy_version_id,input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE order_id=?
                """, (rs, n) -> map(rs), orderId);
        return rows.stream().findFirst();
    }

    public List<DecisionView> listByRun(String paperRunId) {
        return jdbc.query("""
                SELECT decision_id,paper_run_id,strategy_version_id,input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE paper_run_id=? ORDER BY signal_open_time DESC LIMIT 500
                """, (rs, n) -> map(rs), paperRunId);
    }

    public void insert(DecisionWrite value, String status, String reason) {
        jdbc.update("""
                INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,
                    strategy_version_id,strategy_checksum,signal_open_time,signal_available_at,
                    execution_open_time,input_sha256,execution_bar_sha256,input_snapshot_json,
                    target_exposure,status,reason,side,quantity,execution_price,fee_rate,slippage_bps)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?)
                """, value.decisionId(), value.paperRunId(), value.accountId(), value.strategyVersionId(),
                value.strategyChecksum(), Timestamp.from(value.signalOpenTime()),
                Timestamp.from(value.signalAvailableAt()), value.executionOpenTime() == null
                        ? null : Timestamp.from(value.executionOpenTime()),
                value.inputSha256(), value.executionBarSha256(), value.inputSnapshotJson(),
                value.targetExposure(), status, reason, value.side(), value.quantity(),
                value.executionPrice(), value.feeRate(), value.slippageBps());
    }

    public void complete(String decisionId, String status, String reason, String strategyRunId, String orderId) {
        if (jdbc.update("""
                UPDATE strategy_sim_decisions SET status=?,reason=?,strategy_run_id=?,order_id=?
                WHERE decision_id=? AND status='NOT_TRADABLE' AND reason='DECIDING'
                """, status, reason, strategyRunId, orderId, decisionId) != 1) {
            throw new IllegalStateException("SIM_DECISION_COMPLETION_CONFLICT");
        }
    }

    private static DecisionView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DecisionView(rs.getString("decision_id"), rs.getString("paper_run_id"),
                rs.getString("strategy_version_id"), rs.getString("input_sha256"),
                rs.getString("execution_bar_sha256"), rs.getTimestamp("signal_open_time").toInstant(),
                rs.getTimestamp("signal_available_at").toInstant(),
                rs.getTimestamp("execution_open_time") == null ? null
                        : rs.getTimestamp("execution_open_time").toInstant(), rs.getString("status"),
                rs.getString("reason"), rs.getString("side"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("execution_price"), rs.getBigDecimal("fee_rate"),
                rs.getBigDecimal("slippage_bps"), rs.getString("strategy_run_id"),
                rs.getString("order_id"));
    }

    public record DecisionWrite(String decisionId, String paperRunId, long accountId,
                                String strategyVersionId, String strategyChecksum,
                                Instant signalOpenTime, Instant signalAvailableAt, Instant executionOpenTime,
                                String inputSha256, String executionBarSha256, String inputSnapshotJson,
                                BigDecimal targetExposure, String reason, String side, BigDecimal quantity,
                                BigDecimal executionPrice, BigDecimal feeRate, BigDecimal slippageBps) { }

    public record DecisionView(String decisionId, String paperRunId, String strategyVersionId,
                               String inputSha256, String executionBarSha256, Instant signalOpenTime,
                               Instant signalAvailableAt, Instant executionOpenTime, String status,
                               String reason, String side, BigDecimal quantity, BigDecimal executionPrice,
                               BigDecimal feeRate, BigDecimal slippageBps, String strategyRunId,
                               String orderId) { }
}
