package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialRotateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

/**
 * ExchangeAccountCredentialCommandService 提供凭证新增与轮换写侧。
 * <p>
 * Why:
 * 凭证版本链必须始终满足“同账户同类型只有一个 active”，
 * 因此新增版本、失效旧版本和 masked key 生成要在一个应用服务里统一完成。
 */
public class ExchangeAccountCredentialCommandService {

    private static final String DEFAULT_CIPHER_SUITE = "PGP_SYM_AES256";
    private static final int LIFECYCLE_REASON_MAX_LENGTH = 1024;

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final ExchangeAccountCredentialRepository exchangeAccountCredentialRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ObjectMapper objectMapper
    ) {
        this(exchangeAccountRepository, exchangeAccountCredentialRepository, objectMapper, Clock.systemUTC());
    }

    ExchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
        this.exchangeAccountCredentialRepository = Objects.requireNonNull(
                exchangeAccountCredentialRepository,
                "exchangeAccountCredentialRepository must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ExchangeAccountCredentialSummary upsert(
            Long ownerUserId,
            Long exchangeAccountId,
            ExchangeAccountCredentialUpsertCommand command,
            int keyVersion
    ) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        String credentialType = normalizeCredentialType(command.credentialType());
        validatePayload(credentialType, command);
        Instant now = Instant.now(clock);
        Long rotatedFromCredentialId = exchangeAccountCredentialRepository.findActiveByAccountAndType(exchangeAccountId, credentialType)
                .map(ExchangeAccountCredentialSummary::credentialId)
                .orElse(null);
        if (rotatedFromCredentialId != null) {
            exchangeAccountCredentialRepository.deactivateActiveByAccountAndType(exchangeAccountId, credentialType, now);
        }
        return exchangeAccountCredentialRepository.insertNewVersion(
                exchangeAccountId,
                credentialType,
                toPayloadJson(
                        credentialType,
                        command.apiKey(),
                        command.secretKey(),
                        command.passphrase(),
                        command.privateKeyPem()
                ),
                keyVersion,
                DEFAULT_CIPHER_SUITE,
                maskAccessKey(command.apiKey()),
                rotatedFromCredentialId,
                now
        );
    }

    /**
     * 显式轮换指定 ACTIVE credential，并追加旧/新两条 audit log。
     *
     * <p>Why: 旧 upsert 只按 account + credentialType 切换 active，无法证明调用方确实选择了
     * 哪个旧 credential，也没有 append-only rotate 证据。本方法以 credentialId 为命令对象，
     * 在单事务里锁定旧 ACTIVE 版本、写入新 ACTIVE 版本、把旧版本标记 ROTATED，并写入
     * ROTATED / CREATED audit log。受既有 partial unique index 约束，物理 SQL 顺序先把旧版本
     * 标记 inactive 再插入新 active；任一步失败都会由事务回滚，避免成功响应留下无 active。</p>
     *
     * @param ownerUserId 当前认证用户 ID，必须拥有 exchangeAccountId
     * @param exchangeAccountId 凭证所属交易账户 ID
     * @param credentialId 要被替换的旧 ACTIVE credential 主键
     * @param command 新 credential material 和必填 rotate reason；credentialType 从旧记录派生
     * @param actor 当前认证主体；为空时落为 system
     * @param keyVersion 新 credential 加密主密钥版本
     * @return 新 ACTIVE credential 的非敏感摘要
     */
    @Transactional
    public ExchangeAccountCredentialSummary rotate(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            ExchangeAccountCredentialRotateCommand command,
            String actor,
            int keyVersion
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        Long normalizedCredentialId = requirePositive(credentialId, "credentialId");
        requireOwnedAccount(normalizedOwnerUserId, normalizedExchangeAccountId);
        ExchangeAccountCredentialSummary current = exchangeAccountCredentialRepository.findByCredentialIdForOwner(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
        if (!current.isActive() || !"ACTIVE".equals(current.credentialStatus())) {
            throw new IllegalStateException("credential rotate requires ACTIVE credential");
        }
        ExchangeAccountCredentialSummary locked = exchangeAccountCredentialRepository.findActiveByCredentialIdForOwnerForUpdate(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new IllegalStateException("credential rotate requires ACTIVE credential"));
        String credentialType = normalizeCredentialType(locked.credentialType());
        validatePayload(credentialType, command);
        String normalizedActor = normalizeActor(actor);
        String normalizedReason = normalizeRequiredLifecycleReason(command.reason());
        Instant now = Instant.now(clock);

        if (!exchangeAccountCredentialRepository.markRotated(
                normalizedCredentialId,
                normalizedExchangeAccountId,
                normalizedActor,
                now
        )) {
            throw new IllegalStateException("credential rotate requires ACTIVE credential");
        }
        ExchangeAccountCredentialSummary created = exchangeAccountCredentialRepository.insertNewVersion(
                normalizedExchangeAccountId,
                credentialType,
                toPayloadJson(
                        credentialType,
                        command.apiKey(),
                        command.secretKey(),
                        command.passphrase(),
                        command.privateKeyPem()
                ),
                keyVersion,
                DEFAULT_CIPHER_SUITE,
                maskAccessKey(command.apiKey()),
                normalizedCredentialId,
                now
        );
        exchangeAccountCredentialRepository.appendCredentialAuditLog(
                normalizedCredentialId,
                normalizedExchangeAccountId,
                "ROTATED",
                normalizedActor,
                normalizedReason,
                rotateMetadata("ROTATED", normalizedCredentialId, created.credentialId(), credentialType, true),
                now
        );
        exchangeAccountCredentialRepository.appendCredentialAuditLog(
                created.credentialId(),
                normalizedExchangeAccountId,
                "CREATED",
                normalizedActor,
                normalizedReason,
                rotateMetadata("ACTIVE", normalizedCredentialId, created.credentialId(), credentialType, true),
                now
        );
        return created;
    }

    public ExchangeAccountCredentialSummary requireActiveSummary(Long ownerUserId, Long exchangeAccountId) {
        return requireActiveSummary(ownerUserId, exchangeAccountId, null);
    }

    /**
     * 读取 active credential 摘要，可通过 credentialType 消除多 active type 歧义。
     *
     * <p>Why: V12 schema 允许同一 account 下多个 credential type 同时 ACTIVE。无 type
     * 调用只在候选唯一时兼容旧行为；多候选会由 Repository 抛出状态冲突，避免按更新时间选错。</p>
     */
    public ExchangeAccountCredentialSummary requireActiveSummary(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        String normalizedCredentialType = normalizeOptionalCredentialType(credentialType);
        if (normalizedCredentialType == null) {
            return exchangeAccountCredentialRepository.findActiveSummary(
                    normalizedOwnerUserId,
                    normalizedExchangeAccountId
            ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
        }
        return exchangeAccountCredentialRepository.findActiveSummary(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialType
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
    }

    public ExchangeAccountCredentialSummary findActiveSummaryOrNull(Long ownerUserId, Long exchangeAccountId) {
        return findActiveSummaryOrNull(ownerUserId, exchangeAccountId, null);
    }

    /**
     * 读取 active credential 摘要；未配置时返回 null，多 active type 时保持冲突。
     *
     * <p>Why: `GET /credentials/active` 需要保留“无 active 返回 null”的响应形态，
     * 但不能把多 ACTIVE type 静默压成最新一条。</p>
     */
    public ExchangeAccountCredentialSummary findActiveSummaryOrNull(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        String normalizedCredentialType = normalizeOptionalCredentialType(credentialType);
        if (normalizedCredentialType == null) {
            return exchangeAccountCredentialRepository.findActiveSummary(
                    normalizedOwnerUserId,
                    normalizedExchangeAccountId
            ).orElse(null);
        }
        return exchangeAccountCredentialRepository.findActiveSummary(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialType
        ).orElse(null);
    }

    /**
     * 不可恢复撤销指定 credential，并追加 REVOKED 审计事件。
     *
     * <p>Why: REVOKED 是安全闭环状态，不允许后续恢复为 active。重复 revoke
     * 保持幂等返回当前摘要，避免用户重复点击导致重复审计噪音。</p>
     *
     * @param ownerUserId 当前认证用户 ID，必须拥有 exchangeAccountId
     * @param exchangeAccountId 凭证所属交易账户 ID
     * @param credentialId 要撤销的 credential 主键
     * @param actor 当前认证主体；为空时落为 system 并在交付风险中说明
     * @param reason 撤销原因，可空；最长 1024，且不得包含明显敏感材料
     * @return 更新后的非敏感凭证摘要
     */
    @Transactional
    public ExchangeAccountCredentialSummary revoke(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String actor,
            String reason
    ) {
        return transitionLifecycle(ownerUserId, exchangeAccountId, credentialId, "REVOKED", actor, reason);
    }

    /**
     * 临时禁用指定 credential，并追加 DISABLED 审计事件。
     *
     * <p>Why: DISABLED 表示可后续单独设计恢复的临时停用状态，本轮不实现 enable；
     * 对已经 REVOKED 或 ROTATED 的历史记录返回明确状态冲突，避免破坏审计语义。</p>
     */
    @Transactional
    public ExchangeAccountCredentialSummary disable(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String actor,
            String reason
    ) {
        return transitionLifecycle(ownerUserId, exchangeAccountId, credentialId, "DISABLED", actor, reason);
    }

    /**
     * 标记指定 credential 过期，并追加 EXPIRED 审计事件。
     *
     * <p>Why: EXPIRED 表示凭证因为时间或外部策略不可用，不等价于不可恢复撤销；
     * active material 查询会排除该状态，但不会删除历史凭证版本。</p>
     */
    @Transactional
    public ExchangeAccountCredentialSummary expire(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String actor,
            String reason
    ) {
        return transitionLifecycle(ownerUserId, exchangeAccountId, credentialId, "EXPIRED", actor, reason);
    }

    private void requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        exchangeAccountRepository.findByIdForOwner(
                requirePositive(ownerUserId, "ownerUserId"),
                requirePositive(exchangeAccountId, "exchangeAccountId")
        ).orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }

    private ExchangeAccountCredentialSummary transitionLifecycle(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String targetStatus,
            String actor,
            String reason
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        Long normalizedCredentialId = requirePositive(credentialId, "credentialId");
        requireOwnedAccount(normalizedOwnerUserId, normalizedExchangeAccountId);
        ExchangeAccountCredentialSummary current = exchangeAccountCredentialRepository.findByCredentialIdForOwner(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
        if (targetStatus.equals(current.credentialStatus())) {
            return current;
        }
        if (!"REVOKED".equals(targetStatus)
                && ("REVOKED".equals(current.credentialStatus()) || "ROTATED".equals(current.credentialStatus()))) {
            throw new IllegalStateException("credential lifecycle status cannot transition from "
                    + current.credentialStatus() + " to " + targetStatus);
        }
        Instant now = Instant.now(clock);
        String normalizedActor = normalizeActor(actor);
        String normalizedReason = normalizeLifecycleReason(reason);
        Instant revokedAt = "REVOKED".equals(targetStatus) ? now : null;
        String revokedBy = "REVOKED".equals(targetStatus) ? normalizedActor : null;
        String revokeReason = "REVOKED".equals(targetStatus) ? normalizedReason : null;
        boolean updated = exchangeAccountCredentialRepository.updateLifecycleStatus(
                normalizedCredentialId,
                normalizedExchangeAccountId,
                targetStatus,
                false,
                revokedAt,
                revokedBy,
                revokeReason,
                now
        );
        if (!updated) {
            throw new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId);
        }
        exchangeAccountCredentialRepository.appendCredentialAuditLog(
                normalizedCredentialId,
                normalizedExchangeAccountId,
                targetStatus,
                normalizedActor,
                normalizedReason,
                lifecycleMetadata(targetStatus),
                now
        );
        return exchangeAccountCredentialRepository.findByCredentialIdForOwner(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
    }

    private String normalizeCredentialType(String credentialType) {
        String normalized = normalizeText(credentialType, "credentialType").toUpperCase(Locale.ROOT);
        if (!"OKX_API_V5".equals(normalized)
                && !"BINANCE_HMAC".equals(normalized)
                && !"BINANCE_ED25519".equals(normalized)) {
            throw new IllegalArgumentException("unsupported credentialType: " + credentialType);
        }
        return normalized;
    }

    private String normalizeOptionalCredentialType(String credentialType) {
        return credentialType == null || credentialType.isBlank() ? null : normalizeCredentialType(credentialType);
    }

    private void validatePayload(String credentialType, ExchangeAccountCredentialUpsertCommand command) {
        validatePayloadFields(
                credentialType,
                command.apiKey(),
                command.secretKey(),
                command.passphrase(),
                command.privateKeyPem()
        );
    }

    private void validatePayload(String credentialType, ExchangeAccountCredentialRotateCommand command) {
        validatePayloadFields(
                credentialType,
                command.apiKey(),
                command.secretKey(),
                command.passphrase(),
                command.privateKeyPem()
        );
    }

    private void validatePayloadFields(
            String credentialType,
            String apiKey,
            String secretKey,
            String passphrase,
            String privateKeyPem
    ) {
        normalizeText(apiKey, "apiKey");
        switch (credentialType) {
            case "OKX_API_V5" -> {
                normalizeText(secretKey, "secretKey");
                normalizeText(passphrase, "passphrase");
            }
            case "BINANCE_HMAC" -> normalizeText(secretKey, "secretKey");
            case "BINANCE_ED25519" -> normalizeText(privateKeyPem, "privateKeyPem");
            default -> throw new IllegalArgumentException("unsupported credentialType: " + credentialType);
        }
    }

    private String toPayloadJson(
            String credentialType,
            String apiKey,
            String secretKey,
            String passphrase,
            String privateKeyPem
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("credentialType", credentialType);
        payload.put("apiKey", normalizeText(apiKey, "apiKey"));
        payload.put("secretKey", normalizeNullableText(secretKey));
        payload.put("passphrase", normalizeNullableText(passphrase));
        payload.put("privateKeyPem", normalizeNullableText(privateKeyPem));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize credential payload", ex);
        }
    }

    private String maskAccessKey(String apiKey) {
        String normalized = normalizeText(apiKey, "apiKey");
        if (normalized.length() <= 6) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 2);
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeActor(String value) {
        if (value == null || value.isBlank()) {
            return "system";
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("actor length must be less than or equal to 128");
        }
        return normalized;
    }

    private String normalizeRequiredLifecycleReason(String value) {
        String normalized = normalizeLifecycleReason(value);
        if (normalized == null) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return normalized;
    }

    private String normalizeLifecycleReason(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > LIFECYCLE_REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("reason length must be less than or equal to 1024");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("token")
                || lower.contains("api secret")
                || lower.contains("api_secret")
                || lower.contains("private key")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("mnemonic")
                || lower.contains("encrypted_payload")
                || normalized.contains("私钥")
                || normalized.contains("密钥")
                || normalized.contains("助记词")) {
            throw new IllegalArgumentException("reason must not contain sensitive credential material");
        }
        return normalized;
    }

    private String rotateMetadata(
            String credentialStatus,
            Long oldCredentialId,
            Long newCredentialId,
            String credentialType,
            boolean reasonPresent
    ) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("credentialStatus", credentialStatus);
        metadata.put("source", "credential_rotate_command");
        metadata.put("oldCredentialId", oldCredentialId);
        metadata.put("newCredentialId", newCredentialId);
        metadata.put("credentialType", credentialType);
        metadata.put("reasonPresent", reasonPresent);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize credential rotate audit metadata", ex);
        }
    }

    private String lifecycleMetadata(String targetStatus) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("credentialStatus", targetStatus);
        metadata.put("source", "credential_lifecycle_command");
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize credential audit metadata", ex);
        }
    }
}
