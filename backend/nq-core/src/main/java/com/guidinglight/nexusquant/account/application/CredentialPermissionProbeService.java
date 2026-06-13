package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.CredentialPermissionProbeCommand;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
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

import org.springframework.transaction.annotation.Transactional;

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

    public CredentialPermissionProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            ExchangeCredentialPermissionProbePort permissionProbePort,
            ObjectMapper objectMapper
    ) {
        this(exchangeAccountRepository, credentialRepository, permissionProbePort, objectMapper, Clock.systemUTC());
    }

    CredentialPermissionProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            ExchangeCredentialPermissionProbePort permissionProbePort,
            ObjectMapper objectMapper,
            Clock clock
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
    }

    /**
     * 触发一次同步 permission probe。
     *
     * <p>事务/并发：本轮不访问真实交易所，使用 credential row lock + IN_PROGRESS claim 防止重复 probe。
     * 后续如果接入真实 adapter，应把 port 调用拆出长事务，只保留 claim/finish 两阶段短事务。</p>
     *
     * @param ownerUserId 当前认证用户 ID，必须拥有 exchangeAccountId
     * @param exchangeAccountId 凭证所属账户
     * @param credentialId 从路径派生的 credentialId，credentialType 也从该记录派生
     * @param actor 当前认证主体；为空时落为 system
     * @param command 非敏感控制输入；不得包含 credential material
     * @param traceId 当前请求 trace id，可空
     * @return 脱敏 latest summary，不包含 credential material 或 raw exchange response
     */
    @Transactional
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
        ExchangeAccountSummary account = requireOwnedAccount(normalizedOwnerUserId, normalizedExchangeAccountId);
        String normalizedActor = normalizeActor(actor);
        String normalizedReason = normalizeReason(command == null ? null : command.reason());
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);

        ExchangeAccountCredentialMaterial locked = credentialRepository.findByCredentialIdForOwnerForUpdate(
                normalizedOwnerUserId,
                normalizedExchangeAccountId,
                normalizedCredentialId
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(normalizedExchangeAccountId));

        if ("IN_PROGRESS".equals(locked.permissionProbeStatus())) {
            appendAudit(locked, "PERMISSION_PROBE_SKIPPED", normalizedActor, normalizedReason,
                    "IN_PROGRESS_CONFLICT", locked.permissionProbeStatus(), "IN_PROGRESS", requestId, traceId, false);
            throw new IllegalStateException("credential permission probe already in progress");
        }
        if (!locked.isActive() || !"ACTIVE".equals(locked.credentialStatus())) {
            return skip(account, locked, normalizedActor, normalizedReason, "CREDENTIAL_NOT_ACTIVE", requestId, traceId, now);
        }
        if ("LIVE".equalsIgnoreCase(account.tradeEnv())) {
            return skip(account, locked, normalizedActor, normalizedReason, "LIVE_CREDENTIAL_BLOCKED", requestId, traceId, now);
        }
        if (locked.withdrawEnabled()) {
            return skip(account, locked, normalizedActor, normalizedReason, "WITHDRAW_ENABLED_RISK", requestId, traceId, now);
        }
        if (!paperSafetyGatePassed(command)) {
            return skip(account, locked, normalizedActor, normalizedReason, "PAPER_SAFETY_GATE_MISSING", requestId, traceId, now);
        }
        if (!credentialRepository.markPermissionProbeInProgress(normalizedCredentialId, normalizedExchangeAccountId, now)) {
            appendAudit(locked, "PERMISSION_PROBE_SKIPPED", normalizedActor, normalizedReason,
                    "IN_PROGRESS_CONFLICT", locked.permissionProbeStatus(), "IN_PROGRESS", requestId, traceId, false);
            throw new IllegalStateException("credential permission probe already in progress");
        }
        appendAudit(locked, "PERMISSION_PROBE_STARTED", normalizedActor, normalizedReason,
                null, locked.permissionProbeStatus(), "IN_PROGRESS", requestId, traceId, false);

        Instant startedAt = Instant.now(clock);
        ExchangeCredentialPermissionProbeResult result = permissionProbePort.probe(new ExchangeCredentialPermissionProbeRequest(
                account.exchangeAccountId(),
                locked.credentialId(),
                account.exchangeCode(),
                account.tradeEnv(),
                locked.credentialType(),
                normalizeMode(command == null ? null : command.mode()),
                Boolean.TRUE.equals(command == null ? null : command.dryRun()),
                traceId,
                locked.decryptedPayloadJson()
        ));
        Instant finishedAt = result.finishedAt() == null ? Instant.now(clock) : result.finishedAt();
        String finalStatus = normalizeProbeStatus(result.permissionProbeStatus());
        String errorCategory = sanitizeErrorCategory(result.sanitizedErrorCategory());
        boolean incrementFailedAuthCount = shouldIncrementFailedAuthCount(errorCategory);
        credentialRepository.markPermissionProbeResult(
                locked.credentialId(),
                locked.exchangeAccountId(),
                finalStatus,
                normalizePermissionScope(result.detectedPermissionScope()),
                normalizeIpAllowlistStatus(result.ipAllowlistProbeStatus()),
                finishedAt,
                errorCategory,
                incrementFailedAuthCount,
                finishedAt
        );
        appendAudit(locked, eventTypeFor(finalStatus), normalizedActor, normalizedReason,
                errorCategory, "IN_PROGRESS", finalStatus, emptyToDefault(result.requestId(), requestId),
                emptyToDefault(result.traceId(), traceId), incrementFailedAuthCount,
                normalizePermissionScope(result.detectedPermissionScope()),
                normalizeIpAllowlistStatus(result.ipAllowlistProbeStatus()));
        return latest(normalizedOwnerUserId, normalizedExchangeAccountId, normalizedCredentialId,
                emptyToDefault(result.requestId(), requestId), emptyToDefault(result.traceId(), traceId));
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
            ExchangeAccountCredentialMaterial credential,
            String actor,
            String reason,
            String policyDecision,
            String requestId,
            String traceId,
            Instant now
    ) {
        credentialRepository.markPermissionProbeResult(
                credential.credentialId(),
                credential.exchangeAccountId(),
                "SKIPPED",
                credential.permissionScope(),
                "SKIPPED",
                now,
                policyDecision,
                false,
                now
        );
        appendAudit(credential, "PERMISSION_PROBE_SKIPPED", actor, reason, policyDecision,
                credential.permissionProbeStatus(), "SKIPPED", requestId, traceId, false);
        ExchangeAccountCredentialSummary latestCredential = credentialRepository.findByCredentialIdForOwner(
                account.ownerUserId(),
                account.exchangeAccountId(),
                credential.credentialId()
        ).orElse(credential.toSummary());
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
                || "IP_ALLOWLIST_FAILED".equals(errorCategory);
    }

    private void appendAudit(
            ExchangeAccountCredentialMaterial credential,
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
            ExchangeAccountCredentialMaterial credential,
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
            ExchangeAccountCredentialMaterial credential,
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
}
