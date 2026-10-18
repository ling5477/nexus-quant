package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.application.StrategyRunRelationService;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;

import org.junit.jupiter.api.Test;

import java.util.List;

class StrategyRunRelationServiceTest {
    @Test
    void missingRunCannotReadUnscopedRiskOrLedgerFacts() {
        var service = new StrategyRunRelationService(new EmptyPort());
        var query = new StrategyRunRelatedQuery("missing", 20, 0);
        assertThrows(StrategyRunReadNotFoundException.class, () -> service.riskEvents(1L, query));
        assertThrows(
                StrategyRunReadNotFoundException.class, () -> service.businessEvents(1L, query));
        assertThrows(
                StrategyRunReadNotFoundException.class, () -> service.ledgerEntries(1L, query));
    }

    @Test
    void existingRunMayHaveNoOrderRiskOrTradePostingRecords() {
        var service = new StrategyRunRelationService(new EmptyPort());
        var query = new StrategyRunRelatedQuery("existing", 50, 100);
        assertEquals(0, service.riskEvents(1L, query).total());
        assertEquals(100, service.businessEvents(1L, query).offset());
        assertEquals(0, service.ledgerEntries(1L, query).total());
        assertEquals(100, service.ledgerEntries(1L, query).offset());
    }

    @Test
    void internalCallCannotDropOrSpoofTheOwnerBoundary() {
        var service = new StrategyRunRelationService(new EmptyPort());
        var query = new StrategyRunRelatedQuery("existing", 20, 0);
        assertThrows(IllegalArgumentException.class, () -> service.riskEvents(0L, query));
        assertThrows(IllegalArgumentException.class, () -> service.businessEvents(0L, query));
        assertThrows(IllegalArgumentException.class, () -> service.ledgerEntries(-1L, query));
    }

    private static final class EmptyPort implements StrategyRunRelationQueryPort {
        public StrategyRunRelatedPage<StrategyRunBusinessEventItem> readBusinessEvents(
                long owner, StrategyRunRelatedQuery query) {
            if (!query.strategyRunId().equals("existing"))
                throw new AssertionError("missing run reached event reader");
            return new StrategyRunRelatedPage<>(
                    query.strategyRunId(), List.of(), 0, query.limit(), query.offset());
        }

        public boolean visibleRunExists(long owner, String id) {
            return id.equals("existing");
        }

        public StrategyRunRelatedPage<StrategyRunRiskEventItem> readRiskEvents(
                long owner, StrategyRunRelatedQuery query) {
            if (!query.strategyRunId().equals("existing"))
                throw new AssertionError("missing run reached the risk reader");
            return new StrategyRunRelatedPage<>(
                    query.strategyRunId(), List.of(), 0, query.limit(), query.offset());
        }

        public StrategyRunRelatedPage<StrategyRunLedgerEntryItem> readLedgerEntries(
                long owner, StrategyRunRelatedQuery query) {
            if (!query.strategyRunId().equals("existing"))
                throw new AssertionError("missing run reached the ledger reader");
            return new StrategyRunRelatedPage<>(
                    query.strategyRunId(), List.of(), 0, query.limit(), query.offset());
        }
    }
}
