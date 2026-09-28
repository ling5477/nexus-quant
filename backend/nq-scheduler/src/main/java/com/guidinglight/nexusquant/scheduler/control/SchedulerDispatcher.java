package com.guidinglight.nexusquant.scheduler.control;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 唯一周期业务入口；每轮最多处理二十个固定任务，DB 开关不能替代静态能力门槛。 */
@Component
@ConditionalOnProperty(name = "nq.runtime.provider-observation.enabled", havingValue = "false", matchIfMissing = true)
public class SchedulerDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SchedulerDispatcher.class);
    private static final int MAX_JOBS_PER_TICK = 20;
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(5);

    private final ScheduledJobRegistry registry;
    private final ScheduledJobControlRepository controls;
    private final SchedulerExecutionLock executionLock;
    private final TransactionTemplate suspendedTransaction;
    private final Clock clock;
    private volatile boolean ready;

    @Autowired
    public SchedulerDispatcher(ScheduledJobRegistry registry, ScheduledJobControlRepository controls,
            SchedulerExecutionLock executionLock, PlatformTransactionManager transactionManager) {
        this(registry, controls, executionLock, transactionManager, Clock.systemUTC());
    }

    SchedulerDispatcher(ScheduledJobRegistry registry, ScheduledJobControlRepository controls,
            SchedulerExecutionLock executionLock, PlatformTransactionManager transactionManager, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.controls = Objects.requireNonNull(controls);
        this.executionLock = Objects.requireNonNull(executionLock);
        this.clock = Objects.requireNonNull(clock);
        this.suspendedTransaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.suspendedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /** Flyway 完成后补齐新增固定任务；历史未知行保留但永不执行。 */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        for (ScheduledJobRegistry.Job job : registry.all()) {
            controls.materialize(job.key(), job.defaultDelayMs());
        }
        for (ScheduledJobControl row : controls.all()) {
            try {
                registry.require(row.jobKey()).validateDelay(row.fixedDelayMs());
            } catch (IllegalArgumentException invalid) {
                log.error("scheduler_control_invalid jobKey={} reason={}", row.jobKey(), invalid.getMessage());
            }
        }
        ready = true;
    }

    @Scheduled(fixedDelay = 1_000, initialDelay = 1_000)
    public void scheduledTick() {
        if (!ready) return;
        List<String> due;
        try {
            due = controls.due(clock.instant(), MAX_JOBS_PER_TICK);
        } catch (RuntimeException failure) {
            log.warn("scheduler_due_query_failed failureType={}", failure.getClass().getSimpleName());
            return;
        }
        for (String key : due) {
            try {
                execute(key, false);
            } catch (RuntimeException failure) {
                log.warn("scheduler_job_failed jobKey={} failureType={}", key,
                        failure.getClass().getSimpleName());
            }
        }
    }

    /** 受控手动入口；允许 DB disabled，但静态能力、锁和业务安全约束照常生效。 */
    public ExecutionOutcome runOnce(String key) {
        if (!ready) throw new IllegalStateException("SCHEDULER_NOT_READY");
        return execute(key, true);
    }

    private ExecutionOutcome execute(String key, boolean manual) {
        ScheduledJobRegistry.Job job = registry.require(key);
        if (manual && !job.eligible().getAsBoolean())
            throw new IllegalStateException("SCHEDULER_CAPABILITY_UNAVAILABLE");
        SchedulerLockExecution<ExecutionOutcome> locked = executionLock.executeWithLock(
                new SchedulerLockKey("scheduled-job", key), EXECUTION_TIMEOUT,
                () -> suspendedTransaction.execute(status -> executeWithoutLockTransaction(job, manual)));
        return switch (locked.status()) {
            case ACQUIRED_AND_COMPLETED -> Objects.requireNonNull(locked.value());
            case NOT_ACQUIRED -> ExecutionOutcome.SKIPPED;
            case ACTION_FAILED, TIMED_OUT, INTERRUPTED -> {
                log.warn("scheduler_lock_failed jobKey={} status={} failureType={}", key, locked.status(),
                        locked.failureCause().map(cause -> cause.getClass().getSimpleName()).orElse("UNKNOWN"));
                yield ExecutionOutcome.FAILED;
            }
        };
    }

    private ExecutionOutcome executeWithoutLockTransaction(ScheduledJobRegistry.Job job, boolean manual) {
        if (!job.eligible().getAsBoolean()) {
            if (manual) throw new IllegalStateException("SCHEDULER_CAPABILITY_UNAVAILABLE");
            controls.skipUnavailable(job.key(), clock.instant());
            return ExecutionOutcome.SKIPPED;
        }
        UUID runId = UUID.randomUUID();
        Instant started = clock.instant();
        if (!controls.claim(job.key(), runId, started, manual)) return ExecutionOutcome.SKIPPED;
        try {
            ScheduledJobRegistry.JobResult result = Objects.requireNonNull(job.runOnce().get());
            controls.complete(job.key(), runId, clock.instant(), result.status(), result.errorCode());
            return switch (result.status()) {
                case "SUCCESS" -> ExecutionOutcome.SUCCESS;
                case "FAILED" -> ExecutionOutcome.FAILED;
                default -> ExecutionOutcome.SKIPPED;
            };
        } catch (RuntimeException failure) {
            controls.complete(job.key(), runId, clock.instant(), "FAILED", "JOB_EXECUTION_FAILED");
            log.warn("scheduler_execution_failed jobKey={} failureType={}", job.key(),
                    failure.getClass().getSimpleName());
            return ExecutionOutcome.FAILED;
        }
    }

    public enum ExecutionOutcome { SUCCESS, FAILED, SKIPPED }
}
