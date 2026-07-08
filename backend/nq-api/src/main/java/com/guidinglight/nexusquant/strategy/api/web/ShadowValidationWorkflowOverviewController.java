package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewQueryService;
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
 * ShadowValidationWorkflowOverviewController 暴露 GateT-1 workflow overview 只读 API。
 *
 * <p>Why: 本 controller 只声明一个 GET endpoint，并委托 read-only service 派生 operator items；它不会创建
 * review / acknowledge，不会启动 runner/scheduler，不调用 adapter 或真实交易所，不读取 credential，也不修改
 * account / order / ledger / Paper / Shadow 状态。
 */
@Validated
@RestController
@RequestMapping("/api/shadow-validation/workflow")
@Tag(name = "Shadow Validation Workflow API", description = "GateT-1 Shadow Validation Workflow 只读接口。")
public class ShadowValidationWorkflowOverviewController {

    private final ShadowValidationWorkflowOverviewQueryService queryService;

    public ShadowValidationWorkflowOverviewController(ShadowValidationWorkflowOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 Shadow Validation Workflow overview。
     *
     * @return read-only derived workflow overview；所有 operator items 均不是交易授权
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Shadow Validation Workflow 只读 overview",
            description = "只读聚合 GateS 本地 facts 并派生 operator items；不写库、不创建 review、不启动 runner、"
                    + "不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ShadowValidationWorkflowOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return ShadowValidationWorkflowOverviewResponse.from(queryService.overview(traceId));
    }
}
