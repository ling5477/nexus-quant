package com.guidinglight.nexusquant.account.application;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialVerificationResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        ExchangeAccountCredentialMaterial material = exchangeAccountCredentialRepository.findActiveMaterial(ownerUserId, exchangeAccountId)
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
        return exchangeAccountCredentialRepository.findActiveSummary(ownerUserId, exchangeAccountId)
                .orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(exchangeAccountId));
    }

    private void requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        exchangeAccountRepository.findByIdForOwner(ownerUserId, exchangeAccountId)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }
}
