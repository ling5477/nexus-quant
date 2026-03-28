package com.guidinglight.nexusquant.infra.account.jdbc;

import com.guidinglight.nexusquant.core.account.application.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.core.account.domain.ExchangeAccountSummary;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcExchangeAccountRepository 提供 exchange_accounts 的最小查询实现。
 */
public class JdbcExchangeAccountRepository implements ExchangeAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcExchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
        return jdbcTemplate.query(
                """
                        SELECT exchange_account_id,
                               legacy_account_id,
                               owner_user_id,
                               exchange_code,
                               trade_env,
                               account_alias,
                               external_account_ref,
                               is_default,
                               status
                        FROM exchange_accounts
                        WHERE owner_user_id = ?
                        ORDER BY is_default DESC, exchange_code, trade_env, account_alias
                        """,
                (resultSet, rowNum) -> new ExchangeAccountSummary(
                        resultSet.getLong("exchange_account_id"),
                        (Long) resultSet.getObject("legacy_account_id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getString("exchange_code"),
                        resultSet.getString("trade_env"),
                        resultSet.getString("account_alias"),
                        resultSet.getString("external_account_ref"),
                        resultSet.getBoolean("is_default"),
                        resultSet.getString("status")
                ),
                ownerUserId
        );
    }

    @Override
    public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
        List<ExchangeAccountSummary> rows = jdbcTemplate.query(
                """
                        SELECT exchange_account_id,
                               legacy_account_id,
                               owner_user_id,
                               exchange_code,
                               trade_env,
                               account_alias,
                               external_account_ref,
                               is_default,
                               status
                        FROM exchange_accounts
                        WHERE owner_user_id = ? AND is_default = TRUE
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new ExchangeAccountSummary(
                        resultSet.getLong("exchange_account_id"),
                        (Long) resultSet.getObject("legacy_account_id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getString("exchange_code"),
                        resultSet.getString("trade_env"),
                        resultSet.getString("account_alias"),
                        resultSet.getString("external_account_ref"),
                        resultSet.getBoolean("is_default"),
                        resultSet.getString("status")
                ),
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
