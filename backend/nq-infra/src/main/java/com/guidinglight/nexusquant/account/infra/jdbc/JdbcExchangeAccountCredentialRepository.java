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
                   updated_at,
                   permission_probe_status,
                   permission_scope,
                   withdraw_enabled,
                   ip_allowlist_probe_status,
                   failed_auth_count,
                   last_permission_probe_at,
                   last_permission_probe_error
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
    public Optional<ExchangeAccountCredentialMaterial> findByCredentialIdForOwnerForUpdate(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
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
                               permission_probe_status,
                               permission_scope,
                               withdraw_enabled,
                               ip_allowlist_probe_status,
                               failed_auth_count,
                               last_permission_probe_at,
                               last_permission_probe_error,
                               pgp_sym_decrypt(encrypted_payload, ?) AS decrypted_payload_json
                        FROM exchange_account_credentials
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                          AND EXISTS (
                              SELECT 1
                              FROM exchange_accounts ea
                              WHERE ea.exchange_account_id = exchange_account_credentials.exchange_account_id
                                AND ea.owner_user_id = ?
                          )
                        LIMIT 1
                        FOR UPDATE
                        """,
                MATERIAL_ROW_MAPPER,
                masterKey,
                credentialId,
                exchangeAccountId,
                ownerUserId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean existsOtherActiveCredential(
            Long exchangeAccountId,
            String credentialType,
            Long excludedCredentialId
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM exchange_account_credentials
                        WHERE exchange_account_id = ?
                          AND credential_type = ?
                          AND credential_id <> ?
                          AND is_active = TRUE
                          AND credential_status = 'ACTIVE'
                        """,
                Integer.class,
                exchangeAccountId,
                credentialType,
                excludedCredentialId
        );
        return count != null && count > 0;
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
                               permission_probe_status,
                               permission_scope,
                               withdraw_enabled,
                               ip_allowlist_probe_status,
                               failed_auth_count,
                               last_permission_probe_at,
                               last_permission_probe_error,
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
                                  updated_at,
                                  permission_probe_status,
                                  permission_scope,
                                  withdraw_enabled,
                                  ip_allowlist_probe_status,
                                  failed_auth_count,
                                  last_permission_probe_at,
                                  last_permission_probe_error
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
    public boolean markPermissionProbeInProgress(Long credentialId, Long exchangeAccountId, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET permission_probe_status = 'IN_PROGRESS',
                            last_permission_probe_error = NULL,
                            updated_at = ?
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                          AND permission_probe_status <> 'IN_PROGRESS'
                        """,
                Timestamp.from(updatedAt),
                credentialId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public boolean markPermissionProbeResult(
            Long credentialId,
            Long exchangeAccountId,
            String permissionProbeStatus,
            String permissionScope,
            String ipAllowlistProbeStatus,
            Instant lastPermissionProbeAt,
            String lastPermissionProbeError,
            boolean incrementFailedAuthCount,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET permission_probe_status = ?,
                            permission_scope = ?,
                            ip_allowlist_probe_status = ?,
                            last_permission_probe_at = ?,
                            last_permission_probe_error = ?,
                            failed_auth_count = failed_auth_count + CASE WHEN ? THEN 1 ELSE 0 END,
                            updated_at = ?
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                        """,
                permissionProbeStatus,
                permissionScope,
                ipAllowlistProbeStatus,
                Timestamp.from(lastPermissionProbeAt),
                lastPermissionProbeError,
                incrementFailedAuthCount,
                Timestamp.from(updatedAt),
                credentialId,
                exchangeAccountId
        ) > 0;
    }

    @Override
    public boolean markEnabled(
            Long credentialId,
            Long exchangeAccountId,
            String verificationStatus,
            Instant verifiedAt,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE exchange_account_credentials
                        SET credential_status = 'ACTIVE',
                            is_active = TRUE,
                            verification_status = ?,
                            last_verified_at = ?,
                            last_verification_error = NULL,
                            updated_at = ?
                        WHERE credential_id = ?
                          AND exchange_account_id = ?
                          AND credential_status = 'DISABLED'
                          AND is_active = FALSE
                        """,
                verificationStatus,
                Timestamp.from(verifiedAt),
                Timestamp.from(updatedAt),
                credentialId,
                exchangeAccountId
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
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("permission_probe_status"),
                resultSet.getString("permission_scope"),
                resultSet.getBoolean("withdraw_enabled"),
                resultSet.getString("ip_allowlist_probe_status"),
                resultSet.getInt("failed_auth_count"),
                toInstant(resultSet.getTimestamp("last_permission_probe_at")),
                resultSet.getString("last_permission_probe_error")
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
                resultSet.getString("decrypted_payload_json"),
                resultSet.getString("permission_probe_status"),
                resultSet.getString("permission_scope"),
                resultSet.getBoolean("withdraw_enabled"),
                resultSet.getString("ip_allowlist_probe_status"),
                resultSet.getInt("failed_auth_count"),
                toInstant(resultSet.getTimestamp("last_permission_probe_at")),
                resultSet.getString("last_permission_probe_error")
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
