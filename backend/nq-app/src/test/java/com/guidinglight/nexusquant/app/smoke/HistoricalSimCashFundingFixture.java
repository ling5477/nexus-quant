package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 仅在 V13 生成迁移输入；注资仍调用正式 fundOnce，不手写余额或经济分录。
 * 三个读取方法冻结自租户化前 82188a7e147c2c741fe354ce1d29e956f30f3e86；
 * 写入、快照、事件、账户及币种锁沿用正式 Repository。不得用于当前 Schema 的运行证明。
 */
final class HistoricalSimCashFundingFixture extends JdbcLedgerPostingRepository {
    private final JdbcTemplate jdbcTemplate;

    private HistoricalSimCashFundingFixture(JdbcTemplate jdbc) {
        super(jdbc);
        this.jdbcTemplate = jdbc;
        if (!"13".equals(jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                String.class))) {
            throw new IllegalStateException("historical cash fixture requires V13");
        }
    }

    static BigDecimal fundOnce(JdbcTemplate jdbc, long account, String run, BigDecimal budget) {
        var service = new SimCashFundingService(new HistoricalSimCashFundingFixture(jdbc));
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transactions.setTimeout(5);
        return transactions.execute(status -> service.fundOnce(account, run, budget, "pre-v14"));
    }

    @Override
    public void assertAccountEnvironment(Long accountId, String tradeEnv) {
        if (!"SIM".equals(tradeEnv) && !"LIVE".equals(tradeEnv)) {
            throw new IllegalArgumentException("trade environment must be explicit");
        }
        // 旧快照和不同环境的仓位可能共用同一账户；无法拆分时不得发布伪精确的环境投影。
        Boolean ambiguous = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM account_snapshots
                               WHERE account_id=? AND (trade_env IS NULL OR trade_env<>?))
                    OR EXISTS (SELECT 1 FROM trades WHERE account_id=? AND trade_env<>?)
                    OR (?='LIVE' AND EXISTS (SELECT 1 FROM ledger_entries
                                             WHERE account_id=? AND ref_type LIKE 'SIM_FUNDING_%'))
                    OR EXISTS (
                        SELECT 1 FROM positions p
                        WHERE p.account_id=? AND NOT EXISTS (
                            SELECT 1 FROM account_snapshots s
                            WHERE s.account_id=p.account_id AND s.trade_env=?
                              AND s.balance_basis='POSITION_PROJECTION'
                              AND s.currency=split_part(replace(p.symbol, '/', '-'), '-', 1)
                              AND s.snapshot_id=(
                                  SELECT MAX(latest.snapshot_id) FROM account_snapshots latest
                                  WHERE latest.account_id=s.account_id AND latest.currency=s.currency)
                              AND s.balance=(
                                  SELECT SUM(other.qty) FROM positions other
                                  WHERE other.account_id=p.account_id
                                    AND split_part(replace(other.symbol, '/', '-'), '-', 1)=s.currency)
                        )
                    )
                """, Boolean.class, accountId, tradeEnv, accountId, tradeEnv, tradeEnv, accountId,
                accountId, tradeEnv);
        if (!Boolean.FALSE.equals(ambiguous)) {
            throw new IllegalStateException("ACCOUNT_ENVIRONMENT_PROVENANCE_AMBIGUOUS");
        }
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ledger_entries WHERE idempotency_key = ?",
                Integer.class,
                idempotencyKey
        );
        return count != null && count > 0;
    }

    @Override
    public BigDecimal currentBalance(Long accountId, String currency, String tradeEnv) {
        if (!"SIM".equals(tradeEnv) && !"LIVE".equals(tradeEnv)) {
            throw new IllegalArgumentException("trade environment must be explicit");
        }
        BigDecimal balance = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CASE
                    WHEN e.ref_type='SIM_FUNDING_CASH' AND ?='SIM' THEN e.delta
                    WHEN e.ref_type='TRADE' AND EXISTS (
                        SELECT 1 FROM trades t WHERE t.trade_id=e.ref_id AND t.trade_env=?)
                    THEN CASE WHEN ?='SIM' THEN
                        CASE WHEN right(e.idempotency_key, 9)=':LEDGER:1'
                               OR right(e.idempotency_key, 13)=':LEDGER:FEE_1'
                             THEN e.delta ELSE 0 END
                        ELSE e.delta END
                    ELSE 0 END), 0)
                FROM ledger_entries e WHERE e.account_id = ? AND e.currency = ?
                """,
                BigDecimal.class,
                tradeEnv,
                tradeEnv,
                tradeEnv,
                accountId,
                currency
        );
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return balance;
    }
}
