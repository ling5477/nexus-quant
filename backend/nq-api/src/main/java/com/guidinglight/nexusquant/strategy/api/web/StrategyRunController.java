package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.StrategyRunQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * StrategyRunController 提供正式策略运行结果查询接口。
 * <p>
 * Why:
 * Step 4 要删除 Controller 内重复的 trace 提取与 MDC 包装逻辑，
 * 因此这里只依赖过滤器已建立好的 `TraceIdContext`。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-runs")
@Tag(name = "Strategy Run API", description = "正式策略运行结果查询接口。")
public class StrategyRunController {

    private final StrategyRunQueryService strategyRunQueryService;

    public StrategyRunController(StrategyRunQueryService strategyRunQueryService) {
        this.strategyRunQueryService = Objects.requireNonNull(strategyRunQueryService, "strategyRunQueryService must not be null");
    }

    /**
     * 查询单次策略运行详情。
     */
    @GetMapping("/{runId}")
    @Operation(summary = "查询运行详情", description = "按运行 ID 查询单次策略运行的完整聚合结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyRunDetailResponse detail(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return StrategyRunDetailResponse.from(strategyRunQueryService.getRunDetail(runId));
    }

    /**
     * 查询运行列表。
     */
    @GetMapping
    @Operation(summary = "查询运行列表", description = "按 strategyId 或 scheduleId 过滤策略运行列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "查询条件非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<StrategyRunSummaryResponse> list(
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String scheduleId
    ) {
        TraceIdContext.getOrCreate();
        boolean hasStrategyId = strategyId != null && !strategyId.isBlank();
        boolean hasScheduleId = scheduleId != null && !scheduleId.isBlank();
        if (hasStrategyId == hasScheduleId) {
            throw new IllegalArgumentException("exactly one of strategyId or scheduleId must be provided");
        }
        return (hasStrategyId
                ? strategyRunQueryService.listRecentRunsByStrategyId(strategyId.trim())
                : strategyRunQueryService.listRecentRunsByScheduleJobId(scheduleId.trim()))
                .stream()
                .map(StrategyRunSummaryResponse::from)
                .toList();
    }
}



