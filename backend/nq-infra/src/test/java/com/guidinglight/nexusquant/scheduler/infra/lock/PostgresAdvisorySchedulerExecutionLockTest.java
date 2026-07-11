package com.guidinglight.nexusquant.scheduler.infra.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 对数据库边界外的结果映射、callback 次数和 fail-fast timeout 做快速回归。 */
class PostgresAdvisorySchedulerExecutionLockTest {

    private static final SchedulerLockKey KEY = new SchedulerLockKey("test", "job");

    @Test
    void shouldNotRunActionWhenLockIsNotAcquired() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = transactionManager();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(false);
        AtomicInteger calls = new AtomicInteger();

        SchedulerLockExecution<String> result = lock(jdbcTemplate, transactionManager)
                .executeWithLock(KEY, Duration.ofSeconds(5), () -> {
                    calls.incrementAndGet();
                    return "unexpected";
                });

        assertEquals(SchedulerLockExecution.Status.NOT_ACQUIRED, result.status());
        assertEquals(0, calls.get());
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void shouldRunActionOnceAndReturnValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();

        SchedulerLockExecution<String> result = lock(jdbcTemplate, transactionManager())
                .executeWithLock(KEY, Duration.ofSeconds(5), () -> {
                    calls.incrementAndGet();
                    return "completed";
                });

        assertEquals(SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED, result.status());
        assertEquals("completed", result.value());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldPreserveActionFailureAndRollback() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = transactionManager();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("sanitized-test-failure");

        SchedulerLockExecution<String> result = lock(jdbcTemplate, transactionManager)
                .executeWithLock(KEY, Duration.ofSeconds(5), () -> {
                    throw failure;
                });

        assertEquals(SchedulerLockExecution.Status.ACTION_FAILED, result.status());
        assertSame(failure, result.failure());
        ArgumentCaptor<TransactionStatus> status = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transactionManager).commit(status.capture());
        assertTrue(status.getValue().isRollbackOnly());
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    @Test
    void shouldNotReportDatabaseFailureAsSuccessOrRunAction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "sanitized-database-failure"
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenThrow(failure);
        AtomicInteger calls = new AtomicInteger();

        SchedulerLockExecution<String> result = lock(jdbcTemplate, transactionManager())
                .executeWithLock(KEY, Duration.ofSeconds(5), () -> {
                    calls.incrementAndGet();
                    return "unexpected";
                });

        assertEquals(SchedulerLockExecution.Status.ACTION_FAILED, result.status());
        assertSame(failure, result.failure());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldFailFastForUnsafeTimeoutWithoutOpeningTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = transactionManager();
        PostgresAdvisorySchedulerExecutionLock lock = lock(jdbcTemplate, transactionManager);

        assertThrows(IllegalArgumentException.class, () -> lock.executeWithLock(KEY, Duration.ZERO, () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> lock.executeWithLock(
                KEY,
                PostgresAdvisorySchedulerExecutionLock.MAX_TIMEOUT.plusMillis(1),
                () -> "x"
        ));
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private static PostgresAdvisorySchedulerExecutionLock lock(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        return new PostgresAdvisorySchedulerExecutionLock(jdbcTemplate, transactionManager);
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }
}
