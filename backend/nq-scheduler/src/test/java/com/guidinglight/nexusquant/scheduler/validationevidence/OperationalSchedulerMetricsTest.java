package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.observability.operational.*;
import com.guidinglight.nexusquant.scheduler.lock.*;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationalSchedulerMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    @org.junit.jupiter.api.AfterEach
    void closeRegistry() { registry.close(); }

    @Test
    void observesSuccessFailureDegradationAndSkipWithoutChangingLockResults() {
        {
            var observation = new MicrometerOperationalObservation(registry, Clock.systemUTC());
            var properties = new ValidationEvidenceSchedulerProperties();
            properties.setEnabled(true);
            var refresh = mock(ValidationEvidenceRefreshService.class);
            var lock = mock(SchedulerExecutionLock.class);
            var service = new ValidationEvidenceScheduler(properties, refresh, lock, Clock.systemUTC(), observation);
            var success = result(ValidationEvidenceRefreshResult.Result.SUCCESS);
            when(lock.executeWithLock(any(), any(), any())).thenAnswer(invocation ->
                    SchedulerLockExecution.completed(((Supplier<?>) invocation.getArgument(2)).get()));
            when(refresh.refresh(any())).thenReturn(success);
            assertSame(success, service.runOnce());
            verify(refresh, times(1)).refresh(any());
            var degraded = result(ValidationEvidenceRefreshResult.Result.DEGRADED);
            when(refresh.refresh(any())).thenReturn(degraded);
            assertSame(degraded, service.runOnce());
            doReturn(SchedulerLockExecution.actionFailed(new IllegalStateException("synthetic failure")))
                    .when(lock).executeWithLock(any(), any(), any());
            assertEquals(ValidationEvidenceRefreshResult.Result.FAILED, service.runOnce().result());
            when(lock.executeWithLock(any(), any(), any())).thenReturn(SchedulerLockExecution.notAcquired());
            assertEquals(ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED, service.runOnce().result());
            for (String outcome : new String[]{"success", "failure", "degraded", "skipped"}) {
                assertEquals(1, registry.get("nq.operational.executions").tags("operation", "validation_refresh", "result", outcome).counter().count());
            }
            verify(refresh, times(2)).refresh(any());
            var broken = new ValidationEvidenceScheduler(properties, refresh, lock, Clock.systemUTC(),
                    (op, signal, value) -> { throw new IllegalStateException("meter failure"); });
            assertEquals(ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED, broken.runOnce().result());
            when(lock.executeWithLock(any(), any(), any())).thenThrow(new IllegalStateException("lock invocation failure"));
            assertEquals(ValidationEvidenceRefreshResult.FailureCategory.LOCK_INVOCATION_FAILED, broken.runOnce().failureCategory());
        }
    }

    private ValidationEvidenceRefreshResult result(ValidationEvidenceRefreshResult.Result result) {
        return new ValidationEvidenceRefreshResult(Instant.EPOCH, Instant.EPOCH, 0, Availability.AVAILABLE,
                FreshnessStatus.FRESH, Instant.EPOCH, 0, 0, result, null, true, true, true, true);
    }
}
