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
                  AND e.exchange_code<>'PAPER'
                UNION ALL
                SELECT p.trade_env FROM paper_trading_runs p
                JOIN accounts a ON a.account_id=p.canonical_account_id
                WHERE p.canonical_account_id=? AND ?='PAPER'
                  AND p.status='RUNNING' AND p.trade_env='SIM'
                  AND a.status='ACTIVE' AND a.venue='PAPER'
                  AND (NOT EXISTS(SELECT 1 FROM exchange_accounts e WHERE e.legacy_account_id=a.account_id)
                    OR EXISTS(SELECT 1 FROM tenant_sim_accounts s
                        JOIN exchange_accounts e ON e.exchange_account_id=s.exchange_account_id
                            AND e.legacy_account_id=s.account_id AND e.tenant_id=s.tenant_id
                        JOIN tenants t ON t.tenant_id=s.tenant_id
                        JOIN tenant_memberships m ON m.tenant_id=s.tenant_id AND m.user_id=e.owner_user_id
                        WHERE s.account_id=a.account_id AND s.tenant_id=p.tenant_id
                            AND s.bound_paper_run_id=p.paper_run_id AND s.reserved_paper_run_id=p.paper_run_id
                            AND t.tenant_kind='DEMO' AND t.status='ACTIVE'
                            AND m.membership_kind='DEMO' AND m.status='ACTIVE'
                            AND e.status='ACTIVE' AND e.trade_env='SIM' AND e.exchange_code='PAPER'
                            AND NOT EXISTS(SELECT 1 FROM exchange_account_credentials c
                                WHERE c.exchange_account_id=e.exchange_account_id)))
                """, (row, ignored) -> row.getString(1), tradingAccountId, venue, tradingAccountId, venue);
        if (environments.size() != 1 || !List.of("SIM", "LIVE").contains(environments.getFirst())) {
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE");
        }
        return environments.getFirst();
    }
}
