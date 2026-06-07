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
                   credential_status,
                   verification_status,
                   is_active,
                   revoked_at,
                   rotated_from_credential_id,
                   rotated_at,
                   last_verified_at,
                   last_verification_error,
                   updated_at
            FROM exchange_account_credentials
            """;

    private static final RowMapper<ExchangeAccountCredentialSummary> SUMMARY_ROW_MAPPER =
            JdbcExchangeAccountCredentialRepository::mapSummary;
    private static final RowMapper<ExchangeAccountCredentialMaterial> MATERIAL_ROW_MAPPER =
            JdbcExchangeAccountCredentialRepository::mapMaterial;

    private final JdbcTemplate jdbcTemplate;
    private final String masterKey;

    public JdbcExchangeAccountCredentialRepository(JdbcTemplate jdbcTemplate, String masterKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterKey = masterKey;
    }

    @Override
    public List<ExchangeAccountCredentialSummary> listActiveSummaries(Long ownerUserId, Long exchangeAccountId) {
        return jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE exchange_account_id = ?
                           AND is_active = TRUE
                           AND credential_status = 'ACTIVE'
                           AND EXISTS (
                               SELECT 1
                               FROM exchange_accounts ea
                               WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                 AND ea.owner_user_id = ?
                           )
                         ORDER BY credential_type ASC, credential_id ASC
                        """,
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                ownerUserId
        );
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findActiveSummary(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE exchange_account_id = ?
                           AND credential_type = ?
                           AND is_active = TRUE
                           AND credential_status = 'ACTIVE'
                           AND EXISTS (
                               SELECT 1
                               FROM exchange_accounts ea
                               WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                 AND ea.owner_user_id = ?
                           )
                        """,
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                credentialType,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE exchange_account_id = ?
                           AND credential_type = ?
                           AND is_active = TRUE
                           AND credential_status = 'ACTIVE'
                        """,
                SUMMARY_ROW_MAPPER,
                exchangeAccountId,
                credentialType
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findByCredentialIdForOwner(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
    ) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE credential_id = ?
                           AND exchange_account_id = ?
                           AND EXISTS (
                               SELECT 1
                               FROM exchange_accounts ea
                               WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                 AND ea.owner_user_id = ?
                           )
                         LIMIT 1
                        """,
                SUMMARY_ROW_MAPPER,
                credentialId,
                exchangeAccountId,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialSummary> findActiveByCredentialIdForOwnerForUpdate(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
    ) {
        List<ExchangeAccountCredentialSummary> rows = jdbcTemplate.query(
                SUMMARY_SELECT + """
                         WHERE credential_id = ?
                           AND exchange_account_id = ?
                           AND is_active = TRUE
                           AND credential_status = 'ACTIVE'
                           AND EXISTS (
                               SELECT 1
                               FROM exchange_accounts ea
                               WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                 AND ea.owner_user_id = ?
                           )
                         LIMIT 1
                         FOR UPDATE
                        """,
                SUMMARY_ROW_MAPPER,
                credentialId,
                exchangeAccountId,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        List<ExchangeAccountCredentialMaterial> rows = jdbcTemplate.query(
                """
                        SELECT credential_id,
                               exchange_account_id,
                               credential_type,
                               masked_access_key,
                               credential_status,
                               verification_status,
                               is_active,
                               revoked_at,
                               rotated_from_credential_id,
                               rotated_at,
                               last_verified_at,
                               last_verification_error,
                               updated_at,
                               pgp_sym_decrypt(encrypted_payload, ?) AS decrypted_payload_json
                        FROM exchange_account_credentials
                        WHERE exchange_account_id = ?
                          AND credential_type = ?
                          AND is_active = TRUE
                          AND credential_status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1
                              FROM exchange_accounts ea
                              WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                AND ea.owner_user_id = ?
                          )
                        """,
                MATERIAL_ROW_MAPPER,
                masterKey,
                exchangeAccountId,
                credentialType,
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
                            credential_status = 'ROTATED',
                            revoked_at = ?,
                            rotated_at = ?,
                            updated_at = ?
                        WHERE exchange_account_id = ?
                          AND credential_type = ?
                          AND is_active = TRUE
                """,
                Timestamp.from(revokedAt),
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
                            credential_status,
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
                            'ACTIVE',
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
                                  credential_status,
                                  verification_status,
                                  is_active,
                                  revoked_at,
                                  rotated_from_credential_id,
                                  rotated_at,
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

    @Override
    public boolean updateLifecycleStatus(
            Long credentialId,
            Long exchangeAccountId,
            String credentialStatus,
            boolean active,
            Instant revokedAt,
            String revokedBy,
            String revokeReason,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET credential_status = ?,
                            is_active = ?,
                            revoked_at = ?,
                            revoked_by = ?,
                            revoke_reason = ?,
                            updated_at = ?
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                        """,
                credentialStatus,
                active,
                toTimestamp(revokedAt),
                revokedBy,
                revokeReason,
                Timestamp.from(updatedAt),
                credentialId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public boolean markRotated(Long credentialId, Long exchangeAccountId, String rotatedBy, Instant rotatedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET credential_status = 'ROTATED',
                            is_active = FALSE,
                            rotated_at = ?,
                            rotated_by = ?,
                            updated_at = ?
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                          AND credential_status = 'ACTIVE'
                          AND is_active = TRUE
                        """,
                Timestamp.from(rotatedAt),
                rotatedBy,
                Timestamp.from(rotatedAt),
                credentialId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public void appendCredentialAuditLog(
            Long credentialId,
            Long exchangeAccountId,
            String eventType,
            String actor,
            String reason,
            String metadataJson,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO credential_audit_logs (
                            credential_id,
                            exchange_account_id,
                            event_type,
                            actor,
                            reason,
                            metadata,
                            created_at
                        ) VALUES (
                            ?,
                            ?,
                            ?,
                            ?,
                            ?,
                            CAST(? AS jsonb),
                            ?
                        )
                        """,
                credentialId,
                exchangeAccountId,
                eventType,
                actor,
                reason,
                metadataJson,
                Timestamp.from(createdAt)
        );
    }

    private static ExchangeAccountCredentialSummary mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExchangeAccountCredentialSummary(
                resultSet.getLong("credential_id"),
                resultSet.getLong("exchange_account_id"),
                resultSet.getString("credential_type"),
                resultSet.getString("masked_access_key"),
                resultSet.getString("credential_status"),
                resultSet.getString("verification_status"),
                resultSet.getBoolean("is_active"),
                toInstant(resultSet.getTimestamp("revoked_at")),
                (Long) resultSet.getObject("rotated_from_credential_id"),
                toInstant(resultSet.getTimestamp("rotated_at")),
                toInstant(resultSet.getTimestamp("last_verified_at")),
                resultSet.getString("last_verification_error"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static ExchangeAccountCredentialMaterial mapMaterial(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExchangeAccountCredentialMaterial(
                resultSet.getLong("credential_id"),
                resultSet.getLong("exchange_account_id"),
                resultSet.getString("credential_type"),
                resultSet.getString("masked_access_key"),
                resultSet.getString("credential_status"),
                resultSet.getString("verification_status"),
                resultSet.getBoolean("is_active"),
                toInstant(resultSet.getTimestamp("revoked_at")),
                (Long) resultSet.getObject("rotated_from_credential_id"),
                toInstant(resultSet.getTimestamp("rotated_at")),
                toInstant(resultSet.getTimestamp("last_verified_at")),
                resultSet.getString("last_verification_error"),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("decrypted_payload_json")
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
