package com.guidinglight.nexusquant.scheduler.infra.jdbc;

import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;
import com.guidinglight.nexusquant.scheduler.service.port.LedgerReconcileRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcLedgerReconcileRepository 提供最小对账查询。
 */
@Repository
public class JdbcLedgerReconcileRepository implements LedgerReconcileRepository {

    private static final RowMapper<LedgerReconcileDiff> DIFF_ROW_MAPPER = JdbcLedgerReconcileRepository::mapDiff;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLedgerReconcileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LedgerReconcileDiff> findDiffs() {
        return jdbcTemplate.query(
                """
                        WITH latest_typed AS (
                            SELECT DISTINCT ON (account_id, currency, trade_env)
                                   account_id, currency, trade_env, balance_basis, balance
                            FROM account_snapshots
                            WHERE trade_env IS NOT NULL
                            ORDER BY account_id, currency, trade_env, snapshot_id DESC
                        ), checked AS (
                            SELECT s.account_id, s.currency, s.trade_env, s.balance_basis,
                                   s.balance AS snapshot_balance,
                                   CASE WHEN s.balance_basis='POSITION_PROJECTION' THEN
                                       (SELECT COALESCE(SUM(p.qty), 0) FROM positions p
                                        WHERE p.account_id=s.account_id
                                          AND split_part(replace(p.symbol, '/', '-'), '-', 1)=s.currency)
                                   ELSE
                                       (SELECT COALESCE(SUM(CASE
                                           WHEN e.ref_type='SIM_FUNDING_CASH' AND s.trade_env='SIM' THEN e.delta
                                           WHEN e.ref_type='TRADE' AND EXISTS (
                                               SELECT 1 FROM trades t
                                               WHERE t.trade_id=e.ref_id AND t.trade_env=s.trade_env)
                                           THEN CASE WHEN s.trade_env='SIM' THEN
                                               CASE WHEN right(e.idempotency_key, 9)=':LEDGER:1'
                                                         OR right(e.idempotency_key, 13)=':LEDGER:FEE_1'
                                                    THEN e.delta ELSE 0 END
                                               ELSE e.delta END
                                           ELSE 0 END), 0)
                                        FROM ledger_entries e
                                        WHERE e.account_id=s.account_id AND e.currency=s.currency)
                                   END AS ledger_balance
                            FROM latest_typed s
                        )
                        SELECT c.account_id, c.currency, c.ledger_balance, c.snapshot_balance,
                               c.ledger_balance-c.snapshot_balance AS diff_amount,
                               'BALANCE_MISMATCH_' || c.trade_env || '_' || c.balance_basis AS reason
                        FROM checked c WHERE c.ledger_balance<>c.snapshot_balance
                        UNION ALL
                        SELECT s.account_id, s.currency, NULL::numeric, NULL::numeric, NULL::numeric,
                               'SNAPSHOT_PROVENANCE_UNKNOWN' AS reason
                        FROM account_snapshots s
                        WHERE s.trade_env IS NULL
                          AND s.snapshot_id=(SELECT MAX(x.snapshot_id) FROM account_snapshots x
                                             WHERE x.account_id=s.account_id AND x.currency=s.currency
                                               AND x.trade_env IS NULL)
                        UNION ALL
                        SELECT le.account_id, le.currency, SUM(le.delta), 0::numeric, SUM(le.delta),
                               'SNAPSHOT_MISSING' AS reason
                        FROM ledger_entries le
                        WHERE NOT EXISTS (SELECT 1 FROM account_snapshots s
                                          WHERE s.account_id=le.account_id AND s.currency=le.currency)
                        GROUP BY le.account_id, le.currency
                        ORDER BY account_id, currency
                        """,
                DIFF_ROW_MAPPER
        );
    }

    private static LedgerReconcileDiff mapDiff(ResultSet resultSet, int rowNum) throws SQLException {
        return new LedgerReconcileDiff(
                resultSet.getLong("account_id"),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("ledger_balance"),
                resultSet.getBigDecimal("snapshot_balance"),
                resultSet.getBigDecimal("diff_amount"),
                resultSet.getString("reason")
        );
    }
}
