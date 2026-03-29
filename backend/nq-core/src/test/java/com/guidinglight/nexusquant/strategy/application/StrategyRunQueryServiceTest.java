package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunDetail;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunQueryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StrategyRunQueryServiceTest {

    @Test
    void shouldGetRunDetailWithOrderAndTradeSummaries() {
        InMemoryStrategyRunQueryRepository repository = new InMemoryStrategyRunQueryRepository();
        repository.runs.put("run-manual", manualRun("run-manual"));
        repository.orders.put("run-manual", List.of(new StrategyRunOrderSummary(
                "ord-1",
                "coid-1",
                "ex-ord-1",
                "ACCEPTED",
                "BTC-USDT",
                "BUY",
                "LIMIT",
                new BigDecimal("100.00"),
                new BigDecimal("0.01")
        )));
        repository.trades.put("run-manual", List.of(new StrategyRunTradeSummary(
                "trd-1",
                "ex-trd-1",
                "ex-ord-1",
                new BigDecimal("100.10"),
                new BigDecimal("0.01"),
                Instant.parse("2026-03-24T03:00:05Z")
        )));
        StrategyRunQueryService service = new StrategyRunQueryService(repository);

        StrategyRunDetail detail = service.getRunDetail("run-manual");

        assertEquals("run-manual", detail.strategyRunId());
        assertEquals("MANUAL", detail.triggerType());
        assertNull(detail.scheduleJobId());
        assertEquals(1, detail.executionResult().orders().size());
        assertEquals(1, detail.executionResult().trades().size());
        assertNotNull(detail.executionResult().ledgerSummary());
    }

    @Test
    void shouldListRecentRunsByStrategyAndScheduleUsingUnifiedRunView() {
        InMemoryStrategyRunQueryRepository repository = new InMemoryStrategyRunQueryRepository();
        repository.runs.put("run-schedule", scheduleRun("run-schedule", "sch-1"));
        repository.runs.put("run-manual", manualRun("run-manual"));
        StrategyRunQueryService service = new StrategyRunQueryService(repository);

        List<StrategyRunSummary> strategyRuns = service.listRecentRunsByStrategyId("str-1");
        List<StrategyRunSummary> scheduleRuns = service.listRecentRunsByScheduleJobId("sch-1");

        assertEquals(2, strategyRuns.size());
        assertEquals(1, scheduleRuns.size());
        assertEquals("SCHEDULED", scheduleRuns.getFirst().triggerType());
        assertEquals("sch-1", scheduleRuns.getFirst().scheduleJobId());
    }

    @Test
    void shouldFailWhenRunMissing() {
        StrategyRunQueryService service = new StrategyRunQueryService(new InMemoryStrategyRunQueryRepository());

        assertThrows(IllegalArgumentException.class, () -> service.getRunDetail("missing"));
    }

    private static StrategyRun manualRun(String runId) {
        return new StrategyRun(
                runId,
                "str-1",
                1001L,
                "BINANCE",
                "SIM",
                "MANUAL",
                StrategyRunStatus.RUNNING,
                "{}",
                "req-strategy-1",
                Instant.parse("2026-03-24T03:00:00Z"),
                null,
                null,
                "trc-manual"
        );
    }

    private static StrategyRun scheduleRun(String runId, String scheduleJobId) {
        return new StrategyRun(
                runId,
                "str-1",
                1001L,
                "BINANCE",
                "SIM",
                "MANUAL",
                StrategyRunStatus.RUNNING,
                "{}",
                "req-schedule-" + scheduleJobId + "-window-1774256460000",
                Instant.parse("2026-03-24T03:01:00Z"),
                null,
                null,
                "trc-schedule"
        );
    }

    private static final class InMemoryStrategyRunQueryRepository implements StrategyRunQueryRepository {
        private final Map<String, StrategyRun> runs = new LinkedHashMap<>();
        private final Map<String, List<StrategyRunOrderSummary>> orders = new LinkedHashMap<>();
        private final Map<String, List<StrategyRunTradeSummary>> trades = new LinkedHashMap<>();

        @Override
        public Optional<StrategyRun> findRunByStrategyRunId(String strategyRunId) {
            return Optional.ofNullable(runs.get(strategyRunId));
        }

        @Override
        public List<StrategyRun> listRecentRunsByStrategyId(String strategyId, int limit) {
            return runs.values().stream()
                    .filter(item -> item.strategyId().equals(strategyId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<StrategyRun> listRecentRunsByScheduleJobId(String scheduleJobId, int limit) {
            return runs.values().stream()
                    .filter(item -> item.requestId() != null && item.requestId().startsWith("req-schedule-" + scheduleJobId + "-"))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<StrategyRunOrderSummary> listOrderSummariesByStrategyRunId(String strategyRunId) {
            return orders.getOrDefault(strategyRunId, List.of());
        }

        @Override
        public List<StrategyRunTradeSummary> listTradeSummariesByStrategyRunId(String strategyRunId) {
            return trades.getOrDefault(strategyRunId, List.of());
        }
    }
}

