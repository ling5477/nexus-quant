package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeAccountCredentialCommandServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-06T02:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateInitialActiveCredentialAndMaskAccessKey() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialCommandService service = new ExchangeAccountCredentialCommandService(
                accountRepository,
                credentialRepository,
                objectMapper,
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();

        ExchangeAccountCredentialSummary summary = service.upsert(
                1L,
                account.exchangeAccountId(),
                new ExchangeAccountCredentialUpsertCommand("OKX_API_V5", "test-api-key", "secret", "pass", null),
                1
        );

        assertEquals("OKX_API_V5", summary.credentialType());
        assertEquals("tes***ey", summary.maskedAccessKey());
        assertEquals("PENDING", summary.verificationStatus());
        assertTrue(summary.isActive());
        assertNotNull(credentialRepository.findActiveMaterial(1L, account.exchangeAccountId()).orElseThrow());
    }

    @Test
    void shouldRotateActiveCredentialInsteadOfUpdatingInPlace() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialCommandService service = new ExchangeAccountCredentialCommandService(
                accountRepository,
                credentialRepository,
                objectMapper,
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();

        ExchangeAccountCredentialSummary first = service.upsert(
                1L,
                account.exchangeAccountId(),
                new ExchangeAccountCredentialUpsertCommand("BINANCE_HMAC", "first-key", "secret-1", null, null),
                1
        );
        ExchangeAccountCredentialSummary second = service.upsert(
                1L,
                account.exchangeAccountId(),
                new ExchangeAccountCredentialUpsertCommand("BINANCE_HMAC", "second-key", "secret-2", null, null),
                2
        );

        assertTrue(second.isActive());
        assertEquals(first.credentialId(), second.rotatedFromCredentialId());
        ExchangeAccountCredentialSummary revoked = credentialRepository.findByCredentialId(first.credentialId()).orElseThrow();
        assertFalse(revoked.isActive());
        assertEquals("REVOKED", revoked.verificationStatus());
    }

    @Test
    void shouldRejectInvalidPayloadForCredentialType() {
        InMemoryExchangeAccountRepository accountRepository = new InMemoryExchangeAccountRepository();
        InMemoryExchangeAccountCredentialRepository credentialRepository = new InMemoryExchangeAccountCredentialRepository();
        ExchangeAccountCredentialCommandService service = new ExchangeAccountCredentialCommandService(
                accountRepository,
                credentialRepository,
                objectMapper,
                fixedClock
        );
        ExchangeAccountSummary account = accountRepository.seed();

        assertThrows(IllegalArgumentException.class, () -> service.upsert(
                1L,
                account.exchangeAccountId(),
                new ExchangeAccountCredentialUpsertCommand("BINANCE_ED25519", "api", null, null, null),
                1
        ));
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
        private long nextId = 1L;

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId) {
            return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.isActive()).map(ExchangeAccountCredentialMaterial::toSummary).findFirst();
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType) {
            return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.credentialType().equals(credentialType) && item.isActive()).map(ExchangeAccountCredentialMaterial::toSummary).findFirst();
        }

        @Override
        public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId) {
            return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.isActive()).findFirst();
        }

        @Override
        public void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt) {
            storage.replaceAll((id, current) -> current.exchangeAccountId().equals(exchangeAccountId) && current.credentialType().equals(credentialType) && current.isActive()
                    ? new ExchangeAccountCredentialMaterial(current.credentialId(), current.exchangeAccountId(), current.credentialType(), current.maskedAccessKey(), "REVOKED", false, current.rotatedFromCredentialId(), current.lastVerifiedAt(), current.lastVerificationError(), revokedAt, current.decryptedPayloadJson())
                    : current);
        }

        @Override
        public ExchangeAccountCredentialSummary insertNewVersion(Long exchangeAccountId, String credentialType, String encryptedPayloadJson, int keyVersion, String cipherSuite, String maskedAccessKey, Long rotatedFromCredentialId, Instant now) {
            ExchangeAccountCredentialMaterial material = new ExchangeAccountCredentialMaterial(++nextId, exchangeAccountId, credentialType, maskedAccessKey, "PENDING", true, rotatedFromCredentialId, null, null, now, encryptedPayloadJson);
            storage.put(material.credentialId(), material);
            return material.toSummary();
        }

        @Override
        public boolean markVerificationResult(Long credentialId, String verificationStatus, Instant verifiedAt, String lastVerificationError, Instant updatedAt) {
            ExchangeAccountCredentialMaterial current = storage.get(credentialId);
            if (current == null) {
                return false;
            }
            storage.put(credentialId, new ExchangeAccountCredentialMaterial(current.credentialId(), current.exchangeAccountId(), current.credentialType(), current.maskedAccessKey(), verificationStatus, current.isActive(), current.rotatedFromCredentialId(), verifiedAt, lastVerificationError, updatedAt, current.decryptedPayloadJson()));
            return true;
        }

        private Optional<ExchangeAccountCredentialSummary> findByCredentialId(Long credentialId) {
            return Optional.ofNullable(storage.get(credentialId)).map(ExchangeAccountCredentialMaterial::toSummary);
        }
    }
}
