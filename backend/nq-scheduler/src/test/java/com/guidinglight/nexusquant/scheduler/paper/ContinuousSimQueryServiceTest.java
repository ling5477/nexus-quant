package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 核对只读入口的对象权限先行、空游标与原始轮询事实；真实数据库另行证明。 */
class ContinuousSimQueryServiceTest {
    private final ContinuousSimRepository progress = mock(ContinuousSimRepository.class);
    private final StrategySimRunService sim = mock(StrategySimRunService.class);
    private final PaperTradingRunService runs = mock(PaperTradingRunService.class);
    private final ContinuousSimQueryService service =
            new ContinuousSimQueryService(progress, sim, runs);

    @Test
    void inaccessibleRunDoesNotReadCursorOrDecisions() {
        when(runs.getById("hidden")).thenThrow(new IllegalArgumentException("PAPER_RUN_NOT_FOUND"));
        assertThrows(IllegalArgumentException.class, () -> service.find("hidden"));
        verifyNoInteractions(progress, sim);
    }

    @Test
    void visibleOrdinaryRunReturnsAbsenceRatherThanFabricatedStoppedState() {
        when(progress.exists("paper")).thenReturn(false);
        assertNull(service.find("paper"));
        var order = inOrder(runs, progress);
        order.verify(runs).getById("paper");
        order.verify(progress).exists("paper");
        verifyNoMoreInteractions(progress);
        verifyNoInteractions(sim);
    }

    @Test
    void savedStalledCursorIsReadableWithoutAnyExecutionDriver() {
        Instant time = Instant.parse("2026-10-15T00:00:00Z");
        when(progress.exists("paper")).thenReturn(true);
        when(progress.get("paper"))
                .thenReturn(
                        new ContinuousSimRepository.Cursor(
                                "paper",
                                "STALLED",
                                "v",
                                "a".repeat(64),
                                "b".repeat(64),
                                time,
                                time,
                                time.plusSeconds(3600),
                                "c".repeat(64),
                                time.plusSeconds(7200),
                                time.plusSeconds(3600),
                                time.minusSeconds(60),
                                time.plusSeconds(8),
                                null,
                                time.plusSeconds(8),
                                "EXPECTED_BAR_MISSING",
                                3));
        when(sim.decisions("paper")).thenReturn(List.of());
        var result = service.find("paper");
        assertEquals("STALLED", result.status());
        assertEquals(time, result.lastProcessedBar());
        assertEquals(time.plusSeconds(3600), result.nextExpectedBar());
        assertEquals(time.plusSeconds(7200), result.lastObservedBar());
        assertEquals(time.plusSeconds(3600), result.gapStartBar());
        assertEquals(3, result.consecutivePollFailures());
        assertNull(result.lastSuccessfulPollAt());
        assertNull(result.lastDecisionId());
        verify(sim).decisions("paper");
        verifyNoMoreInteractions(sim);
    }
}
