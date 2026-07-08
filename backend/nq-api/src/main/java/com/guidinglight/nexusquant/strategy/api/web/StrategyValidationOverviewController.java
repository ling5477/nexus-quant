package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
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
 * StrategyValidationOverviewController 暴露 GateS-3 Strategy Evaluation Gate runtime baseline。
 *
 * <p>Why: 本 controller 只处理 GET overview 查询并委托 read-only service 聚合本地事实；它不会创建
 * evaluation report、不会 publish strategy、不会创建 Paper/Shadow run、不会启动 runner/scheduler，
 * 不调用真实交易所，不读取 credential，也不修改 account / ledger / order 状态。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-validation")
@Tag(name = "Strategy Validation API", description = "GateS-3 Strategy Evaluation Gate runtime baseline 只读接口。")
public class StrategyValidationOverviewController {

    private final StrategyValidationOverviewQueryService queryService;

    public StrategyValidationOverviewController(StrategyValidationOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 Strategy Validation overview。
     *
     * @return read-only validation overview；APPROVED 也仅表示 validation 层面通过
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Strategy Evaluation Gate runtime baseline overview",
            description = "只读聚合 strategy version、evaluation、publish、Paper 与 Shadow 本地事实；"
                    + "不创建 report、不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyValidationOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return StrategyValidationOverviewResponse.from(queryService.overview(traceId));
    }
}
