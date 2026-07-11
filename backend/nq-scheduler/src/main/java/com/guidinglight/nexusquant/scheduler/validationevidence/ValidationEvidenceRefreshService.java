package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 执行一次 Validation Operations runtime evidence 只读聚合并生成脱敏摘要。
 *
 * <p>Why: scheduler 只能复用 GateU aggregate 的既有五来源语义，不能直接调用来源、Controller、HTTP、
 * repository 或任何写侧。方法严格调用 aggregate 一次；异常直接抛给 advisory lock primitive，避免伪造成功。
 */
public final class ValidationEvidenceRefreshService {

    private final ValidationOperationsRuntimeEvidenceOverviewQueryService queryService;
    private final Clock clock;

    public ValidationEvidenceRefreshService(
            ValidationOperationsRuntimeEvidenceOverviewQueryService queryService,
            Clock clock
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 同步执行一次 bounded read-only aggregate。
     *
     * @param traceId 本次任务的本地 trace id，不得包含业务 payload
     * @return 只包含状态、计数、时间和固定 safety flags 的摘要
     * @throws RuntimeException aggregate 查询失败时原样传播给 lock primitive
     */
    public ValidationEvidenceRefreshResult refresh(String traceId) {
        Instant startedAt = clock.instant();
        ValidationOperationsRuntimeEvidenceOverviewReadModel overview = queryService.overview(traceId);
        ReadModelEvidenceMetadata metadata = overview.evidenceMetadata();
        long blockerCount = overview.sources().stream().filter(this::isBlocker).count();
        long warningCount = overview.sources().stream().filter(this::isWarning).count();
        ValidationEvidenceRefreshResult.Result result = metadata.availability() == Availability.AVAILABLE
                && metadata.freshnessStatus() == FreshnessStatus.FRESH
                && blockerCount == 0
                ? ValidationEvidenceRefreshResult.Result.SUCCESS
                : ValidationEvidenceRefreshResult.Result.DEGRADED;
        Instant completedAt = clock.instant();

        return new ValidationEvidenceRefreshResult(
                startedAt,
                completedAt,
                Duration.between(startedAt, completedAt).toMillis(),
                metadata.availability(),
                metadata.freshnessStatus(),
                metadata.lastCalculatedAt(),
                blockerCount,
                warningCount,
                result,
                null,
                metadata.diagnosticOnly(),
                metadata.noSideEffect(),
                metadata.notTradingAuthorization(),
                metadata.liveDisabled()
        );
    }

    /** UNAVAILABLE/UNKNOWN 来源按 fail-closed blocker 计数，但不创建 review case。 */
    private boolean isBlocker(RuntimeEvidenceSource source) {
        Availability availability = source.evidenceMetadata().availability();
        return availability == Availability.UNAVAILABLE || availability == Availability.UNKNOWN;
    }

    /** PARTIAL 或非 FRESH、且未归入 blocker 的来源按诊断 warning 计数，避免同一来源重复计数。 */
    private boolean isWarning(RuntimeEvidenceSource source) {
        return !isBlocker(source)
                && (source.evidenceMetadata().availability() == Availability.PARTIAL
                || source.evidenceMetadata().freshnessStatus() != FreshnessStatus.FRESH);
    }
}
