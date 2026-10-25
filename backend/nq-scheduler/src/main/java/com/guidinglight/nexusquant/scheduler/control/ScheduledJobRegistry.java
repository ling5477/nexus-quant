package com.guidinglight.nexusquant.scheduler.control;

import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.scheduling.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.validationevidence.scheduling.ValidationEvidenceScheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 固定代码任务目录；数据库只能引用这些身份，不能提供可执行代码。 */
@Component
public class ScheduledJobRegistry {
    private final Map<String, Job> jobs;

    public ScheduledJobRegistry(ObjectProvider<ContinuousSimRunService> continuous,
            ObjectProvider<PaperMatchingService> matching,
            ObjectProvider<LedgerReconcileScheduler> ledger,
            ObjectProvider<ValidationEvidenceScheduler> validation) {
        LinkedHashMap<String, Job> definitions = new LinkedHashMap<>();
        register(definitions, new Job("CONTINUOUS_SIM_POLL", 300_000, 30_000, 86_400_000,
                () -> continuous.getIfAvailable() != null,
                run(() -> continuous.getObject().pollActiveOnce())));
        register(definitions, new Job("PAPER_MATCHING", 2_000, 1_000, 60_000,
                () -> matching.getIfAvailable() != null,
                run(() -> matching.getObject().matchOnce(100))));
        // 恢复链可能继续执行已有 LIVE 订单，首版只登记身份，不允许数据库开关激活。
        register(definitions, deferred("STRATEGY_RECOVERY", 5_000));
        register(definitions, new Job("LEDGER_RECONCILIATION", 30_000, 5_000, 86_400_000,
                () -> ledger.getIfAvailable() != null,
                () -> ledger.getObject().reconcileOnce() == 0
                        ? JobResult.success()
                        : new JobResult("FAILED", "LEDGER_RECONCILIATION_DIFFERENCE")));
        // 交易所 reconcile/recovery 尚未迁移静态授权边界；保留身份但禁止 DB 开关激活。
        register(definitions, deferred("OKX_RECOVERY", 15_000));
        register(definitions, deferred("OKX_RECONCILIATION", 5_000));
        register(definitions, deferred("BINANCE_RECONCILIATION", 5_000));
        register(definitions, new Job("VALIDATION_EVIDENCE_REFRESH", 300_000, 1_000, 86_400_000,
                () -> validation.getIfAvailable() != null,
                () -> {
                    var result = validation.getObject().runOnce();
                    return switch (result.result()) {
                        case SUCCESS -> JobResult.success();
                        case DEGRADED, FAILED -> new JobResult("FAILED",
                                "VALIDATION_EVIDENCE_" + result.result().name());
                        case SKIPPED_DISABLED, SKIPPED_LOCK_NOT_ACQUIRED -> new JobResult("SKIPPED",
                                "VALIDATION_EVIDENCE_" + result.result().name());
                    };
                }));
        jobs = Map.copyOf(definitions);
    }

    public List<Job> all() {
        return List.copyOf(jobs.values());
    }

    public Job require(String key) {
        Job job = jobs.get(key);
        if (job == null) throw new IllegalArgumentException("UNKNOWN_SCHEDULER_JOB");
        return job;
    }

    private static Job deferred(String key, long delay) {
        return new Job(key, delay, 1_000, 86_400_000, () -> false,
                () -> { throw new IllegalStateException("SCHEDULER_JOB_NOT_MIGRATED"); });
    }

    private static Supplier<JobResult> run(Runnable action) {
        return () -> {
            action.run();
            return JobResult.success();
        };
    }

    private static void register(Map<String, Job> definitions, Job job) {
        if (definitions.putIfAbsent(job.key(), job) != null) {
            throw new IllegalStateException("DUPLICATE_SCHEDULER_JOB");
        }
    }

    public record Job(String key, long defaultDelayMs, long minDelayMs, long maxDelayMs,
            BooleanSupplier eligible, Supplier<JobResult> runOnce) {
        public Job {
            Objects.requireNonNull(key);
            Objects.requireNonNull(eligible);
            Objects.requireNonNull(runOnce);
            if (!key.matches("[A-Z][A-Z0-9_]{1,47}") || minDelayMs < 1_000
                    || maxDelayMs > 86_400_000 || defaultDelayMs < minDelayMs
                    || defaultDelayMs > maxDelayMs) {
                throw new IllegalArgumentException("INVALID_SCHEDULER_JOB_DEFINITION");
            }
        }

        public void validateDelay(long delayMs) {
            if (delayMs < minDelayMs || delayMs > maxDelayMs) {
                throw new IllegalArgumentException("SCHEDULER_DELAY_OUT_OF_RANGE");
            }
        }
    }

    public record JobResult(String status, String errorCode) {
        public JobResult {
            if (!List.of("SUCCESS", "FAILED", "SKIPPED").contains(status)) {
                throw new IllegalArgumentException("INVALID_SCHEDULER_JOB_RESULT");
            }
        }

        public static JobResult success() {
            return new JobResult("SUCCESS", null);
        }
    }
}
