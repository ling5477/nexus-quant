package com.guidinglight.nexusquant.account.application;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExchangeAccountQueryServiceTest {

    @Test
    void shouldReturnEmptyWhenOwnerUserIdIsInvalid() {
        RecordingRepository repository = new RecordingRepository();
        ExchangeAccountQueryService service = new ExchangeAccountQueryService(repository);

        assertEquals(List.of(), service.listByOwnerUserId(null));
        assertEquals(List.of(), service.listByOwnerUserId(0L));
        assertEquals(Optional.empty(), service.findDefaultByOwnerUserId(null));
        assertEquals(Optional.empty(), service.findDefaultByOwnerUserId(-1L));
        assertEquals(0, repository.listCalls);
        assertEquals(0, repository.defaultCalls);
    }

    @Test
    void shouldDelegateQueriesToRepositoryWhenOwnerUserIdIsValid() {
        ExchangeAccountSummary summary = new ExchangeAccountSummary(
                900001L,
                900001L,
                1L,
                "OKX",
                "SIM",
                "rc1-admin-default",
                null,
                true,
                "ACTIVE"
        );
        RecordingRepository repository = new RecordingRepository(summary);
        ExchangeAccountQueryService service = new ExchangeAccountQueryService(repository);

        assertEquals(List.of(summary), service.listByOwnerUserId(1L));
        assertEquals(1, repository.listCalls);
        assertEquals(1L, repository.lastOwnerUserId);
        assertSame(summary, service.findDefaultByOwnerUserId(1L).orElseThrow());
        assertEquals(1, repository.defaultCalls);
    }

    private static final class RecordingRepository implements ExchangeAccountRepository {

        private final ExchangeAccountSummary summary;
        private long lastOwnerUserId = -1L;
        private int listCalls;
        private int defaultCalls;

        private RecordingRepository() {
            this.summary = null;
        }

        private RecordingRepository(ExchangeAccountSummary summary) {
            this.summary = summary;
        }

        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            listCalls++;
            lastOwnerUserId = ownerUserId;
            return summary == null ? List.of() : List.of(summary);
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            defaultCalls++;
            lastOwnerUserId = ownerUserId;
            return Optional.ofNullable(summary);
        }
    }
}
