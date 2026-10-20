package com.guidinglight.nexusquant.scheduler.paper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** 只保存策略决策、输入和 canonical 事实引用；Order/Trade/Ledger 仍由原 owner 写入。 */
@Repository
public class StrategySimDecisionRepository {
    private final JdbcTemplate jdbc;

    public StrategySimDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockAccount(long accountId) {
        jdbc.queryForObject(
                "SELECT account_id FROM accounts WHERE account_id=? FOR NO KEY UPDATE",
                Long.class,
                accountId);
    }

    public <T> T withAccountMutex(long accountId, Supplier<T> action) {
        // 独立连接只持有事务级 advisory 锁，不持有 FK 行锁；canonical 准入会开独立事务。
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock =
                    connection.prepareStatement(
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
        List<DecisionView> rows =
                jdbc.query(
                        """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id,fee_model_json::text,fee_charge_json::text
                FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time=?
                """,
                        (rs, n) -> map(rs),
                        paperRunId,
                        Timestamp.from(signalOpenTime));
        return rows.stream().findFirst();
    }

    public Optional<DecisionView> findByOrderId(String orderId) {
        List<DecisionView> rows =
                jdbc.query(
                        """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id,fee_model_json::text,fee_charge_json::text
                FROM strategy_sim_decisions WHERE order_id=?
                """,
                        (rs, n) -> map(rs),
                        orderId);
        return rows.stream().findFirst();
    }

    public List<DecisionView> listByRun(String paperRunId) {
        return jdbc.query(
                """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id,fee_model_json::text,fee_charge_json::text
                FROM strategy_sim_decisions WHERE paper_run_id=? ORDER BY signal_open_time DESC LIMIT 500
                """,
                (rs, n) -> map(rs),
                paperRunId);
    }

    /** 完整在途集合最多500项；绑定失败窗口沿原canonical工作身份读取费用，不能遗漏已存在订单的预留。 */
    public List<PendingOrder> pendingOrders(long accountId) {
        return jdbc.query(
                """
                SELECT o.side,o.qty,o.symbol,d.execution_price,d.fee_rate,d.fee_model_json::text,d.execution_open_time,d.fee_charge_json::text,
                    count(d.decision_id) OVER(PARTITION BY o.order_id) fee_match_count
                FROM orders o
                LEFT JOIN strategy_runs r ON r.strategy_run_id=o.strategy_run_id AND r.account_id=o.account_id
                    AND r.exchange_code='PAPER' AND r.trade_env='SIM'
                LEFT JOIN strategy_run_dispatch_work w ON w.strategy_run_id=r.strategy_run_id AND w.account_id=o.account_id
                    AND w.client_order_id=o.client_order_id AND w.symbol=o.symbol AND w.side=o.side
                    AND coalesce(w.effective_quantity,w.quantity)=o.qty
                LEFT JOIN strategy_sim_decisions d ON d.canonical_account_id=o.account_id AND (
                    d.order_id=o.order_id OR (d.order_id IS NULL AND d.status='NOT_TRADABLE' AND d.reason='DECIDING'
                        AND r.strategy_id='sim-sim-'||d.paper_run_id AND r.request_id='sim-'||left(d.decision_id,60)
                        AND o.client_order_id='coid-'||r.request_id AND w.strategy_run_id IS NOT NULL
                        AND d.side=o.side AND d.quantity=o.qty AND EXISTS (
                            SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=d.paper_run_id
                                AND p.canonical_account_id=o.account_id AND p.trade_env='SIM' AND p.symbol=o.symbol))
                )
                WHERE o.account_id=? AND o.status NOT IN ('FILLED','REJECTED','RISK_REJECTED','CANCELLED','FAILED')
                ORDER BY o.order_id LIMIT 501
                """,
                (rs, n) -> {
                    // 即使稳定请求前缀发生歧义也不能任选一个费用合同；保留未知预留而不是重复或少算。
                    if (rs.getLong("fee_match_count") > 1)
                        throw new IllegalStateException("SIM_PENDING_ORDER_RESERVE_UNKNOWN");
                    return new PendingOrder(
                            rs.getString("side"),
                            rs.getBigDecimal("qty"),
                            rs.getBigDecimal("execution_price"),
                            rs.getBigDecimal("fee_rate"),
                            rs.getString("fee_model_json"),
                            rs.getString("symbol"),
                            rs.getTimestamp("execution_open_time") == null
                                    ? null
                                    : rs.getTimestamp("execution_open_time").toInstant(),
                            rs.getString("fee_charge_json"));
                },
                accountId);
    }

    public void insert(DecisionWrite value, String status, String reason) {
        jdbc.update(
                """
                INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,
                    strategy_version_id,strategy_checksum,signal_open_time,signal_available_at,
                    execution_open_time,input_sha256,execution_bar_sha256,input_snapshot_json,
                    target_exposure,status,reason,side,quantity,execution_price,fee_rate,slippage_bps,fee_model_json,fee_charge_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                """,
                value.decisionId(),
                value.paperRunId(),
                value.accountId(),
                value.strategyVersionId(),
                value.strategyChecksum(),
                Timestamp.from(value.signalOpenTime()),
                Timestamp.from(value.signalAvailableAt()),
                value.executionOpenTime() == null
                        ? null
                        : Timestamp.from(value.executionOpenTime()),
                value.inputSha256(),
                value.executionBarSha256(),
                value.inputSnapshotJson(),
                value.targetExposure(),
                status,
                reason,
                value.side(),
                value.quantity(),
                value.executionPrice(),
                value.feeRate(),
                value.slippageBps(),
                value.feeModelJson(),
                value.feeChargeJson());
    }

    public void complete(
            String decisionId, String status, String reason, String strategyRunId, String orderId) {
        if (jdbc.update(
                        """
                UPDATE strategy_sim_decisions SET status=?,reason=?,strategy_run_id=?,order_id=?
                WHERE decision_id=? AND status='NOT_TRADABLE' AND reason='DECIDING'
                """,
                        status,
                        reason,
                        strategyRunId,
                        orderId,
                        decisionId)
                != 1) {
            throw new IllegalStateException("SIM_DECISION_COMPLETION_CONFLICT");
        }
    }

    private static DecisionView map(ResultSet rs) throws SQLException {
        return new DecisionView(
                rs.getString("decision_id"),
                rs.getString("paper_run_id"),
                rs.getString("strategy_version_id"),
                rs.getString("strategy_checksum"),
                rs.getBigDecimal("target_exposure"),
                rs.getString("input_sha256"),
                rs.getString("execution_bar_sha256"),
                rs.getTimestamp("signal_open_time").toInstant(),
                rs.getTimestamp("signal_available_at").toInstant(),
                rs.getTimestamp("execution_open_time") == null
                        ? null
                        : rs.getTimestamp("execution_open_time").toInstant(),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getString("side"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("execution_price"),
                rs.getBigDecimal("fee_rate"),
                rs.getBigDecimal("slippage_bps"),
                rs.getString("strategy_run_id"),
                rs.getString("order_id"),
                rs.getString("fee_model_json"),
                rs.getString("fee_charge_json"));
    }

    public record DecisionWrite(
            String decisionId,
            String paperRunId,
            long accountId,
            String strategyVersionId,
            String strategyChecksum,
            Instant signalOpenTime,
            Instant signalAvailableAt,
            Instant executionOpenTime,
            String inputSha256,
            String executionBarSha256,
            String inputSnapshotJson,
            BigDecimal targetExposure,
            String reason,
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal feeRate,
            BigDecimal slippageBps,
            String feeModelJson,
            String feeChargeJson) {
        /** 已有收到资产合同不需要第三资产资金选择；保持原调用构造和冻结字节。 */
        public DecisionWrite(
                String decisionId,
                String paperRunId,
                long accountId,
                String strategyVersionId,
                String strategyChecksum,
                Instant signalOpenTime,
                Instant signalAvailableAt,
                Instant executionOpenTime,
                String inputSha256,
                String executionBarSha256,
                String inputSnapshotJson,
                BigDecimal targetExposure,
                String reason,
                String side,
                BigDecimal quantity,
                BigDecimal executionPrice,
                BigDecimal feeRate,
                BigDecimal slippageBps,
                String feeModelJson) {
            this(
                    decisionId,
                    paperRunId,
                    accountId,
                    strategyVersionId,
                    strategyChecksum,
                    signalOpenTime,
                    signalAvailableAt,
                    executionOpenTime,
                    inputSha256,
                    executionBarSha256,
                    inputSnapshotJson,
                    targetExposure,
                    reason,
                    side,
                    quantity,
                    executionPrice,
                    feeRate,
                    slippageBps,
                    feeModelJson,
                    null);
        }

        /** 旧标量调用继续保留原feeRate；方向调用显式传完整合同并将feeRate留空。 */
        public DecisionWrite(
                String decisionId,
                String paperRunId,
                long accountId,
                String strategyVersionId,
                String strategyChecksum,
                Instant signalOpenTime,
                Instant signalAvailableAt,
                Instant executionOpenTime,
                String inputSha256,
                String executionBarSha256,
                String inputSnapshotJson,
                BigDecimal targetExposure,
                String reason,
                String side,
                BigDecimal quantity,
                BigDecimal executionPrice,
                BigDecimal feeRate,
                BigDecimal slippageBps) {
            this(
                    decisionId,
                    paperRunId,
                    accountId,
                    strategyVersionId,
                    strategyChecksum,
                    signalOpenTime,
                    signalAvailableAt,
                    executionOpenTime,
                    inputSha256,
                    executionBarSha256,
                    inputSnapshotJson,
                    targetExposure,
                    reason,
                    side,
                    quantity,
                    executionPrice,
                    feeRate,
                    slippageBps,
                    null,
                    null);
        }
    }

    /** 同一账户未终态订单的预算输入；实际成交/持仓依然由canonical事实提供。 */
    public record PendingOrder(
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal feeRate,
            String feeModelJson,
            String symbol,
            Instant executionAt,
            String feeChargeJson) {
        public PendingOrder(
                String side,
                BigDecimal quantity,
                BigDecimal executionPrice,
                BigDecimal feeRate,
                String feeModelJson,
                String symbol,
                Instant executionAt) {
            this(side, quantity, executionPrice, feeRate, feeModelJson, symbol, executionAt, null);
        }
    }

    public record DecisionView(
            String decisionId,
            String paperRunId,
            String strategyVersionId,
            String strategyChecksum,
            BigDecimal targetExposure,
            String inputSha256,
            String executionBarSha256,
            Instant signalOpenTime,
            Instant signalAvailableAt,
            Instant executionOpenTime,
            String status,
            String reason,
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal feeRate,
            BigDecimal slippageBps,
            String strategyRunId,
            String orderId,
            String feeModelJson,
            String feeChargeJson) {
        /** 旧方向调用沿用原收到资产合同，优惠分支必须显式提供不可变的资金与支付选择。 */
        public DecisionView(
                String decisionId,
                String paperRunId,
                String strategyVersionId,
                String strategyChecksum,
                BigDecimal targetExposure,
                String inputSha256,
                String executionBarSha256,
                Instant signalOpenTime,
                Instant signalAvailableAt,
                Instant executionOpenTime,
                String status,
                String reason,
                String side,
                BigDecimal quantity,
                BigDecimal executionPrice,
                BigDecimal feeRate,
                BigDecimal slippageBps,
                String strategyRunId,
                String orderId,
                String feeModelJson) {
            this(
                    decisionId,
                    paperRunId,
                    strategyVersionId,
                    strategyChecksum,
                    targetExposure,
                    inputSha256,
                    executionBarSha256,
                    signalOpenTime,
                    signalAvailableAt,
                    executionOpenTime,
                    status,
                    reason,
                    side,
                    quantity,
                    executionPrice,
                    feeRate,
                    slippageBps,
                    strategyRunId,
                    orderId,
                    feeModelJson,
                    null);
        }

        /** 保留测试与旧调用方构造方式；缺少方向合同只能消费旧报价币标量，不补猜来源。 */
        public DecisionView(
                String decisionId,
                String paperRunId,
                String strategyVersionId,
                String strategyChecksum,
                BigDecimal targetExposure,
                String inputSha256,
                String executionBarSha256,
                Instant signalOpenTime,
                Instant signalAvailableAt,
                Instant executionOpenTime,
                String status,
                String reason,
                String side,
                BigDecimal quantity,
                BigDecimal executionPrice,
                BigDecimal feeRate,
                BigDecimal slippageBps,
                String strategyRunId,
                String orderId) {
            this(
                    decisionId,
                    paperRunId,
                    strategyVersionId,
                    strategyChecksum,
                    targetExposure,
                    inputSha256,
                    executionBarSha256,
                    signalOpenTime,
                    signalAvailableAt,
                    executionOpenTime,
                    status,
                    reason,
                    side,
                    quantity,
                    executionPrice,
                    feeRate,
                    slippageBps,
                    strategyRunId,
                    orderId,
                    null,
                    null);
        }
    }
}
