package com.guidinglight.nexusquant.scheduler.infra.lock;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL transaction-level advisory lock 的 scheduler 通用实现。
 *
 * <p>每次调用创建独立 {@code REQUIRES_NEW} read-only transaction，并在同一事务绑定连接上
 * 调用 {@code pg_try_advisory_xact_lock(int,int)}。成功取锁后 callback 在该事务内执行；
 * commit、rollback、SQL timeout、callback 异常或中断结束事务时 PostgreSQL 自动释放锁。
 * 本实现不调用 session-level lock/unlock，不创建表，也不包含任何 scheduler 业务逻辑。
 *
 * <p>线程安全：实例只持有不可变协作对象；每次调用独立创建 TransactionTemplate，避免动态
 * timeout 在并发调用间相互覆盖。副作用仅限 PostgreSQL transaction-level lock 状态。
 */
public final class PostgresAdvisorySchedulerExecutionLock implements SchedulerExecutionLock {

    static final Duration MAX_TIMEOUT = Duration.ofMinutes(5);
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final PostgresAdvisoryLockKeyMapper keyMapper;

    public PostgresAdvisorySchedulerExecutionLock(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this(jdbcTemplate, transactionManager, new PostgresAdvisoryLockKeyMapper());
    }

    PostgresAdvisorySchedulerExecutionLock(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            PostgresAdvisoryLockKeyMapper keyMapper
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.keyMapper = Objects.requireNonNull(keyMapper, "keyMapper must not be null");
    }

    /**
     * 非阻塞取得 transaction-level advisory lock，并在同一事务/物理连接语义内执行 callback。
     *
     * <p>callback 异常、timeout 与 interrupt 会标记事务 rollback-only，并以保留 cause 的
     * 非成功结果返回；数据库或事务基础设施异常同样不会伪装为成功。
     */
    @Override
    public <T> SchedulerLockExecution<T> executeWithLock(
            SchedulerLockKey key,
            Duration timeout,
            Supplier<T> action
    ) {
        Objects.requireNonNull(key, "key must not be null");
        Duration validatedTimeout = validateTimeout(timeout);
        Objects.requireNonNull(action, "action must not be null");

        if (Thread.currentThread().isInterrupted()) {
            return SchedulerLockExecution.interrupted(
                    new InterruptedException("thread interrupted before scheduler lock attempt")
            );
        }

        PostgresAdvisoryLockKey mappedKey = keyMapper.map(key);
        long startedNanos = System.nanoTime();
        TransactionTemplate transaction = newTransaction(validatedTimeout);
        try {
            SchedulerLockExecution<T> result = transaction.execute(status -> {
                Boolean acquired = jdbcTemplate.queryForObject(
                        TRY_LOCK_SQL,
                        Boolean.class,
                        mappedKey.namespaceKey(),
                        mappedKey.lockKey()
                );
                if (!Boolean.TRUE.equals(acquired)) {
                    return SchedulerLockExecution.notAcquired();
                }

                try {
                    T value = action.get();
                    if (Thread.currentThread().isInterrupted()) {
                        status.setRollbackOnly();
                        return SchedulerLockExecution.interrupted(
                                new InterruptedException("thread interrupted during scheduler lock action")
                        );
                    }
                    if (elapsedAtLeast(startedNanos, validatedTimeout)) {
                        status.setRollbackOnly();
                        return SchedulerLockExecution.timedOut(
                                new TimeoutException("scheduler lock action exceeded configured timeout")
                        );
                    }
                    return SchedulerLockExecution.completed(value);
                } catch (RuntimeException ex) {
                    status.setRollbackOnly();
                    if (isInterrupted(ex)) {
                        Thread.currentThread().interrupt();
                        return SchedulerLockExecution.interrupted(ex);
                    }
                    if (isTimeout(ex)) {
                        return SchedulerLockExecution.timedOut(ex);
                    }
                    return SchedulerLockExecution.actionFailed(ex);
                }
            });
            return Objects.requireNonNull(result, "transaction callback must return lock execution result");
        } catch (RuntimeException ex) {
            if (isInterrupted(ex)) {
                Thread.currentThread().interrupt();
                return SchedulerLockExecution.interrupted(ex);
            }
            if (isTimeout(ex)) {
                return SchedulerLockExecution.timedOut(ex);
            }
            return SchedulerLockExecution.actionFailed(ex);
        }
    }

    private TransactionTemplate newTransaction(Duration timeout) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(true);
        template.setTimeout(toTransactionTimeoutSeconds(timeout));
        return template;
    }

    private static Duration validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must not exceed " + MAX_TIMEOUT);
        }
        return timeout;
    }

    private static int toTransactionTimeoutSeconds(Duration timeout) {
        long millis = timeout.toMillis();
        long seconds = Math.max(1L, (millis + 999L) / 1_000L);
        return Math.toIntExact(seconds);
    }

    private static boolean elapsedAtLeast(long startedNanos, Duration timeout) {
        return System.nanoTime() - startedNanos >= timeout.toNanos();
    }

    private static boolean isTimeout(Throwable failure) {
        return hasCause(failure, TransactionTimedOutException.class)
                || hasCause(failure, QueryTimeoutException.class)
                || hasCause(failure, TimeoutException.class);
    }

    private static boolean isInterrupted(Throwable failure) {
        return Thread.currentThread().isInterrupted() || hasCause(failure, InterruptedException.class);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
