package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;

import java.time.Instant;
import java.util.Optional;

/**
 * ExchangeAccountCredentialRepository 定义账户凭证版本链端口。
 * <p>
 * Why:
 * RC1-4 要求凭证必须以“新增版本 + active 切换”方式轮换，
 * 因此读 active 摘要、读解密材料、停用旧版本和回写校验状态都要统一从领域端口进入。
 */
public interface ExchangeAccountCredentialRepository {

    Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId);

    Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType);

    Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId);

    /**
     * 按 credentialId 读取凭证摘要，并复用 exchange account owner 校验。
     *
     * <p>Why: revoke / disable / expire 都是按 credentialId 执行的安全操作，
     * 不能只按 credentialId 裸查，否则可能绕过账户归属边界。</p>
     */
    Optional<ExchangeAccountCredentialSummary> findByCredentialIdForOwner(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
    );

    void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt);

    ExchangeAccountCredentialSummary insertNewVersion(
            Long exchangeAccountId,
            String credentialType,
            String encryptedPayloadJson,
            int keyVersion,
            String cipherSuite,
            String maskedAccessKey,
            Long rotatedFromCredentialId,
            Instant now
    );

    boolean markVerificationResult(
            Long credentialId,
            String verificationStatus,
            Instant verifiedAt,
            String lastVerificationError,
            Instant updatedAt
    );

    /**
     * 更新凭证生命周期状态，不删除 credential 历史版本。
     *
     * <p>Why: Batch 5-C 要让 DISABLED / REVOKED / EXPIRED 从 active material
     * 候选中退出，同时保留版本链和审计证据。revoke 字段仅在不可恢复撤销时写入。</p>
     */
    boolean updateLifecycleStatus(
            Long credentialId,
            Long exchangeAccountId,
            String credentialStatus,
            boolean active,
            Instant revokedAt,
            String revokedBy,
            String revokeReason,
            Instant updatedAt
    );

    /**
     * 追加凭证审计日志，调用方只允许传入脱敏 metadata。
     *
     * <p>Why: credential_audit_logs 是 append-only 安全证据表，
     * 不承载 encrypted_payload、secret、token、private key 或明文 payload。</p>
     */
    void appendCredentialAuditLog(
            Long credentialId,
            Long exchangeAccountId,
            String eventType,
            String actor,
            String reason,
            String metadataJson,
            Instant createdAt
    );
}
