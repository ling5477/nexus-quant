package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.observability.operational.*;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationalCriticalAlertMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    @org.junit.jupiter.api.AfterEach
    void closeRegistry() { registry.close(); }

    @Test
    void onlyPersistedCriticalAlertsCountAndTelemetryCannotChangeReturnOrException() {
        {
            var runs = mock(PaperTradingRunService.class);
            var reports = mock(PaperRunDailyReportRepository.class);
            var alerts = mock(PaperRunAlertRepository.class);
            var service = new PaperRunMonitorService(runs, reports, alerts,
                    new MicrometerOperationalObservation(registry, Clock.systemUTC()));
            var critical = command("CRITICAL");
            var returned = service.createAlert(critical);
            var captured = ArgumentCaptor.forClass(PaperRunAlert.class);
            verify(alerts).insert(captured.capture());
            assertSame(returned, captured.getValue());
            service.createAlert(command("HIGH"));
            assertEquals(1, registry.get("nq.operational.alerts").counter().count());
            RuntimeException failure = new IllegalStateException("synthetic insert failure");
            doThrow(failure).when(alerts).insert(any());
            assertSame(failure, assertThrows(IllegalStateException.class, () -> service.createAlert(critical)));
            assertEquals(1, registry.get("nq.operational.alerts").counter().count());
            var broken = new PaperRunMonitorService(runs, reports, alerts,
                    (op, signal, value) -> { throw new IllegalStateException("meter failure"); });
            assertSame(failure, assertThrows(IllegalStateException.class, () -> broken.createAlert(critical)));
            doNothing().when(alerts).insert(any());
            assertNotNull(broken.createAlert(critical));
            assertFalse(registry.getMeters().toString().contains("sensitive-fixture"));
        }
    }

    private PaperRunAlertCreateCommand command(String severity) {
        return new PaperRunAlertCreateCommand("run-sensitive-fixture", "type-sensitive-fixture", severity,
                "title-sensitive-fixture", "message-sensitive-fixture", "source-sensitive-fixture", "{}");
    }
}
