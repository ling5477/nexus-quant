package com.guidinglight.nexusquant.scheduler.repository;

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
 * <p>
 * Why:
 * Gate B 阶段不引入复杂对账引擎，先以 SQL 聚合比对账本与快照，
 * 及时暴露余额偏差，避免错误积累到后续 Gate。
 */
@Repository
public class JdbcLedgerReconcileRepository implements LedgerReconcileRepository {

    private static final RowMapper<LedgerReconcileDiff> DIFF_ROW_MAPPER = JdbcLedgerReconcileRepository::mapDiff;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate JDBC 执行器
     */
    public JdbcLedgerReconcileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LedgerReconcileDiff> findDiffs() {
        return jdbcTemplate.query(
                """
                        SELECT
                            lb.account_id AS account_id,
                            lb.currency AS currency,
                            lb.ledger_balance AS ledger_balance,
                            lb.snapshot_balance AS snapshot_balance,
                            lb.ledger_balance - lb.snapshot_balance AS diff_amount,
                            CASE
                                WHEN lb.snapshot_exists = 0 THEN 'SNAPSHOT_MISSING'
                                ELSE 'BALANCE_MISMATCH'
                            END AS reason
                        FROM (
                            SELECT
                                le.account_id,
                                le.currency,
                                COALESCE(SUM(le.delta), 0) AS ledger_balance,
                                COALESCE((
                                    SELECT s.balance
                                    FROM account_snapshots s
                                    WHERE s.account_id = le.account_id
                                      AND s.currency = le.currency
                                    ORDER BY s.ts DESC, s.snapshot_id DESC
                                    LIMIT 1
                                ), 0) AS snapshot_balance,
                                CASE
                                    WHEN EXISTS (
                                        SELECT 1
                                        FROM account_snapshots sx
                                        WHERE sx.account_id = le.account_id
                                          AND sx.currency = le.currency
                                    ) THEN 1
                                    ELSE 0
                                END AS snapshot_exists
                            FROM ledger_entries le
                            GROUP BY le.account_id, le.currency
                        ) lb
                        WHERE lb.snapshot_exists = 0
                           OR lb.ledger_balance <> lb.snapshot_balance
                        UNION ALL
                        SELECT
                            s.account_id AS account_id,
                            s.currency AS currency,
                            0 AS ledger_balance,
                            s.balance AS snapshot_balance,
                            0 - s.balance AS diff_amount,
                            'LEDGER_MISSING' AS reason
                        FROM account_snapshots s
                        WHERE s.snapshot_id = (
                                SELECT s2.snapshot_id
                                FROM account_snapshots s2
                                WHERE s2.account_id = s.account_id
                                  AND s2.currency = s.currency
                                ORDER BY s2.ts DESC, s2.snapshot_id DESC
                                LIMIT 1
                        )
                          AND NOT EXISTS (
                                SELECT 1
                                FROM ledger_entries le2
                                WHERE le2.account_id = s.account_id
                                  AND le2.currency = s.currency
                        )
                          AND NOT EXISTS (
                                SELECT 1
                                FROM (
                                    SELECT
                                        p.account_id,
                                        split_part(p.symbol, '-', 1) AS currency,
                                        COALESCE(SUM(p.qty), 0) AS position_qty
                                    FROM positions p
                                    GROUP BY p.account_id, split_part(p.symbol, '-', 1)
                                ) pb
                                WHERE pb.account_id = s.account_id
                                  AND pb.currency = s.currency
                                  AND pb.position_qty = s.balance
                        )
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
