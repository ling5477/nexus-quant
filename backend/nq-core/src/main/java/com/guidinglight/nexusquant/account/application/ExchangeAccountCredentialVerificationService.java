package com.guidinglight.nexusquant.account.application;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialVerificationResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

/**
 * ExchangeAccountCredentialVerificationService 提供 active 凭证结构性校验闭环。
 * <p>
 * Why:
 * RC1-4 首版虽然不要求真实外网探活，但 verification 状态流必须真实成立，
 * 因此成功/失败都必须回写到 active 凭证版本上，而不是只在 controller 返回文案。
 */
public class ExchangeAccountCredentialVerificationService {

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final ExchangeAccountCredentialRepository exchangeAccountCredentialRepository;
    private final ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier;
    private final Clock clock;

    public ExchangeAccountCredentialVerificationService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier
    ) {
        this(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                exchangeAccountCredentialVerifier,
                Clock.systemUTC()
        );
    }

    ExchangeAccountCredentialVerificationService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier,
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
        this.exchangeAccountCredentialVerifier = Objects.requireNonNull(
                exchangeAccountCredentialVerifier,
                "exchangeAccountCredentialVerifier must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ExchangeAccountCredentialSummary verifyActive(Long ownerUserId, Long exchangeAccountId) {
        return verifyActive(ownerUserId, exchangeAccountId, null);
    }

    /**
     * 校验 active credential，可通过 credentialType 消除多 ACTIVE type 歧义。
     *
     * <p>Why: 一个 account 允许多个 credential type 同时 ACTIVE。结构性校验会读取
     * decrypted payload，因此必须先明确唯一候选；无 credentialType 时多候选返回 409，
     * 不允许按 `updated_at` 静默选错。</p>
     */
    @Transactional
    public ExchangeAccountCredentialSummary verifyActive(Long ownerUserId, Long exchangeAccountId, String credentialType) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        String normalizedCredentialType = normalizeOptionalCredentialType(credentialType);
        ExchangeAccountCredentialMaterial material = findActiveMaterial(ownerUserId, exchangeAccountId, normalizedCredentialType)
                .orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(exchangeAccountId));
        Instant now = Instant.now(clock);
        ExchangeAccountCredentialVerificationResult verificationResult = exchangeAccountCredentialVerifier.verify(material);
        String verificationStatus = verificationResult.verified() ? "VERIFIED" : "FAILED";
        exchangeAccountCredentialRepository.markVerificationResult(
                material.credentialId(),
                verificationStatus,
                now,
                verificationResult.verified() ? null : verificationResult.errorMessage(),
                now
        );
        return findActiveSummary(ownerUserId, exchangeAccountId, normalizedCredentialType)
                .orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(exchangeAccountId));
    }

    private void requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        exchangeAccountRepository.findByIdForOwner(ownerUserId, exchangeAccountId)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }

    private Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        return credentialType == null
                ? exchangeAccountCredentialRepository.findActiveMaterial(ownerUserId, exchangeAccountId)
                : exchangeAccountCredentialRepository.findActiveMaterial(ownerUserId, exchangeAccountId, credentialType);
    }

    private Optional<ExchangeAccountCredentialSummary> findActiveSummary(
            Long ownerUserId,
            Long exchangeAccountId,
            String credentialType
    ) {
        return credentialType == null
                ? exchangeAccountCredentialRepository.findActiveSummary(ownerUserId, exchangeAccountId)
                : exchangeAccountCredentialRepository.findActiveSummary(ownerUserId, exchangeAccountId, credentialType);
    }

    private String normalizeOptionalCredentialType(String credentialType) {
        return credentialType == null || credentialType.isBlank() ? null : normalizeCredentialType(credentialType);
    }

    private String normalizeCredentialType(String credentialType) {
        String normalized = credentialType.trim().toUpperCase(Locale.ROOT);
        if (!"OKX_API_V5".equals(normalized)
                && !"BINANCE_HMAC".equals(normalized)
                && !"BINANCE_ED25519".equals(normalized)) {
            throw new IllegalArgumentException("unsupported credentialType: " + credentialType);
        }
        return normalized;
    }
}
