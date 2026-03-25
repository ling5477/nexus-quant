package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.core.service.StrategyScheduleCreateRequest;
import com.guidinglight.nexusquant.core.service.StrategyScheduleScanService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * StrategyScheduleController 提供正式策略调度配置与扫描接口。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-schedules")
@Tag(name = "Strategy Schedule API", description = "正式策略调度配置与扫描接口。")
public class StrategyScheduleController {

    private final StrategyScheduleService strategyScheduleService;
    private final StrategyScheduleScanService strategyScheduleScanService;

    public StrategyScheduleController(
            StrategyScheduleService strategyScheduleService,
            StrategyScheduleScanService strategyScheduleScanService
    ) {
        this.strategyScheduleService = Objects.requireNonNull(strategyScheduleService, "strategyScheduleService must not be null");
        this.strategyScheduleScanService = Objects.requireNonNull(strategyScheduleScanService, "strategyScheduleScanService must not be null");
    }

    @PostMapping
    @Operation(summary = "创建策略计划", description = "创建一条新的策略调度配置。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "计划创建冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyScheduleResponse create(@Valid @RequestBody StrategyScheduleCreateRequestBody request) {
        TraceIdContext.getOrCreate();
        return StrategyScheduleResponse.from(strategyScheduleService.create(new StrategyScheduleCreateRequest(
                request.strategyId(),
                request.scheduleType(),
                request.cronExpr(),
                request.timezone(),
                request.enabled(),
                request.windowConfig(),
                request.dedupScope()
        )));
    }

    @GetMapping
    @Operation(summary = "查询策略计划列表", description = "按策略 ID 返回全部计划配置。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<StrategyScheduleResponse> listByStrategyId(
            @RequestParam @NotBlank(message = "strategyId must not be blank") String strategyId
    ) {
        TraceIdContext.getOrCreate();
        return strategyScheduleService.listByStrategyId(strategyId).stream().map(StrategyScheduleResponse::from).toList();
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "查询计划详情", description = "按调度 ID 查询单条计划配置。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyScheduleResponse detail(
            @PathVariable @NotBlank(message = "scheduleId must not be blank") String scheduleId
    ) {
        TraceIdContext.getOrCreate();
        return StrategyScheduleResponse.from(strategyScheduleService.getByScheduleJobId(scheduleId));
    }

    @PatchMapping("/{scheduleId}/status")
    @Operation(summary = "更新计划状态", description = "将调度切换为启用或禁用状态。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyScheduleResponse updateStatus(
            @PathVariable @NotBlank(message = "scheduleId must not be blank") String scheduleId,
            @Valid @RequestBody StrategyScheduleStatusUpdateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return StrategyScheduleResponse.from(Boolean.TRUE.equals(request.enabled())
                ? strategyScheduleService.enable(scheduleId)
                : strategyScheduleService.disable(scheduleId));
    }

    @PostMapping("/scan-once")
    @Operation(summary = "执行计划扫描", description = "执行一次计划扫描，并返回命中、跳过和失败的结构化结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "扫描成功"),
            @ApiResponse(responseCode = "409", description = "扫描状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyScheduleScanResponse scanOnce() {
        String traceId = TraceIdContext.getOrCreate();
        return StrategyScheduleScanResponse.from(strategyScheduleScanService.scanOnce(traceId));
    }
}
