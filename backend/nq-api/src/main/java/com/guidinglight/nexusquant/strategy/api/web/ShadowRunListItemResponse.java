package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * ShadowRunListItemResponse 是 GateR-8 Shadow Run 列表 item 只读 DTO。
 *
 * <p>DTO 只暴露列表入口需要的本地 fact 摘要和 no-side-effect flags；不返回 JSON payload、
 * credential、private endpoint、真实账户/订单字段，也不包含 trading approval 语义。
 */
@Schema(name = "ShadowRunListItemResponse", description = "GateR-8 read-only Shadow Run list item")
public record ShadowRunListItemResponse(
        UUID id,
        String status,
        String strategyVersionId,
        UUID datasetId,
        String paperRunId,
        String authorizationBoundary,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        int blockersCount,
        int warningsCount,
        int nextStepsCount,
        boolean noOrderSubmission,
        boolean noCredentialAccess,
        boolean noPrivateEndpoint,
        boolean noLedgerMutation,
        boolean noAccountMutation
) {
    public static ShadowRunListItemResponse from(ShadowRun run) {
        ShadowRunSensitiveDataGuard.validateJson("blockers", run.blockers());
        ShadowRunSensitiveDataGuard.validateJson("warnings", run.warnings());
        ShadowRunSensitiveDataGuard.validateJson("nextSteps", run.nextSteps());
        return new ShadowRunListItemResponse(
                run.id(),
                run.status().name(),
                run.strategyVersionId(),
                run.datasetId(),
                run.paperRunId(),
                run.authorizationBoundary().name(),
                run.traceId(),
                run.createdAt(),
                run.updatedAt(),
                run.startedAt(),
                run.completedAt(),
                arrayCount(run.blockers()),
                arrayCount(run.warnings()),
                arrayCount(run.nextSteps()),
                run.noOrderSubmission(),
                run.noCredentialAccess(),
                run.noPrivateEndpoint(),
                run.noLedgerMutation(),
                run.noAccountMutation()
        );
    }

    private static int arrayCount(JsonNode value) {
        return value != null && value.isArray() ? value.size() : 0;
    }
}
