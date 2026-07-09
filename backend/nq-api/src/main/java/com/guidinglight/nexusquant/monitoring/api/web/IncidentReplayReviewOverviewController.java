package com.guidinglight.nexusquant.monitoring.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IncidentReplayReviewOverviewController 暴露 GateT-3 Incident / Replay Review overview 只读 API。
 *
 * <p>Why：GateT-3 只允许实现 `GET /api/incidents/replay/review/overview` 的人工诊断复核 read model。
 * 本 controller 不接受 request body，不提供 POST/PUT/PATCH/DELETE，不创建 incident / alert / replay /
 * review / acknowledge / escalation / closeout，不启动 runner/scheduler，不调用真实交易所，不读取 credential，
 * 也不修改 account / order / ledger / Paper / Shadow 状态。
 */
@Validated
@RestController
@RequestMapping("/api/incidents/replay/review")
@Tag(name = "Incident Replay Review API", description = "GateT-3 Incident / Replay Review 只读接口。")
public class IncidentReplayReviewOverviewController {

    private final IncidentReplayReviewOverviewQueryService queryService;

    public IncidentReplayReviewOverviewController(IncidentReplayReviewOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 Incident / Replay Review overview。
     *
     * @return read-only derived review overview；不表达自动处置、真实 incident closeout 或交易授权
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Incident / Replay Review overview",
            description = "只读聚合本地 Incident / Replay diagnostics 并派生 review item；"
                    + "不写库、不创建 incident/alert/replay/review、不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public IncidentReplayReviewOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return IncidentReplayReviewOverviewResponse.from(queryService.overview(traceId));
    }
}
