package com.guidinglight.nexusquant.monitoring.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewQueryService;
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
 * IncidentReplayOverviewController 暴露 GateS-6 Incident / Replay overview 只读 API。
 *
 * <p>Why: 本 controller 只处理 GET overview 查询并委托 read-only service 聚合本地事实；它不会创建
 * incident、不会创建 alert、不会追加 event、不会生成 replay、不会启动 runner/scheduler，不调用真实交易所，
 * 不读取 credential，也不修改 account / ledger / order 状态。
 */
@Validated
@RestController
@RequestMapping("/api/incidents/replay")
@Tag(name = "Incident Replay API", description = "GateS-6 Monitoring / Incident / Replay 只读接口。")
public class IncidentReplayOverviewController {

    private final IncidentReplayOverviewQueryService queryService;

    public IncidentReplayOverviewController(IncidentReplayOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 Incident / Replay overview。
     *
     * @return read-only incident replay overview；severity 只表示诊断优先级
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Incident / Replay 只读 overview",
            description = "只读聚合 Shadow events、Paper alerts、recovery events、consistency divergence "
                    + "和 replay facts；不创建 incident、不生成 replay、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public IncidentReplayOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return IncidentReplayOverviewResponse.from(queryService.overview(traceId));
    }
}
