package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.CredentialPermissionProbeCommand;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionOperations;

/**
 * CredentialPermissionProbeService 编排 credential permission probe 的本地安全 gate、状态写回和 audit。
 *
 * <p>Why: permission probe 涉及 credential material、交易所权限与失败计数，必须先完成
 * owner/account/credential/Paper/LIVE/withdraw gate，Service 本身不得写 HTTP。真实交易所访问只能由
 * ExchangeCredentialPermissionProbePort 的 adapter 实现承载，本轮默认使用 no-real-exchange fake。</p>
 */
public class CredentialPermissionProbeService {

    private static final int REASON_MAX_LENGTH = 1024;

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final ExchangeAccountCredentialRepository credentialRepository;
    private final ExchangeCredentialPermissionProbePort permissionProbePort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionOperations transactions;

    public CredentialPermissionProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            ExchangeCredentialPermissionProbePort permissionProbePort,
            ObjectMapper objectMapper,
            TransactionOperations transactions
    ) {
        this(exchangeAccountRepository, credentialRepository, permissionProbePort, objectMapper,
                Clock.systemUTC(), transactions);
    }

    CredentialPermissionProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            ExchangeCredentialPermissionProbePort permissionProbePort,
            ObjectMapper objectMapper,
            Clock clock,
            TransactionOperations transactions
    ) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
        this.credentialRepository = Objects.requireNonNull(
                credentialRepository,
                "credentialRepository must not be null"
        );
        this.permissionProbePort = Objects.requireNonNull(permissionProbePort, "permissionProbePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    /**
     * 触发一次同步 permission probe。
     *
     * <p>事务/并发：claim/skip 与 finalize 分别在短事务内执行；真实 HTTP 位于两段事务之间。
     * IN_PROGRESS CAS 防止重复 probe，finalize 仅允许更新仍由本次 claim 持有的 IN_PROGRESS 行。</p>
     *
     * @param ownerUserId       当前认证用户 ID，必须拥有 exchangeAccountId
     * @param exchangeAccountId 凭证所属账户
     * @param credentialId      从路径派生的 credentialId，credentialType 也从该记录派生
     * @param actor             当前认证主体；为空时落为 system
     * @param command           非敏感控制输入；不得包含 credential material
     * @param traceId           当前请求 trace id，可空
     * @return 脱敏 latest summary，不包含 credential material 或 raw exchange response
     */
    public CredentialPermissionProbeSummary probe(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String actor,
            CredentialPermissionProbeCommand command,
            String traceId
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        Long normalizedCredentialId = requirePositive(credentialId, "credentialId");
        String normalizedActor = normalizeActor(actor);
        String normalizedReason = normalizeReason(command == null ? null : command.reason());
        String requestId = UUID.randomUUID().toString();
        ProbePreparation preparation = Objects.requireNonNull(transactions.execute(status -> prepare(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId,
                normalizedActor,
                normalizedReason,
                command,
                requestId,
                traceId
        )), "permission probe preparation must not be null");
        if (preparation.completedSummary() != null) {
            return preparation.completedSummary();
        }
        Instant startedAt = Instant.now(clock);
        ExchangeCredentialPermissionProbeResult result;
        try {
            result = permissionProbePort.probe(new ExchangeCredentialPermissionProbeRequest(
                    preparation.account().ownerUserId(),
                    preparation.account().exchangeAccountId(),
                    preparation.credential().credentialId(),
                    preparation.account().exchangeCode(),
                    preparation.account().tradeEnv(),
                    preparation.credential().credentialType(),
                    normalizeMode(command == null ? null : command.mode()),
                    Boolean.TRUE.equals(command == null ? null : command.dryRun()),
                    traceId
            ));
        } catch (RuntimeException ex) {
            result = ExchangeCredentialPermissionProbeResult.failed(
                    preparation.account().exchangeCode(),
                    preparation.credential().credentialType(),
                    "INTERNAL_PROBE_FAILURE",
                    "UNKNOWN",
                    requestId,
                    traceId,
                    startedAt,
                    Instant.now(clock)
            );
        }
        ExchangeCredentialPermissionProbeResult completedResult = result;
        return Objects.requireNonNull(
                transactions.execute(status -> finalizeProbe(preparation, completedResult)),
                "permission probe finalization must not be null"
        );
    }

    private ProbePreparation prepare(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String actor,
            String reason,
            CredentialPermissionProbeCommand command,
            String requestId,
            String traceId
    ) {
        ExchangeAccountSummary account = requireOwnedAccount(ownerUserId, exchangeAccountId);
        ExchangeAccountCredentialSummary credential = credentialRepository.findByCredentialIdForOwner(
                ownerUserId,
                exchangeAccountId,
                credentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(exchangeAccountId));
        if ("IN_PROGRESS".equals(credential.permissionProbeStatus())) {
            throw new IllegalStateException("credential permission probe already in progress");
        }
        Instant now = Instant.now(clock);
        if (!credentialRepository.markPermissionProbeInProgress(credentialId, exchangeAccountId, now)) {
            throw new IllegalStateException("credential permission probe already in progress");
        }
        if (!credential.isActive() || !"ACTIVE".equals(credential.credentialStatus())) {
            return completed(account, credential, actor, reason, "CREDENTIAL_NOT_ACTIVE", requestId, traceId, now);
        }
        if ("LIVE".equalsIgnoreCase(account.tradeEnv())
                && !permissionProbePort.supportsControlledLiveReadOnlyProbe()) {
            return completed(account, credential, actor, reason, "LIVE_CREDENTIAL_BLOCKED", requestId, traceId, now);
        }
        if (credential.withdrawEnabled()) {
            return completed(account, credential, actor, reason, "WITHDRAW_ENABLED_RISK", requestId, traceId, now);
        }
        if (!paperSafetyGatePassed(command)) {
            return completed(account, credential, actor, reason, "PAPER_SAFETY_GATE_MISSING", requestId, traceId, now);
        }
        appendAudit(credential, "PERMISSION_PROBE_STARTED", actor, reason,
                null, credential.permissionProbeStatus(), "IN_PROGRESS", requestId, traceId, false);
        return new ProbePreparation(account, credential, actor, reason, requestId, traceId, null);
    }

    private ProbePreparation completed(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String actor,
            String reason,
            String policyDecision,
            String requestId,
            String traceId,
            Instant now
    ) {
        return new ProbePreparation(
                account,
                credential,
                actor,
                reason,
                requestId,
                traceId,
                skip(account, credential, actor, reason, policyDecision, requestId, traceId, now)
        );
    }

    private CredentialPermissionProbeSummary finalizeProbe(
            ProbePreparation preparation,
            ExchangeCredentialPermissionProbeResult result
    ) {
        Instant finishedAt = result.finishedAt() == null ? Instant.now(clock) : result.finishedAt();
        String finalStatus = normalizeProbeStatus(result.permissionProbeStatus());
        String errorCategory = sanitizeErrorCategory(result.sanitizedErrorCategory());
        boolean incrementFailedAuthCount = shouldIncrementFailedAuthCount(errorCategory);
        String observedPermissionScope = normalizePermissionScope(result.detectedPermissionScope());
        // 认证/网络失败没有产生 permission observation；不得因此清除最后已知的高风险权限事实。
        String persistedPermissionScope = observedPermissionScope == null
                ? preparation.credential().permissionScope()
                : observedPermissionScope;
        boolean persistedWithdrawEnabled = observedPermissionScope == null
                ? preparation.credential().withdrawEnabled()
                : result.withdrawEnabledDetected();
        String ipStatus = normalizeIpAllowlistStatus(result.ipAllowlistProbeStatus());
        String persistedIpStatus = observedPermissionScope == null && "UNKNOWN".equals(ipStatus)
                ? preparation.credential().ipAllowlistProbeStatus()
                : ipStatus;
        try {
            if (!credentialRepository.markPermissionProbeResult(
                    preparation.credential().credentialId(),
                    preparation.credential().exchangeAccountId(),
                    finalStatus,
                    persistedPermissionScope,
                    persistedWithdrawEnabled,
                    true,
                    persistedIpStatus,
                    finishedAt,
                    errorCategory,
                    incrementFailedAuthCount,
                    finishedAt
            )) {
                throw CredentialPermissionProbeWritebackException.versionConflict();
            }
            appendAudit(preparation.credential(), eventTypeFor(finalStatus), preparation.actor(), preparation.reason(),
                    errorCategory, "IN_PROGRESS", finalStatus,
                    emptyToDefault(result.requestId(), preparation.requestId()),
                    emptyToDefault(result.traceId(), preparation.traceId()), incrementFailedAuthCount,
                    observedPermissionScope, persistedIpStatus);
        } catch (CredentialPermissionProbeWritebackException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw CredentialPermissionProbeWritebackException.atomicWritebackFailed();
        }
        return latest(
                preparation.account().ownerUserId(),
                preparation.account().exchangeAccountId(),
                preparation.credential().credentialId(),
                emptyToDefault(result.requestId(), preparation.requestId()),
                emptyToDefault(result.traceId(), preparation.traceId())
        );
    }

    /**
     * 读取 latest permission probe summary。
     *
     * <p>Why: latest 查询只读 summary，不读取 decrypted payload，也不调用 adapter，避免用户查看状态时
     * 触发真实网络或 credential material 访问。</p>
     */
    public CredentialPermissionProbeSummary latest(
            Long ownerUserId,
            Long exchangeAccountId,
            Long credentialId,
            String requestId,
            String traceId
    ) {
        Long normalizedOwnerUserId = requirePositive(ownerUserId, "ownerUserId");
        Long normalizedExchangeAccountId = requirePositive(exchangeAccountId, "exchangeAccountId");
        Long normalizedCredentialId = requirePositive(credentialId, "credentialId");
        ExchangeAccountSummary account = requireOwnedAccount(normalizedOwnerUserId, normalizedExchangeAccountId);
        ExchangeAccountCredentialSummary credential = credentialRepository.findByCredentialIdForOwner(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));
        return CredentialPermissionProbeSummary.from(account, credential, requestId, traceId);
    }

    private CredentialPermissionProbeSummary skip(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String actor,
            String reason,
            String policyDecision,
            String requestId,
            String traceId,
            Instant now
    ) {
        if (!credentialRepository.markPermissionProbeResult(
                credential.credentialId(),
                credential.exchangeAccountId(),
                "SKIPPED",
                credential.permissionScope(),
                credential.withdrawEnabled(),
                true,
                "SKIPPED",
                now,
                policyDecision,
                false,
                now
        )) {
            throw new IllegalStateException("permission probe skip writeback conflict");
        }
        appendAudit(credential, "PERMISSION_PROBE_SKIPPED", actor, reason, policyDecision,
                credential.permissionProbeStatus(), "SKIPPED", requestId, traceId, false);
        ExchangeAccountCredentialSummary latestCredential = credentialRepository.findByCredentialIdForOwner(
                account.ownerUserId(),
                account.exchangeAccountId(),
                credential.credentialId()
        ).orElse(credential);
        return CredentialPermissionProbeSummary.from(account, latestCredential, requestId, traceId);
    }

    private ExchangeAccountSummary requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        return exchangeAccountRepository.findByIdForOwner(ownerUserId, exchangeAccountId)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }

    private boolean paperSafetyGatePassed(CredentialPermissionProbeCommand command) {
        if (command == null) {
            return false;
        }
        return Boolean.TRUE.equals(command.paperSafetyConfirmed())
                && Boolean.TRUE.equals(command.dryRun())
                && "PAPER".equals(normalizeMode(command.mode()));
    }

    private String eventTypeFor(String finalStatus) {
        return switch (finalStatus) {
            case "SUCCEEDED" -> "PERMISSION_PROBE_SUCCEEDED";
            case "FAILED" -> "PERMISSION_PROBE_FAILED";
            case "SKIPPED" -> "PERMISSION_PROBE_SKIPPED";
            default -> throw new IllegalArgumentException("unsupported permission probe status: " + finalStatus);
        };
    }

    private boolean shouldIncrementFailedAuthCount(String errorCategory) {
        return "AUTH_FAILED".equals(errorCategory)
                || "INVALID_API_KEY".equals(errorCategory)
                || "SIGNATURE_FAILED".equals(errorCategory)
                || "IP_ALLOWLIST_FAILED".equals(errorCategory)
                || "IP_ALLOWLIST_MISSING".equals(errorCategory)
                || "IP_ALLOWLIST_MISMATCH".equals(errorCategory)
                || "HTTP_UNAUTHORIZED".equals(errorCategory)
                || "HTTP_FORBIDDEN".equals(errorCategory)
                || "OKX_AUTHENTICATION_FAILED".equals(errorCategory)
                || "OKX_SIGNATURE_INVALID".equals(errorCategory)
                || "OKX_IP_NOT_ALLOWED".equals(errorCategory);
    }

    private void appendAudit(
            ExchangeAccountCredentialSummary credential,
            String eventType,
            String actor,
            String reason,
            String policyDecision,
            String fromStatus,
            String toStatus,
            String requestId,
            String traceId,
            boolean failedAuthCountIncremented
    ) {
        appendAudit(
                credential,
                eventType,
                actor,
                reason,
                policyDecision,
                fromStatus,
                toStatus,
                requestId,
                traceId,
                failedAuthCountIncremented,
                credential.permissionScope(),
                credential.ipAllowlistProbeStatus()
        );
    }

    private void appendAudit(
            ExchangeAccountCredentialSummary credential,
            String eventType,
            String actor,
            String reason,
            String policyDecision,
            String fromStatus,
            String toStatus,
            String requestId,
            String traceId,
            boolean failedAuthCountIncremented,
            String detectedScope,
            String ipAllowlistStatus
    ) {
        credentialRepository.appendCredentialAuditLog(
                credential.credentialId(),
                credential.exchangeAccountId(),
                eventType,
                actor,
                reason,
                auditMetadata(
                        credential,
                        fromStatus,
                        toStatus,
                        policyDecision,
                        requestId,
                        traceId,
                        failedAuthCountIncremented,
                        detectedScope,
                        ipAllowlistStatus
                ),
                Instant.now(clock)
        );
    }

    private String auditMetadata(
            ExchangeAccountCredentialSummary credential,
            String fromStatus,
            String toStatus,
            String policyDecision,
            String requestId,
            String traceId,
            boolean failedAuthCountIncremented,
            String detectedScope,
            String ipAllowlistStatus
    ) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("credentialId", credential.credentialId());
        metadata.put("accountId", credential.exchangeAccountId());
        metadata.put("credentialType", credential.credentialType());
        metadata.put("fromStatus", fromStatus);
        metadata.put("toStatus", toStatus);
        metadata.put("probeStatus", toStatus);
        metadata.put("detectedScope", detectedScope);
        metadata.put("ipAllowlistStatus", ipAllowlistStatus);
        metadata.put("policyDecision", policyDecision);
        metadata.put("errorCategory", policyDecision);
        metadata.put("requestId", requestId);
        metadata.put("traceId", traceId);
        metadata.put("failedAuthCountIncremented", failedAuthCountIncremented);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize credential permission probe audit metadata", ex);
        }
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
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

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("reason length must be less than or equal to 1024");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("token")
                || lower.contains("api key")
                || lower.contains("api_key")
                || lower.contains("api secret")
                || lower.contains("api_secret")
                || lower.contains("private key")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("mnemonic")
                || lower.contains("signature")
                || lower.contains("header")
                || lower.contains("raw response")
                || lower.contains("encrypted_payload")
                || lower.contains("decrypted_payload")
                || normalized.contains("私钥")
                || normalized.contains("密钥")
                || normalized.contains("助记词")) {
            throw new IllegalArgumentException("reason must not contain sensitive credential material");
        }
        return normalized;
    }

    private String normalizeMode(String value) {
        if (value == null || value.isBlank()) {
            return "PAPER";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"PAPER".equals(normalized)) {
            throw new IllegalArgumentException("permission probe mode must be PAPER");
        }
        return normalized;
    }

    private String normalizeProbeStatus(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (!"SUCCEEDED".equals(normalized) && !"FAILED".equals(normalized) && !"SKIPPED".equals(normalized)) {
            throw new IllegalArgumentException("unsupported permission probe status: " + value);
        }
        return normalized;
    }

    private String normalizePermissionScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"READ_ONLY".equals(normalized) && !"TRADE".equals(normalized) && !"FUNDING".equals(normalized)) {
            throw new IllegalArgumentException("unsupported permission scope: " + value);
        }
        return normalized;
    }

    private String normalizeIpAllowlistStatus(String value) {
        String normalized = value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
        if (!"NOT_CHECKED".equals(normalized)
                && !"PASSED".equals(normalized)
                && !"FAILED".equals(normalized)
                && !"UNKNOWN".equals(normalized)
                && !"SKIPPED".equals(normalized)) {
            throw new IllegalArgumentException("unsupported IP allowlist probe status: " + value);
        }
        return normalized;
    }

    private String sanitizeErrorCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        if (normalized.contains("SECRET")
                || normalized.contains("TOKEN")
                || normalized.contains("SIGNATURE=")
                || normalized.contains("API_KEY")
                || normalized.contains("PRIVATE")) {
            return "REDACTED_ERROR";
        }
        return normalized.replaceAll("[^A-Z0-9_]", "_");
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record ProbePreparation(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String actor,
            String reason,
            String requestId,
            String traceId,
            CredentialPermissionProbeSummary completedSummary
    ) {
    }
}
