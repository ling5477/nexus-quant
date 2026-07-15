package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ExchangeAccountCredentialRepository 定义账户凭证版本链端口。
 * <p>
 * Why:
 * RC1-4 要求凭证必须以“新增版本 + active 切换”方式轮换，
 * 因此读 active 摘要、读解密材料、停用旧版本和回写校验状态都要统一从领域端口进入。
 */
public interface ExchangeAccountCredentialRepository {

    /**
     * 列出指定 owner + account 下所有当前 ACTIVE credential 摘要。
     *
     * <p>Why: V12 只保证同一 account + credentialType 一个 active，允许同一 account
     * 同时存在多个 active credential type。无 credentialType 的读取必须先看到完整候选集，
     * 才能在多候选时返回明确冲突，而不是按更新时间静默选错。</p>
     */
    List<ExchangeAccountCredentialSummary> listActiveSummaries(Long ownerUserId, Long exchangeAccountId);

    /**
     * 读取唯一 active 摘要；多 credential type 候选时抛出状态冲突。
     *
     * <p>Why: 保留旧调用方的单 active 兼容行为，同时把多 ACTIVE type 从隐式
     * `LIMIT 1` 改为显式业务冲突，避免后续权限探活或展示路径选错凭证。</p>
     */
    default Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId) {
        List<ExchangeAccountCredentialSummary> activeSummaries = listActiveSummaries(ownerUserId, exchangeAccountId);
        if (activeSummaries.size() > 1) {
            throw new IllegalStateException("multiple active credential types require credentialType");
        }
        return activeSummaries.isEmpty() ? Optional.empty() : Optional.of(activeSummaries.getFirst());
    }

    /**
     * 按 credentialType 显式读取 active 摘要。
     *
     * <p>Why: 当一个 account 合法持有多个 active credential type 时，调用方必须明确
     * 选择业务需要的 type，不能依赖更新时间或插入顺序。</p>
     */
    Optional<ExchangeAccountCredentialSummary> findActiveSummary(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    );

    Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType);

    /**
     * 读取唯一 active material；多 credential type 候选时抛出状态冲突。
     *
     * <p>Why: active material 包含服务端解密后的 payload，必须先通过摘要候选集确认
     * 无歧义，再按唯一 credentialType 读取 material，避免为冲突检测解密多份凭证。</p>
     */
    default Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId) {
        return findActiveSummary(ownerUserId, exchangeAccountId)
                .flatMap(summary -> findActiveMaterial(ownerUserId, exchangeAccountId, summary.credentialType()));
    }

    /**
     * 按 credentialType 显式读取 active material。
     *
     * <p>Why: 结构性校验、未来权限探活或只读/交易权限拆分都必须明确选择 credential type；
     * 本方法仍只允许 `is_active=true` 且 `credential_status='ACTIVE'` 的记录进入 material。</p>
     */
    Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    );

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

    /**
     * 按 account + credentialId 锁定当前 ACTIVE credential。
     *
     * <p>Why: 显式 rotate 要在一个事务里先锁住旧 active 版本，再完成旧版本 ROTATED、
     * 新版本 ACTIVE 和 audit log 写入，避免并发 rotate 留下双 active 或半成品审计。</p>
     */
    Optional<ExchangeAccountCredentialSummary> findActiveByCredentialIdForOwnerForUpdate(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
    );

    /**
     * 按 account + credentialId 锁定任意生命周期的 credential material。
     *
     * <p>Why: enable 只能从 DISABLED 恢复，结构性校验需要读取服务端解密后的 material，
     * 但该 material 不能进入 API response、普通日志或 audit metadata。调用方必须在同一事务内
     * 完成状态检查、结构性校验、ACTIVE 冲突检测、状态写回和 audit log，避免并发 enable 造成双 active。</p>
     */
    Optional<ExchangeAccountCredentialMaterial> findByCredentialIdForOwnerForUpdate(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId
    );

    /**
     * 检查同一 account + credentialType 下是否存在其他 ACTIVE credential。
     *
     * <p>Why: enable 会把一个 inactive credential 恢复为 ACTIVE。必须在写回前主动检查
     * 业务冲突，而不是依赖数据库 partial unique index 抛底层异常。</p>
     */
    boolean existsOtherActiveCredential(
            Long exchangeAccountId,
            String credentialType,
            Long excludedCredentialId
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
     * 把 credential 标记为 permission probe IN_PROGRESS。
     *
     * <p>Why: 同一 credential 同时只能有一个探活编排。调用方应先用 FOR UPDATE 锁定目标行，
     * 再用本方法 claim 状态，避免并发请求重复调用 adapter。</p>
     */
    default boolean markPermissionProbeInProgress(
            Long credentialId,
            Long exchangeAccountId,
            Instant updatedAt
    ) {
        throw new UnsupportedOperationException("permission probe claim is not implemented");
    }

    /**
     * 写回 permission probe 的 latest 脱敏摘要。
     *
     * <p>Why: V31 只提供 latest summary 字段，不新增 history 表；因此 Service 必须一次性写入
     * status、scope、withdraw、IP allowlist、脱敏错误和 failed_auth_count 增量。实现必须仅在
     * 当前状态仍为 IN_PROGRESS 时完成 CAS finalize；成功路径不得自动清零 failed_auth_count。</p>
     */
    default boolean markPermissionProbeResult(
            Long credentialId,
            Long exchangeAccountId,
            String permissionProbeStatus,
            String permissionScope,
            boolean withdrawEnabled,
            boolean ipAllowlistRequired,
            String ipAllowlistProbeStatus,
            Instant lastPermissionProbeAt,
            String lastPermissionProbeError,
            boolean incrementFailedAuthCount,
            Instant updatedAt
    ) {
        throw new UnsupportedOperationException("permission probe result writeback is not implemented");
    }

    /**
     * 把 DISABLED credential 恢复为 ACTIVE，并同步结构性校验结果。
     *
     * <p>Why: enable 成功必须原子写入 credential_status、is_active、verification_status、
     * last_verified_at 和 updated_at；不得清空 revoke/rotate 历史字段，也不得触碰 encrypted payload。</p>
     */
    boolean markEnabled(
            Long credentialId,
            Long exchangeAccountId,
            String verificationStatus,
            Instant verifiedAt,
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
     * 把旧 ACTIVE credential 标记为 ROTATED，不删除历史记录。
     *
     * <p>Why: rotate 与 revoke 语义不同，ROTATED 只说明被新版本替换，
     * 需要写 rotated_at / rotated_by，但不得写 revoke_reason 或 hard delete。</p>
     */
    boolean markRotated(
            Long credentialId,
            Long exchangeAccountId,
            String rotatedBy,
            Instant rotatedAt
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
