package com.guidinglight.nexusquant.account.application;

import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountUpdateCommand;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeAccountCommandServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-06T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateUpdateEnableDisableAndSwitchDefaultAccount() {
        InMemoryExchangeAccountRepository repository = new InMemoryExchangeAccountRepository();
        ExchangeAccountCommandService service = new ExchangeAccountCommandService(repository, fixedClock);

        ExchangeAccountSummary first = service.create(1L, new ExchangeAccountCreateCommand("okx", "sim", "demo-a", "acc-a"));
        ExchangeAccountSummary second = service.create(1L, new ExchangeAccountCreateCommand("okx", "sim", "demo-b", "acc-b"));

        ExchangeAccountSummary updated = service.updateProfile(1L, second.exchangeAccountId(), new ExchangeAccountUpdateCommand("demo-b-2", "acc-b-2"));
        assertEquals("demo-b-2", updated.accountAlias());
        assertEquals("acc-b-2", updated.externalAccountRef());

        ExchangeAccountSummary firstDefault = service.setDefault(1L, first.exchangeAccountId());
        assertTrue(firstDefault.isDefault());
        ExchangeAccountSummary secondDefault = service.setDefault(1L, second.exchangeAccountId());
        assertTrue(secondDefault.isDefault());
        assertFalse(repository.findByIdForOwner(1L, first.exchangeAccountId()).orElseThrow().isDefault());

        ExchangeAccountSummary disabled = service.disable(1L, second.exchangeAccountId());
        assertEquals("DISABLED", disabled.status());
        assertFalse(disabled.isDefault());

        ExchangeAccountSummary enabled = service.enable(1L, second.exchangeAccountId());
        assertEquals("ACTIVE", enabled.status());
    }

    @Test
    void shouldRejectSettingDisabledAccountAsDefault() {
        InMemoryExchangeAccountRepository repository = new InMemoryExchangeAccountRepository();
        ExchangeAccountCommandService service = new ExchangeAccountCommandService(repository, fixedClock);
        ExchangeAccountSummary summary = service.create(1L, new ExchangeAccountCreateCommand("OKX", "SIM", "demo-a", "acc-a"));
        service.disable(1L, summary.exchangeAccountId());

        assertThrows(IllegalStateException.class, () -> service.setDefault(1L, summary.exchangeAccountId()));
    }

    @Test
    void shouldTranslateConflictToStateConflict() {
        ConflictRepository repository = new ConflictRepository();
        ExchangeAccountCommandService service = new ExchangeAccountCommandService(repository, fixedClock);

        assertThrows(IllegalStateException.class, () -> service.create(1L, new ExchangeAccountCreateCommand("OKX", "SIM", "demo-a", "acc-a")));
    }

    private static class InMemoryExchangeAccountRepository implements ExchangeAccountRepository {
        private final Map<Long, ExchangeAccountSummary> storage = new LinkedHashMap<>();
        private long nextId = 1000L;

        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            return storage.values().stream().filter(item -> item.ownerUserId().equals(ownerUserId)).toList();
        }

        @Override
        public Optional<ExchangeAccountSummary> findById(Long exchangeAccountId) {
            return Optional.ofNullable(storage.get(exchangeAccountId));
        }

        @Override
        public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId) {
            ExchangeAccountSummary summary = storage.get(exchangeAccountId);
            return summary != null && summary.ownerUserId().equals(ownerUserId) ? Optional.of(summary) : Optional.empty();
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            return storage.values().stream().filter(item -> item.ownerUserId().equals(ownerUserId) && item.isDefault()).findFirst();
        }

        @Override
        public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) {
            assertUnique(null, ownerUserId, exchangeCode, tradeEnv, accountAlias, externalAccountRef);
            ExchangeAccountSummary summary = new ExchangeAccountSummary(++nextId, null, ownerUserId, exchangeCode, tradeEnv, accountAlias, externalAccountRef, false, "ACTIVE");
            storage.put(summary.exchangeAccountId(), summary);
            return summary;
        }

        @Override
        public boolean updateProfile(Long ownerUserId, Long exchangeAccountId, String accountAlias, String externalAccountRef, Instant now) {
            ExchangeAccountSummary current = storage.get(exchangeAccountId);
            if (current == null || !current.ownerUserId().equals(ownerUserId)) {
                return false;
            }
            assertUnique(exchangeAccountId, ownerUserId, current.exchangeCode(), current.tradeEnv(), accountAlias, externalAccountRef);
            storage.put(exchangeAccountId, new ExchangeAccountSummary(
                    current.exchangeAccountId(),
                    current.legacyAccountId(),
                    current.ownerUserId(),
                    current.exchangeCode(),
                    current.tradeEnv(),
                    accountAlias,
                    externalAccountRef,
                    current.isDefault(),
                    current.status()
            ));
            return true;
        }

        @Override
        public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            return replaceStatus(ownerUserId, exchangeAccountId, "ACTIVE", false);
        }

        @Override
        public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            return replaceStatus(ownerUserId, exchangeAccountId, "DISABLED", true);
        }

        @Override
        public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
            storage.replaceAll((id, current) -> current.ownerUserId().equals(ownerUserId)
                    && current.exchangeCode().equals(exchangeCode)
                    && current.tradeEnv().equals(tradeEnv)
                    && current.isDefault()
                    ? new ExchangeAccountSummary(id, current.legacyAccountId(), current.ownerUserId(), current.exchangeCode(), current.tradeEnv(), current.accountAlias(), current.externalAccountRef(), false, current.status())
                    : current);
        }

        @Override
        public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
            ExchangeAccountSummary current = storage.get(exchangeAccountId);
            if (current == null || !current.ownerUserId().equals(ownerUserId)) {
                return false;
            }
            storage.put(exchangeAccountId, new ExchangeAccountSummary(
                    current.exchangeAccountId(),
                    current.legacyAccountId(),
                    current.ownerUserId(),
                    current.exchangeCode(),
                    current.tradeEnv(),
                    current.accountAlias(),
                    current.externalAccountRef(),
                    true,
                    current.status()
            ));
            return true;
        }

        private boolean replaceStatus(Long ownerUserId, Long exchangeAccountId, String status, boolean clearDefault) {
            ExchangeAccountSummary current = storage.get(exchangeAccountId);
            if (current == null || !current.ownerUserId().equals(ownerUserId)) {
                return false;
            }
            storage.put(exchangeAccountId, new ExchangeAccountSummary(
                    current.exchangeAccountId(),
                    current.legacyAccountId(),
                    current.ownerUserId(),
                    current.exchangeCode(),
                    current.tradeEnv(),
                    current.accountAlias(),
                    current.externalAccountRef(),
                    clearDefault ? false : current.isDefault(),
                    status
            ));
            return true;
        }

        private void assertUnique(Long currentId, Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef) {
            boolean aliasConflict = storage.values().stream().anyMatch(item -> !item.exchangeAccountId().equals(currentId)
                    && item.ownerUserId().equals(ownerUserId)
                    && item.exchangeCode().equals(exchangeCode)
                    && item.tradeEnv().equals(tradeEnv)
                    && item.accountAlias().equals(accountAlias));
            boolean externalConflict = externalAccountRef != null && storage.values().stream().anyMatch(item -> !item.exchangeAccountId().equals(currentId)
                    && item.exchangeCode().equals(exchangeCode)
                    && item.tradeEnv().equals(tradeEnv)
                    && externalAccountRef.equals(item.externalAccountRef()));
            if (aliasConflict || externalConflict) {
                throw new DataIntegrityViolationException("conflict");
            }
        }
    }

    private static final class ConflictRepository extends InMemoryExchangeAccountRepository {
        @Override
        public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) {
            throw new DataIntegrityViolationException("uq_exchange_accounts_owner_exchange_env_alias");
        }
    }
}
