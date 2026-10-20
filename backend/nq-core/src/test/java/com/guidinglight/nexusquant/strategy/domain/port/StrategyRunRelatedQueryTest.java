package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.application.StrategyRunDetailService;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class StrategyRunRelatedQueryTest {
    @Test
    void requiresAnExactBoundedRunIdentityBeforeQuerying() {
        for (String id : new String[] {null, "", "run\t", "x".repeat(65)})
            assertThrows(
                    IllegalArgumentException.class, () -> new StrategyRunRelatedQuery(id, 20, 0));
        assertEquals(
                "run-%_!",
                new StrategyRunRelatedQuery(" run-%_! ", 100, Integer.MAX_VALUE).strategyRunId());
        assertThrows(
                IllegalArgumentException.class, () -> new StrategyRunRelatedQuery("run", 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new StrategyRunRelatedQuery("run", 101, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new StrategyRunRelatedQuery("run", 20, -1));
    }

    @Test
    void rejectsIncompleteChildWindowsButAcceptsARealEmptyTail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyRunRelatedPage<>("run", List.of(), 527, 20, 0));
        assertEquals(
                List.of(), new StrategyRunRelatedPage<>("run", List.of(), 527, 20, 600).items());
    }

    @Test
    void distinguishesMissingRunFromExistingRunWithNoChildren() {
        var service = new StrategyRunDetailService(new EmptyPort());
        assertThrows(StrategyRunReadNotFoundException.class, () -> service.overview("missing"));
        assertThrows(
                StrategyRunReadNotFoundException.class,
                () -> service.orders(new StrategyRunRelatedQuery("missing", 20, 0)));
        assertThrows(
                StrategyRunReadNotFoundException.class,
                () -> service.trades(new StrategyRunRelatedQuery("missing", 20, 0)));
        assertEquals(0, service.orders(new StrategyRunRelatedQuery("existing", 20, 0)).total());
        assertEquals(0, service.trades(new StrategyRunRelatedQuery("existing", 20, 0)).total());
    }

    private static final class EmptyPort implements StrategyRunDetailQueryPort {
        public Optional<StrategyRunOverview> findOverview(String id) {
            return Optional.empty();
        }

        public boolean runExists(String id) {
            return id.equals("existing");
        }

        public StrategyRunRelatedPage<StrategyRunOrderItem> readOrders(
                StrategyRunRelatedQuery query) {
            return new StrategyRunRelatedPage<>(
                    query.strategyRunId(), List.of(), 0, query.limit(), query.offset());
        }

        public StrategyRunRelatedPage<StrategyRunTradeItem> readTrades(
                StrategyRunRelatedQuery query) {
            return new StrategyRunRelatedPage<>(
                    query.strategyRunId(), List.of(), 0, query.limit(), query.offset());
        }
    }
}
