package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * ShadowRunDetailResponse 是 GateR-6 Shadow Run detail 只读 DTO。
 *
 * <p>DTO 只暴露本地 Shadow Run fact、无副作用 flags 和脱敏 review 信息；不包含
 * apiKey、secret、credential material、real account/order、trading approval 或 LIVE ready 字段。
 */
@Schema(name = "ShadowRunDetailResponse", description = "GateR-6 read-only Shadow Run detail")
public record ShadowRunDetailResponse(
        UUID id,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String status,
        Instant windowStart,
        Instant windowEnd,
        String authorizationBoundary,
        SideEffectFlags sideEffectFlags,
        JsonNode blockers,
        JsonNode warnings,
        JsonNode nextSteps,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant stoppedAt,
        Instant completedAt
) {
    public static ShadowRunDetailResponse from(ShadowRun run) {
        return new ShadowRunDetailResponse(
                run.id(),
                run.strategyVersionId(),
                run.datasetId(),
                run.evaluationId(),
                run.publishId(),
                run.paperRunId(),
                run.status().name(),
                run.windowStart(),
                run.windowEnd(),
                run.authorizationBoundary().name(),
                new SideEffectFlags(
                        run.noOrderSubmission(),
                        run.noCredentialAccess(),
                        run.noPrivateEndpoint(),
                        run.noLedgerMutation(),
                        run.noAccountMutation(),
                        run.noExternalPrivateIo()
                ),
                safeJson("blockers", run.blockers()),
                safeJson("warnings", run.warnings()),
                safeJson("nextSteps", run.nextSteps()),
                run.requestId(),
                run.traceId(),
                run.createdAt(),
                run.updatedAt(),
                run.startedAt(),
                run.stoppedAt(),
                run.completedAt()
        );
    }

    private static JsonNode safeJson(String fieldName, JsonNode value) {
        ShadowRunSensitiveDataGuard.validateJson(fieldName, value);
        return value;
    }

    /** SideEffectFlags 固定表达 no-side-effect 边界，不是交易授权。 */
    public record SideEffectFlags(
            boolean noOrderSubmission,
            boolean noCredentialAccess,
            boolean noPrivateEndpoint,
            boolean noLedgerMutation,
            boolean noAccountMutation,
            boolean noExternalPrivateIo
    ) {
    }
}
