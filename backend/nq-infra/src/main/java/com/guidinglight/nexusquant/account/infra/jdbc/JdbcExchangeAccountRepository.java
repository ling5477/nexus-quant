package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JdbcExchangeAccountRepository 提供 exchange_accounts 的最小读写实现。
 */
public class JdbcExchangeAccountRepository implements ExchangeAccountRepository {

    private static final String BASE_SELECT = """
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
            """;

    private static final RowMapper<ExchangeAccountSummary> ROW_MAPPER = JdbcExchangeAccountRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcExchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE owner_user_id = ? ORDER BY is_default DESC, exchange_code, trade_env, account_alias",
                ROW_MAPPER,
                ownerUserId
        );
    }

    @Override
    public Optional<ExchangeAccountSummary> findById(Long exchangeAccountId) {
        List<ExchangeAccountSummary> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE exchange_account_id = ?",
                ROW_MAPPER,
                exchangeAccountId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId) {
        List<ExchangeAccountSummary> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE owner_user_id = ? AND exchange_account_id = ?",
                ROW_MAPPER,
                ownerUserId,
                exchangeAccountId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
        List<ExchangeAccountSummary> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE owner_user_id = ? AND is_default = TRUE ORDER BY updated_at DESC LIMIT 1",
                ROW_MAPPER,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public ExchangeAccountSummary create(
            Long ownerUserId,
            String exchangeCode,
            String tradeEnv,
            String accountAlias,
            String externalAccountRef,
            Instant now
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO exchange_accounts (
                            owner_user_id,
                            exchange_code,
                            trade_env,
                            account_alias,
                            external_account_ref,
                            is_default,
                            status,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, FALSE, 'ACTIVE', ?, ?)
                        RETURNING exchange_account_id,
                                  legacy_account_id,
                                  owner_user_id,
                                  exchange_code,
                                  trade_env,
                                  account_alias,
                                  external_account_ref,
                                  is_default,
                                  status
                        """,
                ROW_MAPPER,
                ownerUserId,
                exchangeCode,
                tradeEnv,
                accountAlias,
                externalAccountRef,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    @Override
    public boolean updateProfile(
            Long ownerUserId,
            Long exchangeAccountId,
            String accountAlias,
            String externalAccountRef,
            Instant now
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_accounts
                        SET account_alias = ?,
                            external_account_ref = ?,
                            updated_at = ?
                        WHERE owner_user_id = ? AND exchange_account_id = ?
                        """,
                accountAlias,
                externalAccountRef,
                Timestamp.from(now),
                ownerUserId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_accounts
                        SET status = 'ACTIVE',
                            updated_at = ?
                        WHERE owner_user_id = ? AND exchange_account_id = ?
                        """,
                Timestamp.from(now),
                ownerUserId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_accounts
                        SET status = 'DISABLED',
                            is_default = FALSE,
                            updated_at = ?
                        WHERE owner_user_id = ? AND exchange_account_id = ?
                        """,
                Timestamp.from(now),
                ownerUserId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
        jdbcTemplate.update(
                """
                        UPDATE exchange_accounts
                        SET is_default = FALSE,
                            updated_at = ?
                        WHERE owner_user_id = ?
                          AND exchange_code = ?
                          AND trade_env = ?
                          AND is_default = TRUE
                        """,
                Timestamp.from(now),
                ownerUserId,
                exchangeCode,
                tradeEnv
        );
    }

    @Override
    public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_accounts
                        SET is_default = TRUE,
                            updated_at = ?
                        WHERE owner_user_id = ? AND exchange_account_id = ?
                        """,
                Timestamp.from(now),
                ownerUserId,
                exchangeAccountId
        ) > 0;
    }

    private static ExchangeAccountSummary mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExchangeAccountSummary(
                resultSet.getLong("exchange_account_id"),
                (Long) resultSet.getObject("legacy_account_id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getString("exchange_code"),
                resultSet.getString("trade_env"),
                resultSet.getString("account_alias"),
                resultSet.getString("external_account_ref"),
                resultSet.getBoolean("is_default"),
                resultSet.getString("status")
        );
    }
}


