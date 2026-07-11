package com.guidinglight.nexusquant.scheduler.validationevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/** 验证 scheduler 的 default-disabled、advisory lock 映射、无 overlap 与锁后可重跑。 */
class ValidationEvidenceSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldSkipWithoutLockOrAggregateWhenDisabled() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        SchedulerExecutionLock lock = mock(SchedulerExecutionLock.class);
        ValidationEvidenceScheduler scheduler = scheduler(false, queryService, lock);

        ValidationEvidenceRefreshResult result = scheduler.runOnce();

        assertEquals(ValidationEvidenceRefreshResult.Result.SKIPPED_DISABLED, result.result());
        verify(queryService, never()).overview(anyString());
        verify(lock, never()).executeWithLock(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldSkipWithoutAggregateWhenLockNotAcquired() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        SchedulerExecutionLock lock = new FixedStatusTestLock(SchedulerLockExecution.Status.NOT_ACQUIRED);

        ValidationEvidenceRefreshResult result = scheduler(true, queryService, lock).runOnce();

        assertEquals(ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED, result.result());
        verify(queryService, never()).overview(anyString());
    }

    @Test
    void shouldCallAggregateOnceAndMapLockFailures() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview(anyString())).thenReturn(healthyOverview());
        SchedulerExecutionLock completed = new ExecutingTestLock();

        ValidationEvidenceRefreshResult success = scheduler(true, queryService, completed).runOnce();

        assertEquals(ValidationEvidenceRefreshResult.Result.SUCCESS, success.result());
        verify(queryService, times(1)).overview(anyString());

        assertEquals(
                ValidationEvidenceRefreshResult.FailureCategory.ACTION_FAILED,
                scheduler(true, queryService, new FixedStatusTestLock(
                        SchedulerLockExecution.Status.ACTION_FAILED
                )).runOnce().failureCategory()
        );
        assertEquals(
                ValidationEvidenceRefreshResult.FailureCategory.TIMED_OUT,
                scheduler(true, queryService, new FixedStatusTestLock(
                        SchedulerLockExecution.Status.TIMED_OUT
                )).runOnce().failureCategory()
        );
        assertEquals(
                ValidationEvidenceRefreshResult.FailureCategory.INTERRUPTED,
                scheduler(true, queryService, new FixedStatusTestLock(
                        SchedulerLockExecution.Status.INTERRUPTED
                )).runOnce().failureCategory()
        );
        verify(queryService, times(1)).overview(anyString());
    }

    @Test
    void shouldMapAggregateExceptionToFailedWithoutRetry() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview(anyString())).thenThrow(new IllegalStateException("query failed"));

        ValidationEvidenceRefreshResult result = scheduler(true, queryService, new ExecutingTestLock()).runOnce();

        assertEquals(ValidationEvidenceRefreshResult.Result.FAILED, result.result());
        assertEquals(ValidationEvidenceRefreshResult.FailureCategory.ACTION_FAILED, result.failureCategory());
        verify(queryService, times(1)).overview(anyString());
    }

    @Test
    void shouldPreventOverlapAndAllowSubsequentRun() throws Exception {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview(anyString())).thenReturn(healthyOverview());
        ContendedTestLock lock = new ContendedTestLock();
        ValidationEvidenceScheduler scheduler = scheduler(true, queryService, lock);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ValidationEvidenceRefreshResult> first = executor.submit(scheduler::runOnce);
            lock.awaitFirstAcquisition();

            ValidationEvidenceRefreshResult overlapping = scheduler.runOnce();
            assertEquals(
                    ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED,
                    overlapping.result()
            );
            verify(queryService, never()).overview(anyString());

            lock.releaseFirst();
            assertEquals(ValidationEvidenceRefreshResult.Result.SUCCESS, first.get(5, TimeUnit.SECONDS).result());
            assertEquals(ValidationEvidenceRefreshResult.Result.SUCCESS, scheduler.runOnce().result());
            verify(queryService, times(2)).overview(anyString());
        } finally {
            executor.shutdownNow();
        }
    }

    private static ValidationEvidenceScheduler scheduler(
            boolean enabled,
            ValidationOperationsRuntimeEvidenceOverviewQueryService queryService,
            SchedulerExecutionLock lock
    ) {
        ValidationEvidenceSchedulerProperties properties = new ValidationEvidenceSchedulerProperties();
        properties.setEnabled(enabled);
        properties.afterPropertiesSet();
        return new ValidationEvidenceScheduler(
                properties,
                new ValidationEvidenceRefreshService(queryService, CLOCK),
                lock,
                CLOCK
        );
    }

    private static ValidationOperationsRuntimeEvidenceOverviewReadModel healthyOverview() {
        ReadModelEvidenceMetadata metadata = metadata();
        List<RuntimeEvidenceSource> sources = List.of(
                new RuntimeEvidenceSource("ONE", "ONE", metadata),
                new RuntimeEvidenceSource("TWO", "TWO", metadata),
                new RuntimeEvidenceSource("THREE", "THREE", metadata),
                new RuntimeEvidenceSource("FOUR", "FOUR", metadata),
                new RuntimeEvidenceSource("FIVE", "FIVE", metadata)
        );
        return new ValidationOperationsRuntimeEvidenceOverviewReadModel(
                NOW,
                metadata,
                5,
                5,
                0,
                0,
                0,
                5,
                0,
                0,
                sources,
                "trc-test"
        );
    }

    private static ReadModelEvidenceMetadata metadata() {
        return new ReadModelEvidenceMetadata(
                "TEST",
                Availability.AVAILABLE,
                NOW.minusSeconds(10),
                FreshnessStatus.FRESH,
                10L,
                null,
                null,
                true,
                true,
                true,
                true
        );
    }

    private static final class ExecutingTestLock implements SchedulerExecutionLock {
        @Override
        public <T> SchedulerLockExecution<T> executeWithLock(
                SchedulerLockKey key,
                Duration timeout,
                Supplier<T> action
        ) {
            try {
                return SchedulerLockExecution.completed(action.get());
            } catch (RuntimeException ex) {
                return SchedulerLockExecution.actionFailed(ex);
            }
        }
    }

    private static final class FixedStatusTestLock implements SchedulerExecutionLock {
        private final SchedulerLockExecution.Status status;

        private FixedStatusTestLock(SchedulerLockExecution.Status status) {
            this.status = status;
        }

        @Override
        public <T> SchedulerLockExecution<T> executeWithLock(
                SchedulerLockKey key,
                Duration timeout,
                Supplier<T> action
        ) {
            return switch (status) {
                case NOT_ACQUIRED -> SchedulerLockExecution.notAcquired();
                case ACTION_FAILED -> SchedulerLockExecution.actionFailed(new IllegalStateException("failed"));
                case TIMED_OUT -> SchedulerLockExecution.timedOut(new IllegalStateException("timeout"));
                case INTERRUPTED -> SchedulerLockExecution.interrupted(new InterruptedException("interrupted"));
                case ACQUIRED_AND_COMPLETED -> throw new IllegalArgumentException("use ExecutingTestLock");
            };
        }
    }

    /** 仅用于测试 scheduler 是否遵守 lock contract；生产实现仍只使用 PostgreSQL advisory lock。 */
    private static final class ContendedTestLock implements SchedulerExecutionLock {
        private final Semaphore permit = new Semaphore(1);
        private final CountDownLatch firstAcquired = new CountDownLatch(1);
        private final CountDownLatch firstRelease = new CountDownLatch(1);

        @Override
        public <T> SchedulerLockExecution<T> executeWithLock(
                SchedulerLockKey key,
                Duration timeout,
                Supplier<T> action
        ) {
            if (!permit.tryAcquire()) {
                return SchedulerLockExecution.notAcquired();
            }
            try {
                if (firstAcquired.getCount() > 0) {
                    firstAcquired.countDown();
                    if (!firstRelease.await(5, TimeUnit.SECONDS)) {
                        return SchedulerLockExecution.timedOut(new IllegalStateException("test release timed out"));
                    }
                }
                return SchedulerLockExecution.completed(action.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return SchedulerLockExecution.interrupted(ex);
            } finally {
                permit.release();
            }
        }

        void awaitFirstAcquisition() throws InterruptedException {
            if (!firstAcquired.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first scheduler run did not acquire test lock");
            }
        }

        void releaseFirst() {
            firstRelease.countDown();
        }
    }
}
