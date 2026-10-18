package com.guidinglight.nexusquant.scheduler.control;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 唯一周期业务入口；每轮最多处理二十个固定任务，DB 开关不能替代静态能力门槛。 */
@Component
@ConditionalOnProperty(
        name = "nq.runtime.provider-observation.enabled",
        havingValue = "false",
        matchIfMissing = true)
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
    public SchedulerDispatcher(
            ScheduledJobRegistry registry,
            ScheduledJobControlRepository controls,
            SchedulerExecutionLock executionLock,
            PlatformTransactionManager transactionManager) {
        this(registry, controls, executionLock, transactionManager, Clock.systemUTC());
    }

    SchedulerDispatcher(
            ScheduledJobRegistry registry,
            ScheduledJobControlRepository controls,
            SchedulerExecutionLock executionLock,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.controls = Objects.requireNonNull(controls);
        this.executionLock = Objects.requireNonNull(executionLock);
        this.clock = Objects.requireNonNull(clock);
        this.suspendedTransaction =
                new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.suspendedTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
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
                log.error(
                        "scheduler_control_invalid jobKey={} reason={}",
                        row.jobKey(),
                        invalid.getMessage());
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
            log.warn(
                    "scheduler_due_query_failed failureType={}",
                    failure.getClass().getSimpleName());
            return;
        }
        for (String key : due) {
            try {
                execute(key, false);
            } catch (RuntimeException failure) {
                log.warn(
                        "scheduler_job_failed jobKey={} failureType={}",
                        key,
                        failure.getClass().getSimpleName());
            }
        }
    }

    /** 受控手动入口；允许 DB disabled，但静态能力、锁和业务安全约束照常生效。 */
    public ExecutionOutcome runOnce(String key) {
        if (!ready) throw new IllegalStateException("SCHEDULER_NOT_READY");
        return execute(key, true);
    }

    /** 原请求UUID同时标识本次任务领取；重复提交只能领取一次，不能随机重建运行身份。 */
    public ExecutionOutcome runOnce(String key, UUID operationId) {
        if (!ready) throw new IllegalStateException("SCHEDULER_NOT_READY");
        return execute(key, true, Objects.requireNonNull(operationId));
    }

    private ExecutionOutcome execute(String key, boolean manual) {
        return execute(key, manual, null);
    }

    private ExecutionOutcome execute(String key, boolean manual, UUID operationId) {
        ScheduledJobRegistry.Job job = registry.require(key);
        if (manual && !job.eligible().getAsBoolean())
            throw new IllegalStateException("SCHEDULER_CAPABILITY_UNAVAILABLE");
        SchedulerLockExecution<ExecutionOutcome> locked =
                executionLock.executeWithLock(
                        new SchedulerLockKey("scheduled-job", key),
                        EXECUTION_TIMEOUT,
                        () ->
                                suspendedTransaction.execute(
                                        status ->
                                                executeWithoutLockTransaction(
                                                        job, manual, operationId)));
        return switch (locked.status()) {
            case ACQUIRED_AND_COMPLETED -> Objects.requireNonNull(locked.value());
            case NOT_ACQUIRED -> ExecutionOutcome.SKIPPED;
            case ACTION_FAILED, TIMED_OUT, INTERRUPTED -> {
                log.warn(
                        "scheduler_lock_failed jobKey={} status={} failureType={}",
                        key,
                        locked.status(),
                        locked.failureCause()
                                .map(cause -> cause.getClass().getSimpleName())
                                .orElse("UNKNOWN"));
                yield ExecutionOutcome.FAILED;
            }
        };
    }

    private ExecutionOutcome executeWithoutLockTransaction(
            ScheduledJobRegistry.Job job, boolean manual, UUID operationId) {
        if (!job.eligible().getAsBoolean()) {
            if (manual) throw new IllegalStateException("SCHEDULER_CAPABILITY_UNAVAILABLE");
            controls.skipUnavailable(job.key(), clock.instant());
            return ExecutionOutcome.SKIPPED;
        }
        UUID runId = operationId == null ? UUID.randomUUID() : operationId;
        Instant started = clock.instant();
        boolean claimed =
                operationId == null
                        ? controls.claim(job.key(), runId, started, manual)
                        : controls.claim(job.key(), runId, started, manual, operationId);
        if (!claimed) return ExecutionOutcome.SKIPPED;
        ScheduledJobRegistry.JobResult result;
        try {
            result = Objects.requireNonNull(job.runOnce().get());
        } catch (RuntimeException failure) {
            result = new ScheduledJobRegistry.JobResult("FAILED", "JOB_EXECUTION_FAILED");
            log.warn(
                    "scheduler_execution_failed jobKey={} failureType={}",
                    job.key(),
                    failure.getClass().getSimpleName());
        }
        // 业务执行可能已提交副作用；完成回执/审计失败只能保留未知，不能再写一次FAILED终态。
        // 外层锁和原操作服务负责保留未收敛身份，阻止以新UUID重跑同一任务。
        complete(job.key(), runId, result.status(), result.errorCode(), operationId);
        return switch (result.status()) {
            case "SUCCESS" -> ExecutionOutcome.SUCCESS;
            case "FAILED" -> ExecutionOutcome.FAILED;
            default -> ExecutionOutcome.SKIPPED;
        };
    }

    private void complete(String key, UUID runId, String result, String reason, UUID operationId) {
        if (operationId == null) controls.complete(key, runId, clock.instant(), result, reason);
        else controls.complete(key, runId, clock.instant(), result, reason, operationId);
    }

    public enum ExecutionOutcome {
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
