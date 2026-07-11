package com.guidinglight.nexusquant.scheduler.infra.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 使用真实 PostgreSQL 与独立事务/连接验证 transaction-level advisory lock 多实例语义。
 *
 * <p>测试沿用 CI PostgreSQL smoke properties；未显式启用时可跳过，CI required 模式缺少配置则失败。
 * 全程只调用 advisory lock 与 catalog SELECT，不建表、不写行、不启动 scheduler 或业务服务。
 */
class PostgresAdvisorySchedulerExecutionLockPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";

    @Test
    void shouldEnforceMultiInstanceMutualExclusionAndAutomaticRelease() throws Exception {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL advisory lock integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        PostgresAdvisorySchedulerExecutionLock instanceOne = new PostgresAdvisorySchedulerExecutionLock(
                jdbcTemplate,
                new DataSourceTransactionManager(dataSource)
        );
        PostgresAdvisorySchedulerExecutionLock instanceTwo = new PostgresAdvisorySchedulerExecutionLock(
                new JdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource)
        );

        String suffix = UUID.randomUUID().toString();
        SchedulerLockKey sharedKey = new SchedulerLockKey("gatev3a-test", "shared-" + suffix);
        SchedulerLockKey differentKey = new SchedulerLockKey("gatev3a-test", "different-" + suffix);
        int schedulerLockTablesBefore = schedulerLockTableCount(jdbcTemplate);

        assertSameKeyContentionAndCommitRelease(instanceOne, instanceTwo, sharedKey);
        assertDifferentKeysCanRunConcurrently(instanceOne, instanceTwo, sharedKey, differentKey);
        assertExceptionRollbackReleases(instanceOne, instanceTwo, sharedKey);
        assertTimeoutRollbackReleases(instanceOne, instanceTwo, sharedKey);
        assertInterruptionReleases(instanceOne, instanceTwo, sharedKey);

        assertEquals(schedulerLockTablesBefore, schedulerLockTableCount(jdbcTemplate));
    }

    private static void assertSameKeyContentionAndCommitRelease(
            PostgresAdvisorySchedulerExecutionLock first,
            PostgresAdvisorySchedulerExecutionLock second,
            SchedulerLockKey key
    ) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger blockedCalls = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SchedulerLockExecution<String>> holding = executor.submit(() -> first.executeWithLock(
                    key,
                    Duration.ofSeconds(10),
                    () -> {
                        firstCalls.incrementAndGet();
                        entered.countDown();
                        await(release);
                        return "first";
                    }
            ));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            SchedulerLockExecution<String> contended = second.executeWithLock(
                    key,
                    Duration.ofSeconds(5),
                    () -> {
                        blockedCalls.incrementAndGet();
                        return "blocked";
                    }
            );
            assertEquals(SchedulerLockExecution.Status.NOT_ACQUIRED, contended.status());
            assertEquals(0, blockedCalls.get());

            release.countDown();
            assertEquals(SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED, holding.get(5, TimeUnit.SECONDS).status());
        }
        assertEquals(1, firstCalls.get());
        assertEquals(
                SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED,
                second.executeWithLock(key, Duration.ofSeconds(5), () -> "after-commit").status()
        );
    }

    private static void assertDifferentKeysCanRunConcurrently(
            PostgresAdvisorySchedulerExecutionLock first,
            PostgresAdvisorySchedulerExecutionLock second,
            SchedulerLockKey firstKey,
            SchedulerLockKey secondKey
    ) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SchedulerLockExecution<String>> holding = executor.submit(() -> first.executeWithLock(
                    firstKey,
                    Duration.ofSeconds(10),
                    () -> {
                        entered.countDown();
                        await(release);
                        return "first-key";
                    }
            ));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            SchedulerLockExecution<String> other = second.executeWithLock(
                    secondKey,
                    Duration.ofSeconds(5),
                    () -> "second-key"
            );
            assertEquals(SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED, other.status());
            release.countDown();
            assertEquals(SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED, holding.get(5, TimeUnit.SECONDS).status());
        }
    }

    private static void assertExceptionRollbackReleases(
            PostgresAdvisorySchedulerExecutionLock first,
            PostgresAdvisorySchedulerExecutionLock second,
            SchedulerLockKey key
    ) {
        IllegalStateException failure = new IllegalStateException("sanitized-integration-failure");
        SchedulerLockExecution<String> failed = first.executeWithLock(key, Duration.ofSeconds(5), () -> {
            throw failure;
        });
        assertEquals(SchedulerLockExecution.Status.ACTION_FAILED, failed.status());
        assertSame(failure, failed.failure());
        assertEquals(
                SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED,
                second.executeWithLock(key, Duration.ofSeconds(5), () -> "after-rollback").status()
        );
    }

    private static void assertTimeoutRollbackReleases(
            PostgresAdvisorySchedulerExecutionLock first,
            PostgresAdvisorySchedulerExecutionLock second,
            SchedulerLockKey key
    ) {
        SchedulerLockExecution<String> timedOut = first.executeWithLock(key, Duration.ofMillis(50), () -> {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            return "late";
        });
        assertEquals(SchedulerLockExecution.Status.TIMED_OUT, timedOut.status());
        assertEquals(
                SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED,
                second.executeWithLock(key, Duration.ofSeconds(5), () -> "after-timeout").status()
        );
    }

    private static void assertInterruptionReleases(
            PostgresAdvisorySchedulerExecutionLock first,
            PostgresAdvisorySchedulerExecutionLock second,
            SchedulerLockKey key
    ) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SchedulerLockExecution<String>> future = executor.submit(() -> first.executeWithLock(
                    key,
                    Duration.ofSeconds(10),
                    () -> {
                        worker.set(Thread.currentThread());
                        entered.countDown();
                        LockSupport.parkNanos(Duration.ofSeconds(10).toNanos());
                        return "interrupted";
                    }
            ));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            worker.get().interrupt();
            assertEquals(SchedulerLockExecution.Status.INTERRUPTED, future.get(5, TimeUnit.SECONDS).status());
        }
        assertEquals(
                SchedulerLockExecution.Status.ACQUIRED_AND_COMPLETED,
                second.executeWithLock(key, Duration.ofSeconds(5), () -> "after-interrupt").status()
        );
    }

    private static int schedulerLockTableCount(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relkind = 'r' AND relname = 'scheduler_lock'",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test action interrupted", ex);
        }
    }

    private record SmokeConfig(String url, String user, String password, boolean required) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}
