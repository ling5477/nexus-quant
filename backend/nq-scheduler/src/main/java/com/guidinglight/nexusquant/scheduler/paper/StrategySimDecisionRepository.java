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

    record Boundary(Instant startedAt, Instant endAt, boolean manuallyClosed, boolean closed) {}

    Boundary boundary(String run) {
        return jdbc.query("""
                SELECT started_at,end_at, manual_end_at IS NOT NULL,
                       manual_end_at IS NOT NULL OR lifecycle_status<>'RUNNING' OR clock_timestamp()>=end_at
                FROM continuous_sim_runs WHERE paper_run_id=? AND end_at IS NOT NULL
                """, (rs, row) -> new Boundary(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
                        rs.getBoolean(3), rs.getBoolean(4)), run).stream().findFirst().orElse(null);
    }

    /** 有界订单通过撮合条件后才取库时钟；开放式和回放订单保留原报价时点。 */
    Instant boundedMatchingTime(String paperRunId) {
        return jdbc.query("""
                SELECT clock_timestamp() FROM continuous_sim_runs WHERE paper_run_id=? AND end_at IS NOT NULL
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), paperRunId).stream().findFirst().orElse(null);
    }

    record ClosedWork(String strategyRunId, String runStatus, String rejection, String orderId, String orderStatus) {}

    ClosedWork closedWork(String runId, long account, DecisionView decision) {
        String request = "sim-" + decision.decisionId().substring(0, 60);
        var saved = jdbc.query("""
                SELECT r.strategy_run_id,r.status,w.normalization_rejection,o.order_id,o.status,
                       (r.strategy_id=? AND r.exchange_code='PAPER' AND r.trade_env='SIM'
                        AND w.account_id=r.account_id AND w.client_order_id='coid-'||r.request_id
                        AND w.symbol='BTC-USDT' AND w.side=? AND w.quantity=?
                        AND (o.order_id IS NULL OR (o.account_id=r.account_id AND o.trade_env='SIM'
                         AND o.venue='PAPER' AND o.client_order_id=w.client_order_id AND o.symbol=w.symbol
                         AND o.side=w.side AND o.qty=coalesce(w.effective_quantity,w.quantity)))) AS bound
                FROM strategy_runs r LEFT JOIN strategy_run_dispatch_work w USING(strategy_run_id)
                LEFT JOIN orders o ON o.strategy_run_id=r.strategy_run_id
                WHERE r.account_id=? AND r.request_id=? LIMIT 2
                """, (rs, row) -> {
                    if (!rs.getBoolean("bound")) throw new IllegalStateException("SIM_EXECUTION_BINDING_UNKNOWN");
                    return new ClosedWork(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5));
                }, "sim-sim-" + runId, decision.side(), decision.quantity(), account, request);
        if (saved.size() > 1) throw new IllegalStateException("SIM_EXECUTION_BINDING_UNKNOWN");
        return saved.isEmpty() ? null : saved.getFirst();
    }

    void rejectUnprepared(String strategyRunId) {
        jdbc.queryForObject("SELECT nq_bind_strategy_effective(?,NULL,NULL,'BOUNDED_RUN_ADMISSION_CLOSED')",
                Boolean.class, strategyRunId);
    }

    List<DecisionView> recoverable(String run, long account) {
        return jdbc.query("""
                SELECT d.* FROM strategy_sim_decisions d
                LEFT JOIN orders o ON o.order_id=d.order_id AND o.account_id=d.canonical_account_id
                LEFT JOIN strategy_runs r ON r.strategy_run_id=d.strategy_run_id AND r.account_id=o.account_id
                WHERE d.paper_run_id=? AND d.canonical_account_id=? AND (d.reason='DECIDING'
                  OR (o.status IN ('FILLED','RISK_REJECTED','REJECTED','CANCELLED')
                    AND r.status IN ('CREATED','DISPATCHING','RUNNING')))
                ORDER BY d.signal_open_time LIMIT 32
                """, (rs, row) -> map(rs), run, account);
    }

    /** 整套事实用同一快照核验。成交引用、资金分录集合或未决工作不闭合时保留所有原始记录。 */
    StrategySimRunService.Settlement settlement(String run, long account) {
        return jdbc.queryForObject("""
                WITH scope AS (SELECT ?::text AS run, ?::bigint AS account),
                 d AS MATERIALIZED (SELECT d.* FROM strategy_sim_decisions d,scope s
                       WHERE d.paper_run_id=s.run AND d.canonical_account_id=s.account LIMIT 10001),
                 o AS MATERIALIZED (SELECT o.* FROM orders o,scope s WHERE o.account_id=s.account LIMIT 10001),
                 r AS MATERIALIZED (SELECT r.* FROM strategy_runs r,scope s WHERE r.account_id=s.account LIMIT 10001),
                 t AS MATERIALIZED (SELECT t.* FROM trades t,scope s WHERE t.account_id=s.account LIMIT 10001),
                expected AS (
                    SELECT t.trade_id ref_id,t.trade_id||':LEDGER:'||leg.suffix AS key,leg.currency,leg.delta,
                           CASE WHEN leg.delta>=0 THEN 'CREDIT' ELSE 'DEBIT' END AS direction,t.ts
                    FROM t JOIN o USING(order_id)
                    CROSS JOIN LATERAL (SELECT round(t.price*t.qty,8)*CASE o.side WHEN 'BUY' THEN -1 ELSE 1 END AS cash) v
                    CROSS JOIN LATERAL (VALUES ('1','USDT',v.cash),('2','USDT',-v.cash),
                       ('FEE_1',t.fee_currency,-t.fee),('FEE_2',t.fee_currency,t.fee)) leg(suffix,currency,delta)
                    WHERE leg.suffix IN ('1','2') OR t.fee<>0
                ), actual AS (
                    SELECT e.ref_id,e.idempotency_key AS key,e.currency,e.delta,e.direction,e.ts
                     FROM ledger_entries e,scope s WHERE e.account_id=s.account AND e.ref_type='TRADE' LIMIT 40001
                ), pending AS (
                    SELECT count(*)::int AS n FROM o WHERE status NOT IN ('FILLED','CANCELLED','REJECTED','RISK_REJECTED')
                )
                SELECT pending.n,
                   CASE WHEN (SELECT count(*) FROM o)>10000 OR (SELECT count(*) FROM d)>10000
                        OR (SELECT count(*) FROM r)>10000 OR (SELECT count(*) FROM t)>10000
                        OR (SELECT count(*) FROM actual)>40000 THEN 'SIM_SETTLEMENT_FACTS_LIMITED'
                   WHEN EXISTS (SELECT 1 FROM o,scope s WHERE NOT EXISTS (
                      SELECT FROM d JOIN r ON r.strategy_run_id=d.strategy_run_id
                      JOIN strategy_run_dispatch_work w ON w.strategy_run_id=r.strategy_run_id
                      WHERE d.order_id=o.order_id AND d.strategy_run_id=o.strategy_run_id
                        AND r.strategy_id='sim-sim-'||s.run AND r.request_id='sim-'||left(d.decision_id,60)
                        AND r.trade_env='SIM' AND r.exchange_code='PAPER'
                        AND o.trade_env='SIM' AND o.venue='PAPER' AND o.symbol='BTC-USDT'
                        AND w.account_id=s.account AND w.client_order_id=o.client_order_id
                        AND w.client_order_id='coid-'||r.request_id AND w.symbol=o.symbol
                        AND w.side=o.side AND o.side=d.side AND o.qty=d.quantity))
                    THEN 'SIM_ORDER_BINDING_UNKNOWN'
                  WHEN EXISTS (SELECT FROM d WHERE status IN ('ACCEPTED','RISK_REJECTED') AND NOT EXISTS
                      (SELECT FROM o WHERE o.order_id=d.order_id AND o.strategy_run_id=d.strategy_run_id))
                    THEN 'SIM_DECISION_ORDER_UNKNOWN'
                  WHEN EXISTS (SELECT FROM t LEFT JOIN o USING(order_id) LEFT JOIN d ON d.order_id=t.order_id
                      WHERE o.order_id IS NULL OR d.order_id IS NULL OR t.trade_env<>'SIM'
                      OR t.exchange_code<>'PAPER' OR t.symbol IS DISTINCT FROM o.symbol
                      OR t.strategy_run_id IS DISTINCT FROM o.strategy_run_id
                      OR t.price IS DISTINCT FROM d.execution_price OR d.execution_open_time IS NULL OR NOT isfinite(t.ts)
                      OR t.fee_currency IS DISTINCT FROM 'USDT' OR d.fee_rate IS NULL
                      OR t.fee IS DISTINCT FROM round(t.qty*t.price*d.fee_rate,8))
                    THEN 'SIM_TRADE_BINDING_UNKNOWN'
                  WHEN EXISTS ((SELECT * FROM expected EXCEPT ALL SELECT * FROM actual)
                        UNION ALL (SELECT * FROM actual EXCEPT ALL SELECT * FROM expected))
                    THEN 'SIM_TRADE_LEDGER_MISMATCH'
                  WHEN EXISTS (SELECT FROM o CROSS JOIN LATERAL nq_strategy_execution_proof(o.order_id) p
                      WHERE (o.status='FILLED' AND (p.valid IS NOT TRUE OR p.executed_quantity<>o.qty))
                        OR (o.status IN ('CANCELLED','RISK_REJECTED','REJECTED') AND p.fill_count<>0))
                    THEN 'SIM_ORDER_TRADE_MISMATCH'
                  WHEN EXISTS (SELECT FROM o WHERE status IN ('FAILED','SENT','SUBMITTING','NEW','RISK_PASSED','VALIDATED'))
                    THEN 'SIM_ORDER_OUTCOME_UNKNOWN'
                  WHEN pending.n>0 THEN 'SIM_PREVIOUS_EXECUTION_PENDING'
                  WHEN EXISTS (SELECT FROM d WHERE reason='DECIDING') THEN 'SIM_DECISION_RECOVERY_PENDING'
                  WHEN EXISTS (SELECT FROM r LEFT JOIN o ON o.strategy_run_id=r.strategy_run_id
                      LEFT JOIN strategy_run_dispatch_work w ON w.strategy_run_id=r.strategy_run_id
                      WHERE CASE WHEN o.order_id IS NULL THEN (r.status='FAILED' AND w.normalization_rejection IS NOT NULL
                                 AND EXISTS (SELECT FROM d WHERE d.reason='BOUNDED_RUN_ADMISSION_CLOSED'
                                 AND r.strategy_id='sim-sim-'||d.paper_run_id AND r.request_id='sim-'||left(d.decision_id,60)
                                 AND w.account_id=r.account_id AND w.client_order_id='coid-'||r.request_id
                                 AND w.symbol='BTC-USDT' AND w.side=d.side AND w.quantity=d.quantity))
                                 WHEN o.status='FILLED' THEN r.status='SUCCEEDED'
                                 ELSE r.status='FAILED' END IS NOT TRUE)
                    THEN 'SIM_CANONICAL_RUN_NOT_TERMINAL'
                  ELSE NULL END AS reason FROM pending
                """, (rs, row) -> new StrategySimRunService.Settlement(rs.getInt(1), rs.getString(2)), run, account);
    }

    /** 仅核验现有 BTC/USDT 有界合同。交易核验已确认每笔成交、方向及 USDT 手续费。 */
    String projectionFailure(String run, long account, BigDecimal cash) {
        return jdbc.queryForObject("""
                WITH scope AS (
                    SELECT p.paper_run_id,p.canonical_account_id,
                           (p.config_snapshot_json->>'budget')::numeric AS budget
                    FROM paper_trading_runs p WHERE p.paper_run_id=? AND p.canonical_account_id=?
                ), funding AS (
                    SELECT e.* FROM ledger_entries e,scope s
                    WHERE e.account_id=s.canonical_account_id AND e.ref_type<>'TRADE' LIMIT 3
                ), btc AS (
                    SELECT count(*) AS fills, coalesce(sum(CASE o.side WHEN 'BUY' THEN t.qty ELSE -t.qty END),0) AS qty
                    FROM trades t JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id
                    WHERE t.account_id=?
                ), quote AS (
                    SELECT balance,available,frozen,balance_basis,balance_scope FROM account_snapshots
                    WHERE account_id=? AND currency='USDT' AND trade_env='SIM' ORDER BY snapshot_id DESC LIMIT 1
                ), base AS (
                    SELECT balance,available,frozen,balance_basis,balance_scope FROM account_snapshots
                    WHERE account_id=? AND currency='BTC' AND trade_env='SIM' ORDER BY snapshot_id DESC LIMIT 1
                ), checks AS (
                    SELECT
                        EXISTS (SELECT 1 FROM scope s WHERE s.budget>0
                            AND (SELECT count(*) FROM funding)=2
                            AND (SELECT count(*) FROM funding f WHERE f.ref_id=s.paper_run_id AND f.currency='USDT'
                                 AND ((f.ref_type='SIM_FUNDING_CASH' AND f.delta=s.budget AND f.direction='CREDIT'
                                       AND f.idempotency_key=s.paper_run_id||':SIM_FUND:CASH')
                                  OR (f.ref_type='SIM_FUNDING_CONTRA' AND f.delta=-s.budget AND f.direction='DEBIT'
                                       AND f.idempotency_key=s.paper_run_id||':SIM_FUND:CONTRA')))=2
                        ) AND EXISTS (SELECT 1 FROM quote q WHERE q.balance=?::numeric AND q.available=q.balance
                              AND q.frozen=0 AND q.balance_scope='NQ_MANAGED_ACCOUNT'
                              AND q.balance_basis='LEDGER_CASH_PROJECTION') AS cash_matches,
                        NOT EXISTS (SELECT 1 FROM positions p WHERE p.account_id=?
                            AND (p.symbol<>'BTC-USDT' OR p.qty<>b.qty OR p.available_qty<>b.qty OR p.frozen_qty<>0))
                        AND b.qty>=0
                        AND CASE WHEN b.fills=0 THEN NOT EXISTS (SELECT 1 FROM base)
                            ELSE EXISTS (SELECT 1 FROM positions p WHERE p.account_id=? AND p.symbol='BTC-USDT')
                             AND EXISTS (SELECT 1 FROM base s WHERE s.balance=b.qty AND s.available=b.qty AND s.frozen=0
                                  AND s.balance_basis='POSITION_PROJECTION' AND s.balance_scope='NQ_MANAGED_ACCOUNT')
                            END AS position_matches
                    FROM btc b
                )
                SELECT CASE WHEN NOT cash_matches AND NOT position_matches THEN 'SIM_ACCOUNT_PROJECTIONS_MISMATCH'
                            WHEN NOT cash_matches THEN 'SIM_CASH_PROJECTION_MISMATCH'
                            WHEN NOT position_matches THEN 'SIM_POSITION_PROJECTION_MISMATCH'
                       END
                FROM checks
                """, String.class, run, account, account, account, account, cash, account, account);
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
                FROM (SELECT tr.* FROM strategy_sim_decisions tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time=?
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
                FROM (SELECT tr.* FROM strategy_sim_decisions tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) strategy_sim_decisions WHERE order_id=?
                """,
                        (rs, n) -> map(rs),
                        orderId);
        return rows.stream().findFirst();
    }

    /** 使用现有 run/window 索引只读取相邻决策，状态恢复不受列表500行上限影响。 */
    public Optional<DecisionView> findPreviousWindow(String paperRunId, Instant before) {
        return jdbc.query("""
                SELECT tr.* FROM strategy_sim_decisions tr
                WHERE paper_run_id=? AND signal_open_time<?
                  AND nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()
                ORDER BY signal_open_time DESC LIMIT 1
                """, (rs, n) -> map(rs), paperRunId, Timestamp.from(before)).stream().findFirst();
    }

    public List<DecisionView> listByRun(String paperRunId) {
        return jdbc.query(
                """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id,fee_model_json::text,fee_charge_json::text
                FROM (SELECT tr.* FROM strategy_sim_decisions tr WHERE nq_entity_tenant('paper',tr.paper_run_id)=nq_current_tenant_id()) strategy_sim_decisions WHERE paper_run_id=? ORDER BY signal_open_time DESC LIMIT 500
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
