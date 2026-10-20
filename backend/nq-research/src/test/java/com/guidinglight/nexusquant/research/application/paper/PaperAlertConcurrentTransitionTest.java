package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

/** 模拟读取与条件写之间已有另一事务提交；真实SQL竞争由PG集成场景另行证明。 */
class PaperAlertConcurrentTransitionTest {
    private PaperRunAlert alert(PaperRunAlertStatus status) {
        Instant now = Instant.parse("2026-10-15T01:02:03Z");
        return new PaperRunAlert(
                "alt-one",
                "ptr-one",
                "SYSTEM_NOTICE",
                PaperRunAlertSeverity.LOW,
                status,
                "notice",
                null,
                "MANUAL",
                "{}",
                null,
                null,
                null,
                now,
                now);
    }

    @Test
    void lateAcknowledgementDoesNotClaimSuccessAfterAnotherResolverWins() {
        var repository = mock(PaperRunAlertRepository.class);
        when(repository.findById("alt-one"))
                .thenReturn(
                        Optional.of(alert(PaperRunAlertStatus.OPEN)),
                        Optional.of(alert(PaperRunAlertStatus.RESOLVED)));
        var monitor =
                new PaperRunMonitorService(
                        mock(PaperTradingRunService.class),
                        mock(PaperRunDailyReportRepository.class),
                        repository);
        assertThrows(
                IllegalStateException.class, () -> monitor.ackAlert("ptr-one", "alt-one", "owner"));
    }

    @Test
    void sameStateConcurrentWriterReturnsExistingOutcomeWithoutChangingActor() {
        var repository = mock(PaperRunAlertRepository.class);
        when(repository.findById("alt-one"))
                .thenReturn(
                        Optional.of(alert(PaperRunAlertStatus.OPEN)),
                        Optional.of(alert(PaperRunAlertStatus.ACKED)));
        when(repository.updateStatus(
                        eq("alt-one"), eq(PaperRunAlertStatus.ACKED), any(), any(), any(), any()))
                .thenReturn(false);
        var monitor =
                new PaperRunMonitorService(
                        mock(PaperTradingRunService.class),
                        mock(PaperRunDailyReportRepository.class),
                        repository);
        assertEquals(
                PaperRunAlertStatus.ACKED,
                monitor.ackAlert("ptr-one", "alt-one", "owner").status());
    }
}
