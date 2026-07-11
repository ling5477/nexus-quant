package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockExecution;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 默认关闭、跨实例互斥的 Validation Evidence Scheduler。
 *
 * <p>该类只在显式 enabled 配置下由 configuration 注册。每次触发只通过 GateV-3A lock 执行一次
 * read-only aggregate callback；不 retry、不创建额外线程池、不调用 HTTP、review lifecycle 或交易写侧。
 */
public final class ValidationEvidenceScheduler {

    static final String JOB = "validation-evidence-refresh";

    private static final Logger log = LoggerFactory.getLogger(ValidationEvidenceScheduler.class);

    private final ValidationEvidenceSchedulerProperties properties;
    private final ValidationEvidenceRefreshService refreshService;
    private final SchedulerExecutionLock executionLock;
    private final Clock clock;

    public ValidationEvidenceScheduler(
            ValidationEvidenceSchedulerProperties properties,
            ValidationEvidenceRefreshService refreshService,
            SchedulerExecutionLock executionLock,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.refreshService = Objects.requireNonNull(refreshService, "refreshService must not be null");
        this.executionLock = Objects.requireNonNull(executionLock, "executionLock must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Spring 调度入口；duration placeholders 与已验证的 properties 使用相同前缀和默认值。
     */
    @Scheduled(
            fixedDelayString = "${nq.validation-operations.scheduler.fixed-delay:PT5M}",
            initialDelayString = "${nq.validation-operations.scheduler.initial-delay:PT30S}"
    )
    public void scheduledRefresh() {
        runOnce();
    }

    /**
     * 执行一轮受控 refresh，所有非成功 lock 状态均 fail-closed 映射。
     *
     * @return 脱敏运行摘要；不会持久化 execution history
     */
    public ValidationEvidenceRefreshResult runOnce() {
        if (!properties.isEnabled()) {
            return logResult(nonQueryResult(ValidationEvidenceRefreshResult.Result.SKIPPED_DISABLED, null));
        }

        String traceId = TraceIdContext.putOrCreate(null);
        try {
            SchedulerLockExecution<ValidationEvidenceRefreshResult> execution = executionLock.executeWithLock(
                    new SchedulerLockKey(properties.getLockNamespace(), properties.getLockName()),
                    properties.getExecutionTimeout(),
                    () -> refreshService.refresh(traceId)
            );
            ValidationEvidenceRefreshResult result = switch (execution.status()) {
                case ACQUIRED_AND_COMPLETED -> Objects.requireNonNull(
                        execution.value(),
                        "completed lock execution must carry refresh result"
                );
                case NOT_ACQUIRED -> nonQueryResult(
                        ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED,
                        null
                );
                case ACTION_FAILED -> nonQueryResult(
                        ValidationEvidenceRefreshResult.Result.FAILED,
                        ValidationEvidenceRefreshResult.FailureCategory.ACTION_FAILED
                );
                case TIMED_OUT -> nonQueryResult(
                        ValidationEvidenceRefreshResult.Result.FAILED,
                        ValidationEvidenceRefreshResult.FailureCategory.TIMED_OUT
                );
                case INTERRUPTED -> nonQueryResult(
                        ValidationEvidenceRefreshResult.Result.FAILED,
                        ValidationEvidenceRefreshResult.FailureCategory.INTERRUPTED
                );
            };
            return logResult(result);
        } catch (RuntimeException ex) {
            return logResult(nonQueryResult(
                    ValidationEvidenceRefreshResult.Result.FAILED,
                    ValidationEvidenceRefreshResult.FailureCategory.LOCK_INVOCATION_FAILED
            ));
        } finally {
            TraceIdContext.clear();
        }
    }

    private ValidationEvidenceRefreshResult nonQueryResult(
            ValidationEvidenceRefreshResult.Result result,
            ValidationEvidenceRefreshResult.FailureCategory failureCategory
    ) {
        Instant now = clock.instant();
        return new ValidationEvidenceRefreshResult(
                now,
                now,
                Duration.ZERO.toMillis(),
                Availability.UNKNOWN,
                FreshnessStatus.UNKNOWN,
                null,
                0,
                0,
                result,
                failureCategory,
                true,
                true,
                true,
                true
        );
    }

    /** 日志严格限制为低基数状态和摘要字段，不记录异常 message、traceId 或 evidence payload。 */
    private ValidationEvidenceRefreshResult logResult(ValidationEvidenceRefreshResult result) {
        String template = "validation_evidence_scheduler job={} result={} startedAt={} completedAt={} "
                + "durationMs={} availability={} freshnessStatus={} blockerCount={} warningCount={} failureCategory={}";
        Object[] fields = {
                JOB,
                result.result(),
                result.startedAt(),
                result.completedAt(),
                result.durationMs(),
                result.availability(),
                result.freshnessStatus(),
                result.blockerCount(),
                result.warningCount(),
                result.failureCategory()
        };
        if (result.result() == ValidationEvidenceRefreshResult.Result.FAILED) {
            log.warn(template, fields);
        } else {
            log.info(template, fields);
        }
        return result;
    }
}
