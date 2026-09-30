package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.port.AccountTradeEnvironmentAuthority;
import com.guidinglight.nexusquant.account.domain.AccountTradeEnvironmentException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** 复用正式账户和隔离 SIM 运行的持久化事实，不读取凭证或推断账户编号。 */
public final class JdbcAccountTradeEnvironmentAuthority implements AccountTradeEnvironmentAuthority {
    private final JdbcTemplate jdbc;

    public JdbcAccountTradeEnvironmentAuthority(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public String resolve(Long tradingAccountId, String venue) {
        if (tradingAccountId == null || tradingAccountId <= 0 || venue == null || venue.isBlank()) {
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE");
        }
        List<String> environments = jdbc.query("""
                SELECT e.trade_env FROM exchange_accounts e
                JOIN accounts a ON a.account_id=e.legacy_account_id
                WHERE e.legacy_account_id=? AND e.exchange_code=?
                  AND e.status='ACTIVE' AND a.status='ACTIVE' AND a.venue=e.exchange_code
                UNION ALL
                SELECT p.trade_env FROM paper_trading_runs p
                JOIN accounts a ON a.account_id=p.canonical_account_id
                WHERE p.canonical_account_id=? AND ?='PAPER'
                  AND p.status='RUNNING' AND p.trade_env='SIM'
                  AND a.status='ACTIVE' AND a.venue='PAPER'
                """, (row, ignored) -> row.getString(1), tradingAccountId, venue, tradingAccountId, venue);
        if (environments.size() != 1 || !List.of("SIM", "LIVE").contains(environments.getFirst())) {
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE");
        }
        return environments.getFirst();
    }
}
