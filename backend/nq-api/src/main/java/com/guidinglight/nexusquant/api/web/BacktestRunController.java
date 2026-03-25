package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.api.service.BacktestRunApiService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BacktestRunController 提供正式回测运行、评估、发布与事实查询接口。
 * <p>
 * Why:
 * Step 4 把 trace 生命周期统一交给 Filter + TraceIdContext，Controller 只读取当前 trace，
 * 不再自行处理 header、MDC 和异常链的 trace 组装。
 */
@Validated
@RestController
@ConditionalOnBean(BacktestRunApiService.class)
@RequestMapping("/api/backtest-runs")
@Tag(name = "Backtest Run API", description = "正式回测运行、评估、发布与事实查询接口。")
public class BacktestRunController {

    private final BacktestRunApiService applicationService;

    public BacktestRunController(BacktestRunApiService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @PostMapping
    @Operation(summary = "创建回测运行", description = "根据回测配置创建一条新的回测运行骨架。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "回测运行创建冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestRunResponse create(@Valid @RequestBody BacktestRunStartRequestBody request) {
        TraceIdContext.getOrCreate();
        return BacktestRunResponse.from(applicationService.create(request.backtestConfigId()), null, null);
    }

    @PostMapping("/{runId}/start")
    @Operation(summary = "启动回测执行", description = "启动指定回测运行，并返回最新摘要。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "启动成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "运行状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestRunResponse startExecution(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        var publishRecord = applicationService.findPublishOrNull(runId);
        return BacktestRunResponse.from(
                applicationService.startExecution(runId),
                BacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(runId)),
                publishRecord == null ? null : publishRecord.toSummary()
        );
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询回测运行详情", description = "按回测运行 ID 查询单次运行详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestRunResponse detail(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        var publishRecord = applicationService.findPublishOrNull(runId);
        return BacktestRunResponse.from(
                applicationService.getByBacktestRunId(runId),
                BacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(runId)),
                publishRecord == null ? null : publishRecord.toSummary()
        );
    }

    @GetMapping
    @Operation(summary = "查询回测运行列表", description = "按研究配置或回测配置筛选回测运行列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<BacktestRunResponse> list(
            @RequestParam(required = false) @Size(max = 64, message = "researchConfigId length must be less than or equal to 64") String researchConfigId,
            @RequestParam(required = false) @Size(max = 64, message = "backtestConfigId length must be less than or equal to 64") String backtestConfigId
    ) {
        TraceIdContext.getOrCreate();
        return applicationService.list(researchConfigId, backtestConfigId)
                .stream()
                .map(run -> {
                    var publishRecord = applicationService.findPublishOrNull(run.backtestRunId());
                    return BacktestRunResponse.from(
                            run,
                            BacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(run.backtestRunId())),
                            publishRecord == null ? null : publishRecord.toSummary()
                    );
                })
                .toList();
    }

    @PostMapping("/{runId}/evaluate")
    @Operation(summary = "执行回测评估", description = "对指定回测运行执行评估并返回评估结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "评估成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "评估状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestEvaluationResponse evaluate(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return BacktestEvaluationResponse.from(applicationService.evaluate(runId));
    }

    @GetMapping("/{runId}/evaluation")
    @Operation(summary = "查询回测评估结果", description = "返回指定回测运行当前已生成的评估结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestEvaluationResponse evaluation(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return BacktestEvaluationResponse.from(applicationService.getEvaluation(runId));
    }

    @PostMapping("/{runId}/publish")
    @Operation(summary = "发布回测结果", description = "把回测结果发布为可复用的策略定义产物。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发布成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "发布状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestPublishResponse publish(
            @PathVariable @NotBlank(message = "runId must not be blank") String runId,
            @Valid @RequestBody(required = false) BacktestPublishRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return BacktestPublishResponse.from(applicationService.publish(runId, request == null ? null : request.displayName()));
    }

    @GetMapping("/{runId}/publish")
    @Operation(summary = "查询发布结果", description = "返回指定回测运行当前的发布结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "查询状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestPublishResponse publishDetail(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return BacktestPublishResponse.from(applicationService.getPublish(runId));
    }

    @GetMapping("/{runId}/sim-orders")
    @Operation(summary = "查询模拟订单", description = "返回指定回测运行生成的模拟订单事实列表。")
    public List<SimOrderResponse> orders(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return applicationService.listOrders(runId).stream().map(SimOrderResponse::from).toList();
    }

    @GetMapping("/{runId}/sim-trades")
    @Operation(summary = "查询模拟成交", description = "返回指定回测运行生成的模拟成交事实列表。")
    public List<SimTradeResponse> trades(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return applicationService.listTrades(runId).stream().map(SimTradeResponse::from).toList();
    }

    @GetMapping("/{runId}/sim-positions")
    @Operation(summary = "查询模拟持仓", description = "返回指定回测运行生成的模拟持仓事实列表。")
    public List<SimPositionResponse> positions(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return applicationService.listPositions(runId).stream().map(SimPositionResponse::from).toList();
    }

    @GetMapping("/{runId}/pnl-snapshots")
    @Operation(summary = "查询模拟权益快照", description = "返回指定回测运行的权益与 PnL 快照序列。")
    public List<SimPnlSnapshotResponse> pnlSnapshots(@PathVariable @NotBlank(message = "runId must not be blank") String runId) {
        TraceIdContext.getOrCreate();
        return applicationService.listPnlSnapshots(runId).stream().map(SimPnlSnapshotResponse::from).toList();
    }
}
