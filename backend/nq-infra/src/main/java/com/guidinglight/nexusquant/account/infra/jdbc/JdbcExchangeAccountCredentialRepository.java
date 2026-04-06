package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JdbcExchangeAccountCredentialRepository 提供账户凭证版本链 JDBC 实现。
 */
public class JdbcExchangeAccountCredentialRepository implements ExchangeAccountCredentialRepository {

    private static final String SUMMARY_SELECT = """
            SELECT credential_id,
                   exchange_account_id,
                   credential_type,
                   masked_access_key,
                   verification_status,
                   is_active,
                   rotated_from_credential_id,
                   last_verified_at,
                   last_verification_error,
                   updated_at
            FROM exchange_account_credentials
            """;

    private static final RowMapper<ExchangeAccountCredentialSummary> SUMMARY_ROW_MAPPER =
            JdbcExchangeAccountCredentialRepository::mapSummary;

    private final JdbcTemplate jdbcTemplate;
    private final String masterKey;

    public JdbcExchangeAccountCredentialRepository(JdbcTemplate jdbcTemplate, String masterKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterKey = masterKey;
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE exchange_account_id = ?
                           AND is_active = TRUE
                           AND EXISTS (
                               SELECT 1
                               FROM exchange_accounts ea
                               WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                 AND ea.owner_user_id = ?
                           )
                         ORDER BY updated_at DESC
                         LIMIT 1
                        """,
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + " WHERE exchange_account_id = ? AND credential_type = ? AND is_active = TRUE",
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                credentialType
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId) {
        List<ExchangeAccountCredentialMaterial> rows = jdbcTemplate.query(
                """
                        SELECT credential_id,
                               exchange_account_id,
                               credential_type,
                               masked_access_key,
                               verification_status,
                               is_active,
                               rotated_from_credential_id,
                               last_verified_at,
                               last_verification_error,
                               updated_at,
                               pgp_sym_decrypt(encrypted_payload, ?) AS decrypted_payload_json
                        FROM exchange_account_credentials
                        WHERE exchange_account_id = ?
                          AND is_active = TRUE
                          AND EXISTS (
                              SELECT 1
                              FROM exchange_accounts ea
                              WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                AND ea.owner_user_id = ?
                          )
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new ExchangeAccountCredentialMaterial(
                        resultSet.getLong("credential_id"),
                        resultSet.getLong("exchange_account_id"),
                        resultSet.getString("credential_type"),
                        resultSet.getString("masked_access_key"),
                        resultSet.getString("verification_status"),
                        resultSet.getBoolean("is_active"),
                        (Long) resultSet.getObject("rotated_from_credential_id"),
                        toInstant(resultSet.getTimestamp("last_verified_at")),
                        resultSet.getString("last_verification_error"),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        resultSet.getString("decrypted_payload_json")
                ),
                masterKey,
                exchangeAccountId,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt) {
        jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET is_active = FALSE,
                            verification_status = 'REVOKED',
                            revoked_at = ?,
                            updated_at = ?
                        WHERE exchange_account_id = ?
                          AND credential_type = ?
                          AND is_active = TRUE
                        """,
                Timestamp.from(revokedAt),
                Timestamp.from(revokedAt),
                exchangeAccountId,
                credentialType
        );
    }

    @Override
    public ExchangeAccountCredentialSummary insertNewVersion(
            Long exchangeAccountId,
            String credentialType,
            String encryptedPayloadJson,
            int keyVersion,
            String cipherSuite,
            String maskedAccessKey,
            Long rotatedFromCredentialId,
            Instant now
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO exchange_account_credentials (
                            exchange_account_id,
                            credential_type,
                            encrypted_payload,
                            key_version,
                            cipher_suite,
                            masked_access_key,
                            verification_status,
                            is_active,
                            revoked_at,
                            rotated_from_credential_id,
                            last_verified_at,
                            last_verification_error,
                            created_at,
                            updated_at
                        ) VALUES (
                            ?,
                            ?,
                            pgp_sym_encrypt(CAST(? AS TEXT), ?, 'cipher-algo=aes256'),
                            ?,
                            ?,
                            ?,
                            'PENDING',
                            TRUE,
                            NULL,
                            ?,
                            NULL,
                            NULL,
                            ?,
                            ?
                        )
                        RETURNING credential_id,
                                  exchange_account_id,
                                  credential_type,
                                  masked_access_key,
                                  verification_status,
                                  is_active,
                                  rotated_from_credential_id,
                                  last_verified_at,
                                  last_verification_error,
                                  updated_at
                        """,
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                credentialType,
                encryptedPayloadJson,
                masterKey,
                keyVersion,
                cipherSuite,
                maskedAccessKey,
                rotatedFromCredentialId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    @Override
    public boolean markVerificationResult(
            Long credentialId,
            String verificationStatus,
            Instant verifiedAt,
            String lastVerificationError,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET verification_status = ?,
                            last_verified_at = ?,
                            last_verification_error = ?,
                            updated_at = ?
                        WHERE credential_id = ?
                        """,
                verificationStatus,
                Timestamp.from(verifiedAt),
                lastVerificationError,
                Timestamp.from(updatedAt),
                credentialId
        ) > 0;
    }

    private static ExchangeAccountCredentialSummary mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExchangeAccountCredentialSummary(
                resultSet.getLong("credential_id"),
                resultSet.getLong("exchange_account_id"),
                resultSet.getString("credential_type"),
                resultSet.getString("masked_access_key"),
                resultSet.getString("verification_status"),
                resultSet.getBoolean("is_active"),
                (Long) resultSet.getObject("rotated_from_credential_id"),
                toInstant(resultSet.getTimestamp("last_verified_at")),
                resultSet.getString("last_verification_error"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
