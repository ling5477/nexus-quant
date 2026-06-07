package com.guidinglight.nexusquant.account.application;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialVerificationResult;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeAccountCredentialVerificationServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-06T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldMarkCredentialVerifiedOnSuccess() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialVerificationService service = new ExchangeAccountCredentialVerificationService(
                accountRepository,
                credentialRepository,
                credential -> ExchangeAccountCredentialVerificationResult.success(),
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();
        credentialRepository.seed(account.exchangeAccountId());

        ExchangeAccountCredentialSummary summary = service.verifyActive(1L, account.exchangeAccountId());
        assertEquals("VERIFIED", summary.verificationStatus());
        assertEquals(fixedClock.instant(), summary.lastVerifiedAt());
    }

    @Test
    void shouldMarkCredentialFailedOnVerificationError() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialVerificationService service = new ExchangeAccountCredentialVerificationService(
                accountRepository,
                credentialRepository,
                credential -> ExchangeAccountCredentialVerificationResult.failed("signature invalid"),
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();
        credentialRepository.seed(account.exchangeAccountId());

        ExchangeAccountCredentialSummary summary = service.verifyActive(1L, account.exchangeAccountId());
        assertEquals("FAILED", summary.verificationStatus());
        assertEquals("signature invalid", summary.lastVerificationError());
    }

    @Test
    void shouldFailWhenNoActiveCredentialExists() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialVerificationService service = new ExchangeAccountCredentialVerificationService(
                accountRepository,
                credentialRepository,
                credential -> ExchangeAccountCredentialVerificationResult.success(),
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();

        assertThrows(ExchangeAccountCredentialNotFoundException.class, () -> service.verifyActive(1L, account.exchangeAccountId()));
    }

    private static final class InMemoryExchangeAccountRepository implements ExchangeAccountRepository {
        private final Map<Long, ExchangeAccountSummary> storage = new LinkedHashMap<>();
        private ExchangeAccountSummary seed() {
            ExchangeAccountSummary summary = new ExchangeAccountSummary(900001L, 900001L, 1L, "OKX", "SIM", "demo", null, true, "ACTIVE");
            storage.put(summary.exchangeAccountId(), summary);
            return summary;
        }
        @Override public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) { return storage.values().stream().toList(); }
        @Override public Optional<ExchangeAccountSummary> findById(Long exchangeAccountId) { return Optional.ofNullable(storage.get(exchangeAccountId)); }
        @Override public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId) { return Optional.ofNullable(storage.get(exchangeAccountId)); }
        @Override public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) { return storage.values().stream().filter(ExchangeAccountSummary::isDefault).findFirst(); }
        @Override public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) { throw new UnsupportedOperationException(); }
        @Override public boolean updateProfile(Long ownerUserId, Long exchangeAccountId, String accountAlias, String externalAccountRef, Instant now) { throw new UnsupportedOperationException(); }
        @Override public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) { throw new UnsupportedOperationException(); }
        @Override public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) { throw new UnsupportedOperationException(); }
        @Override public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) { throw new UnsupportedOperationException(); }
        @Override public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) { throw new UnsupportedOperationException(); }
    }

    private static final class InMemoryExchangeAccountCredentialRepository implements ExchangeAccountCredentialRepository {
        private final Map<Long, ExchangeAccountCredentialMaterial> storage = new LinkedHashMap<>();
        void seed(Long exchangeAccountId) {
            storage.put(1L, new ExchangeAccountCredentialMaterial(1L, exchangeAccountId, "OKX_API_V5", "tes***ey", "ACTIVE", "PENDING", true, null, null, null, null, null, Instant.parse("2026-04-05T00:00:00Z"), "{}"));
        }
        @Override public Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId) { return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.isActive() && "ACTIVE".equals(item.credentialStatus())).findFirst().map(ExchangeAccountCredentialMaterial::toSummary); }
        @Override public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType) { return Optional.empty(); }
        @Override public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId) { return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.isActive() && "ACTIVE".equals(item.credentialStatus())).findFirst(); }
        @Override public Optional<ExchangeAccountCredentialSummary> findByCredentialIdForOwner(Long ownerUserId, Long exchangeAccountId, Long credentialId) { return Optional.empty(); }
        @Override public void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt) { throw new UnsupportedOperationException(); }
        @Override public ExchangeAccountCredentialSummary insertNewVersion(Long exchangeAccountId, String credentialType, String encryptedPayloadJson, int keyVersion, String cipherSuite, String maskedAccessKey, Long rotatedFromCredentialId, Instant now) { throw new UnsupportedOperationException(); }
        @Override public boolean markVerificationResult(Long credentialId, String verificationStatus, Instant verifiedAt, String lastVerificationError, Instant updatedAt) {
            ExchangeAccountCredentialMaterial current = storage.get(credentialId);
            storage.put(credentialId, new ExchangeAccountCredentialMaterial(current.credentialId(), current.exchangeAccountId(), current.credentialType(), current.maskedAccessKey(), current.credentialStatus(), verificationStatus, current.isActive(), current.revokedAt(), current.rotatedFromCredentialId(), current.rotatedAt(), verifiedAt, lastVerificationError, updatedAt, current.decryptedPayloadJson()));
            return true;
        }
        @Override public boolean updateLifecycleStatus(Long credentialId, Long exchangeAccountId, String credentialStatus, boolean active, Instant revokedAt, String revokedBy, String revokeReason, Instant updatedAt) { throw new UnsupportedOperationException(); }
        @Override public void appendCredentialAuditLog(Long credentialId, Long exchangeAccountId, String eventType, String actor, String reason, String metadataJson, Instant createdAt) { throw new UnsupportedOperationException(); }
    }
}
