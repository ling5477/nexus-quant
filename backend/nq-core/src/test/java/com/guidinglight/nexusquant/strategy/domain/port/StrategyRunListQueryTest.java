package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.application.StrategyRunQueryService;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class StrategyRunListQueryTest {
    @Test
    void requiresOneExplicitReadScopeWithoutInterpretingIdentityWildcards() {
        assertThrows(IllegalArgumentException.class, () -> query(null, null, null, null, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> query("strategy", "schedule", null, null, 20, 0));
        assertEquals("%_!", query(" %_! ", null, null, null, 20, 0).strategyId());
        assertEquals("%_!", query(null, " %_! ", null, null, 20, 0).scheduleId());
    }

    @Test
    void acceptsActualStatusesAndAllSupportedTriggerRepresentations() {
        for (var status : StrategyRunStatus.values()) {
            assertEquals(
                    status.name(),
                    query("str", null, status.name().toLowerCase(), null, 1, 0).status());
        }
        for (String trigger : List.of("MANUAL", "SCHEDULED", "SCHEDULER", "RECOVERY")) {
            assertEquals(
                    trigger,
                    query("str", null, null, trigger.toLowerCase(), 100, Integer.MAX_VALUE)
                            .triggerType());
        }
    }

    @Test
    void rejectsControlsOversizedIdentityAndInvalidWindows() {
        for (String identity : List.of("str\t", "\n", "x".repeat(129))) {
            assertThrows(
                    IllegalArgumentException.class, () -> query(identity, null, null, null, 20, 0));
            assertThrows(
                    IllegalArgumentException.class, () -> query(null, identity, null, null, 20, 0));
        }
        assertThrows(
                IllegalArgumentException.class, () -> query("str", null, "STOPPED", null, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> query("str", null, null, "LIVE", 20, 0));
        assertThrows(IllegalArgumentException.class, () -> query("str", null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> query("str", null, null, null, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> query("str", null, null, null, 20, -1));
    }

    @Test
    void rejectsMissingRowsWhileAcceptingAnActualEmptyTail() {
        assertThrows(
                IllegalArgumentException.class, () -> new StrategyRunPage(List.of(), 527, 20, 0));
        assertEquals(List.of(), new StrategyRunPage(List.of(), 527, 20, 600).items());
    }

    @Test
    void exactDetailUsesTheExplicitAdmissionRelationshipBeforeLegacyRequestParsing() {
        var run =
                new StrategyRun(
                        "run",
                        "str",
                        1L,
                        "OKX",
                        "SIM",
                        "SCHEDULER",
                        StrategyRunStatus.CREATED,
                        "{}",
                        "req-schedule-legacy-window-1",
                        Instant.parse("2026-10-01T00:00:00Z"),
                        null,
                        null,
                        "trace",
                        "sch-%_!");
        var repository =
                new StrategyRunQueryRepository() {
                    public Optional<StrategyRun> findRunByStrategyRunId(String id) {
                        return Optional.of(run);
                    }

                    public List<StrategyRun> listRecentRunsByStrategyId(String id, int limit) {
                        return List.of(run);
                    }

                    public List<StrategyRun> listRecentRunsByScheduleJobId(String id, int limit) {
                        return List.of(run);
                    }

                    public List<StrategyRunOrderSummary> listOrderSummariesByStrategyRunId(
                            String id) {
                        return List.of();
                    }

                    public List<StrategyRunTradeSummary> listTradeSummariesByStrategyRunId(
                            String id) {
                        return List.of();
                    }
                };
        var detail = new StrategyRunQueryService(repository).getRunDetail("run");
        assertEquals("sch-%_!", detail.scheduleJobId());
        assertEquals("SCHEDULER", detail.triggerType());
    }

    private static StrategyRunListQuery query(
            String strategy,
            String schedule,
            String status,
            String trigger,
            int limit,
            int offset) {
        return new StrategyRunListQuery(strategy, schedule, status, trigger, limit, offset);
    }
}
