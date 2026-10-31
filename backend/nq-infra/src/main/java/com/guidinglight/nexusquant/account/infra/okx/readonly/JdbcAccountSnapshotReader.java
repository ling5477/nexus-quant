package com.guidinglight.nexusquant.account.infra.okx.readonly;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 按明确环境和发布身份读取 NQ 账户投影；历史未知环境不得进入 LIVE 结果。
 * snapshot_id 在账户锁下确定最新投影；recorded_at 是实际插入时间，created_at 是事务起点的数据库时间。
 * 两者都不是交易所同步时间，不能单独证明与当前 OKX 余额可比。
 */
public final class JdbcAccountSnapshotReader {
    private final JdbcTemplate jdbc;

    public JdbcAccountSnapshotReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Snapshot> latest(long accountId, String tradeEnv) {
        if (!"SIM".equals(tradeEnv) && !"LIVE".equals(tradeEnv)) {
            throw new IllegalArgumentException("trade environment must be explicit");
        }
        return jdbc.query("""
                SELECT DISTINCT ON (currency) snapshot_id, currency, balance, available, frozen,
                       ts, recorded_at, trade_env, balance_basis, balance_scope
                FROM account_snapshots
                WHERE account_id=? AND trade_env=?
                ORDER BY currency, snapshot_id DESC
                LIMIT 101
                """, (rs, row) -> new Snapshot(
                rs.getLong("snapshot_id"), rs.getString("currency"), rs.getBigDecimal("balance"),
                rs.getBigDecimal("available"), rs.getBigDecimal("frozen"),
                rs.getTimestamp("ts").toInstant(), rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("trade_env"), rs.getString("balance_basis"), rs.getString("balance_scope")),
                accountId, tradeEnv);
    }

    public boolean hasUnknownHistory(long accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM account_snapshots
                               WHERE account_id=? AND trade_env IS NULL)
                """, Boolean.class, accountId));
    }

    public record Snapshot(long snapshotId, String currency, BigDecimal balance, BigDecimal available,
                    BigDecimal frozen, Instant sourceEventAt, Instant recordedAt,
                    String tradeEnv, String balanceBasis, String balanceScope) {
    }
}
