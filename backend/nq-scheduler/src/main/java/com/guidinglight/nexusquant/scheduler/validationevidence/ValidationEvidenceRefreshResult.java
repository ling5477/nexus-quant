package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次 Validation Evidence refresh 的脱敏运行摘要。
 *
 * <p>该结果不持久化完整 evidence payload，不包含 case、owner、账户、订单、Ledger 或 credential 数据。
 * safety flags 固定为保守值，任何结果都不是交易授权。
 */
public record ValidationEvidenceRefreshResult(
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        Availability availability,
        FreshnessStatus freshnessStatus,
        Instant lastCalculatedAt,
        long blockerCount,
        long warningCount,
        Result result,
        FailureCategory failureCategory,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled
) {

    public ValidationEvidenceRefreshResult {
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        availability = Objects.requireNonNull(availability, "availability must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        result = Objects.requireNonNull(result, "result must not be null");
        if (completedAt.isBefore(startedAt) || durationMs < 0 || blockerCount < 0 || warningCount < 0) {
            throw new IllegalArgumentException("refresh timing and counts must not be negative");
        }
        if (durationMs != Duration.between(startedAt, completedAt).toMillis()) {
            throw new IllegalArgumentException("durationMs must match startedAt/completedAt");
        }
        if ((result == Result.FAILED) != (failureCategory != null)) {
            throw new IllegalArgumentException("only FAILED result must carry failureCategory");
        }
        if (!diagnosticOnly || !noSideEffect || !notTradingAuthorization || !liveDisabled) {
            throw new IllegalArgumentException("scheduler safety flags must remain fail-closed");
        }
    }

    /** 运行结果只描述诊断 refresh，不表示业务状态或交易授权。 */
    public enum Result {
        SUCCESS,
        DEGRADED,
        FAILED,
        SKIPPED_DISABLED,
        SKIPPED_LOCK_NOT_ACQUIRED
    }

    /** 低基数失败分类；禁止把异常 message、SQL 参数或 payload 写入结果与日志。 */
    public enum FailureCategory {
        ACTION_FAILED,
        TIMED_OUT,
        INTERRUPTED,
        LOCK_INVOCATION_FAILED
    }
}
