package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.EmergencyStopEventResponse;
import com.guidinglight.nexusquant.paper.api.dto.EmergencyStopRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.EquityCurveSnapshotResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRiskCheckResultResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertAckRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunDailyReportGenerateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunDailyReportResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunHeartbeatResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingOrderResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingPositionResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingTradeResponse;
import com.guidinglight.nexusquant.paper.api.dto.PositionCurveSnapshotResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunMonitorRunOnceResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunRecoverRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunRecoveryEventResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunRetryFailedStepRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunStabilityCheckGenerateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunStabilityCheckResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunSummaryResponse;
import com.guidinglight.nexusquant.paper.api.dto.TradeReplayRecordResponse;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunDailyReportGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunRecoverCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunRetryFailedStepCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunStabilityCheckGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/runs")
@Tag(name = "Paper Trading API", description = "GateI-3 SIM/Paper Trading 运行闭环接口。")
public class PaperTradingController {

    private final PaperTradingApiService apiService;

    public PaperTradingController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询 Paper run 列表", description = "按 publishId 或 status 过滤 Paper Trading run 列表。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<PaperTradingRunResponse> list(
            @RequestParam(required = false) String publishId,
            @RequestParam(required = false) String status
    ) {
        TraceIdContext.getOrCreate();
        return apiService.list(publishId, status).stream()
                .map(PaperTradingRunResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "创建 Paper run", description = "基于 publishId 创建 Paper Trading run，固化 publish/strategy version/dataset/param/config 快照。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "404", description = "发布记录不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse create(@Valid @RequestBody PaperTradingRunCreateRequestBody request) {
        TraceIdContext.getOrCreate();
        var command = new PaperTradingRunCreateCommand(
                request.publishId(),
                request.tradeEnv(),
                request.exchangeCode(),
                request.marketType(),
                request.symbol(),
                request.intervalCode(),
                request.configSnapshotJson(),
                "system"
        );
        return PaperTradingRunResponse.from(apiService.create(command));
    }

    @GetMapping("/{paperRunId}")
    @Operation(summary = "查询 Paper run 详情", description = "按 paperRunId 查询 Paper Trading run 详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse detail(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.getById(paperRunId));
    }

    @PostMapping("/{paperRunId}/start")
    @Operation(summary = "启动 Paper run", description = "把 CREATED 状态的 Paper run 推进到 RUNNING。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "启动成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse start(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.start(paperRunId));
    }

    @PostMapping("/{paperRunId}/stop")
    @Operation(summary = "停止 Paper run", description = "把 RUNNING 状态的 Paper run 推进到 STOPPED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "停止成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse stop(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.stop(paperRunId));
    }

    @GetMapping("/{paperRunId}/summary")
    @Operation(summary = "查询 Paper run 只读聚合摘要",
            description = "聚合指定 Paper run 的运行结果复盘、异常原因诊断、运行事件时间线与关键计数，供前端详情区优先消费。只读，不触发任何状态机或外部调用。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunSummaryResponse summary(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperRunSummaryResponse.from(apiService.summary(paperRunId));
    }

    @GetMapping("/{paperRunId}/orders")
    @Operation(summary = "查询 Paper orders", description = "返回指定 Paper run 的订单事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingOrderResponse> orders(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listOrders(paperRunId).stream()
                .map(PaperTradingOrderResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/trades")
    @Operation(summary = "查询 Paper trades", description = "返回指定 Paper run 的成交事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingTradeResponse> trades(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listTrades(paperRunId).stream()
                .map(PaperTradingTradeResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/positions")
    @Operation(summary = "查询 Paper positions", description = "返回指定 Paper run 的持仓事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingPositionResponse> positions(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listPositions(paperRunId).stream()
                .map(PaperTradingPositionResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/risk-results")
    @Operation(summary = "查询 Paper run 风控结果", description = "返回指定 Paper run 的风控检查结果列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRiskCheckResultResponse> riskResults(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listRiskResults(paperRunId).stream()
                .map(PaperRiskCheckResultResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/risk-results/run-once")
    @Operation(summary = "执行一次风控检查", description = "对指定 Paper run 执行一次最小风控检查并返回结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "检查完成"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRiskCheckResultResponse runRiskOnce(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperRiskCheckResultResponse.from(apiService.runRiskCheckOnce(paperRunId));
    }

    @GetMapping("/{paperRunId}/equity-curve")
    @Operation(summary = "查询 Paper run 资金曲线", description = "返回指定 Paper run 的资金曲线快照列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<EquityCurveSnapshotResponse> equityCurve(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listEquityCurve(paperRunId).stream()
                .map(EquityCurveSnapshotResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/position-curve")
    @Operation(summary = "查询 Paper run 持仓曲线", description = "返回指定 Paper run 的持仓曲线快照列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PositionCurveSnapshotResponse> positionCurve(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listPositionCurve(paperRunId).stream()
                .map(PositionCurveSnapshotResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/replay")
    @Operation(summary = "查询 Paper run 交易复盘", description = "返回指定 Paper run 的交易复盘记录列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<TradeReplayRecordResponse> replay(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listReplayRecords(paperRunId).stream()
                .map(TradeReplayRecordResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/emergency-stop")
    @Operation(summary = "触发 Paper run 紧急停机", description = "对指定 Paper run 触发紧急停机，只作用于 SIM/Paper，不触发真实 LIVE 下单或撤单。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "停机事件已记录"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public EmergencyStopEventResponse emergencyStop(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody EmergencyStopRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return EmergencyStopEventResponse.from(apiService.triggerEmergencyStop(
                paperRunId, request.triggerType(), request.reason(),
                request.triggeredBy() != null ? request.triggeredBy() : "system"
        ));
    }

    @GetMapping("/{paperRunId}/emergency-stops")
    @Operation(summary = "查询 Paper run 异常停机事件", description = "返回指定 Paper run 的异常停机事件列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<EmergencyStopEventResponse> emergencyStops(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listEmergencyStops(paperRunId).stream()
                .map(EmergencyStopEventResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/heartbeats")
    @Operation(summary = "查询 Paper run 心跳记录", description = "返回指定 Paper run 的心跳记录列表（按 heartbeat_time 倒序）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunHeartbeatResponse> heartbeats(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listHeartbeats(paperRunId).stream()
                .map(PaperRunHeartbeatResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/heartbeats/run-once")
    @Operation(summary = "执行一次心跳", description = "对指定 Paper run 生成一次心跳并写入记录。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "心跳已记录"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunHeartbeatResponse runHeartbeatOnce(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperRunHeartbeatResponse.from(apiService.runHeartbeatOnce(paperRunId));
    }

    @GetMapping("/{paperRunId}/daily-reports")
    @Operation(summary = "查询 Paper run 日报列表", description = "返回指定 Paper run 的日报列表（按 report_date 倒序）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunDailyReportResponse> dailyReports(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listDailyReports(paperRunId).stream()
                .map(PaperRunDailyReportResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/daily-reports/generate")
    @Operation(summary = "生成 Paper run 日报", description = "为指定 Paper run 生成一份日报，按 (paperRunId, reportDate) 幂等。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "日报已生成"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunDailyReportResponse generateDailyReport(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody PaperRunDailyReportGenerateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        var command = new PaperRunDailyReportGenerateCommand(paperRunId, request.reportDate());
        return PaperRunDailyReportResponse.from(apiService.generateDailyReport(command));
    }

    @GetMapping("/{paperRunId}/daily-reports/{reportId}")
    @Operation(summary = "查询 Paper run 日报详情", description = "按 reportId 查询日报详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "日报不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunDailyReportResponse dailyReportDetail(
            @PathVariable @NotBlank String paperRunId,
            @PathVariable @NotBlank String reportId
    ) {
        TraceIdContext.getOrCreate();
        return PaperRunDailyReportResponse.from(apiService.getDailyReportById(reportId));
    }

    @GetMapping("/{paperRunId}/alerts")
    @Operation(summary = "查询 Paper run 告警列表", description = "返回指定 Paper run 的告警列表（按 created_at 倒序）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunAlertResponse> alerts(
            @PathVariable @NotBlank String paperRunId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity
    ) {
        TraceIdContext.getOrCreate();
        return apiService.listAlerts(paperRunId, status, severity).stream()
                .map(PaperRunAlertResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/alerts")
    @Operation(summary = "创建 Paper run 告警", description = "为指定 Paper run 创建一条告警事件。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "告警已创建"),
            @ApiResponse(responseCode = "400", description = "参数无效", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunAlertResponse createAlert(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody PaperRunAlertCreateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        var command = new PaperRunAlertCreateCommand(
                paperRunId, request.alertType(), request.severity(),
                request.title(), request.message(), request.source(),
                request.eventSnapshotJson()
        );
        return PaperRunAlertResponse.from(apiService.createAlert(command));
    }

    @PatchMapping("/{paperRunId}/alerts/{alertId}/ack")
    @Operation(summary = "确认告警", description = "将 OPEN 状态的告警标记为 ACKED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "确认成功"),
            @ApiResponse(responseCode = "404", description = "告警不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "告警已解决", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunAlertResponse ackAlert(
            @PathVariable @NotBlank String paperRunId,
            @PathVariable @NotBlank String alertId,
            @Valid @RequestBody(required = false) PaperRunAlertAckRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        String ackBy = request != null ? request.acknowledgedBy() : null;
        return PaperRunAlertResponse.from(apiService.ackAlert(alertId, ackBy));
    }

    @PatchMapping("/{paperRunId}/alerts/{alertId}/resolve")
    @Operation(summary = "解决告警", description = "将告警标记为 RESOLVED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解决成功"),
            @ApiResponse(responseCode = "404", description = "告警不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunAlertResponse resolveAlert(
            @PathVariable @NotBlank String paperRunId,
            @PathVariable @NotBlank String alertId
    ) {
        TraceIdContext.getOrCreate();
        return PaperRunAlertResponse.from(apiService.resolveAlert(alertId, "system"));
    }

    @GetMapping("/{paperRunId}/recovery-events")
    @Operation(summary = "查询 Paper run 恢复事件列表", description = "返回指定 Paper run 的恢复/重试事件列表（按 created_at 倒序）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数无效", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunRecoveryEventResponse> recoveryEvents(
            @PathVariable @NotBlank String paperRunId,
            @RequestParam(required = false) String recoveryType,
            @RequestParam(required = false) String status
    ) {
        TraceIdContext.getOrCreate();
        return apiService.listRecoveryEvents(paperRunId, recoveryType, status).stream()
                .map(PaperRunRecoveryEventResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/recover")
    @Operation(summary = "执行 Paper run 恢复", description = "对指定 Paper run 触发一次手动恢复，写入 recovery event 记录。不调用真实交易所下单。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "恢复已记录"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunRecoveryEventResponse recover(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody(required = false) PaperRunRecoverRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        String reason = request != null ? request.reason() : null;
        String requestJson = request != null ? request.requestJson() : null;
        var command = new PaperRunRecoverCommand(paperRunId, reason, requestJson);
        return PaperRunRecoveryEventResponse.from(apiService.recover(command));
    }

    @PostMapping("/{paperRunId}/retry-failed-step")
    @Operation(summary = "重试失败步骤", description = "对指定 Paper run 触发一次失败步骤重试，写入 recovery event 记录。不调用真实交易所下单。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "重试已记录"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunRecoveryEventResponse retryFailedStep(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody(required = false) PaperRunRetryFailedStepRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        String failedStep = request != null ? request.failedStep() : null;
        String reason = request != null ? request.reason() : null;
        String requestJson = request != null ? request.requestJson() : null;
        var command = new PaperRunRetryFailedStepCommand(paperRunId, failedStep, reason, requestJson);
        return PaperRunRecoveryEventResponse.from(apiService.retryFailedStep(command));
    }

    @GetMapping("/{paperRunId}/stability-checks")
    @Operation(summary = "查询 Paper run 稳定性验收列表", description = "返回指定 Paper run 的稳定性验收列表（按 created_at 倒序）。第一版口径，非 GateJ-FREEZE 最终验收。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数无效", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunStabilityCheckResponse> stabilityChecks(
            @PathVariable @NotBlank String paperRunId,
            @RequestParam(required = false) String status
    ) {
        TraceIdContext.getOrCreate();
        return apiService.listStabilityChecks(paperRunId, status).stream()
                .map(PaperRunStabilityCheckResponse::from)
                .toList();
    }

    @PostMapping("/{paperRunId}/stability-checks/generate")
    @Operation(summary = "生成 Paper run 稳定性验收", description = "为指定 Paper run 生成一份稳定性验收，按 (paperRunId, checkWindowStart, checkWindowEnd) 幂等。第一版口径，非 GateJ-FREEZE 最终验收。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "稳定性验收已生成"),
            @ApiResponse(responseCode = "400", description = "参数无效", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunStabilityCheckResponse generateStabilityCheck(
            @PathVariable @NotBlank String paperRunId,
            @Valid @RequestBody PaperRunStabilityCheckGenerateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        var command = new PaperRunStabilityCheckGenerateCommand(
                paperRunId, request.checkWindowStart(), request.checkWindowEnd());
        return PaperRunStabilityCheckResponse.from(apiService.generateStabilityCheck(command));
    }

    @GetMapping("/{paperRunId}/stability-checks/{stabilityCheckId}")
    @Operation(summary = "查询 Paper run 稳定性验收详情", description = "按 stabilityCheckId 查询稳定性验收详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "稳定性验收不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunStabilityCheckResponse stabilityCheckDetail(
            @PathVariable @NotBlank String paperRunId,
            @PathVariable @NotBlank String stabilityCheckId
    ) {
        TraceIdContext.getOrCreate();
        return PaperRunStabilityCheckResponse.from(apiService.getStabilityCheckById(stabilityCheckId));
    }

    @PostMapping("/{paperRunId}/monitor/run-once")
    @Operation(summary = "执行一次监控守护", description = "对指定 Paper run 执行一次监控守护：检查 heartbeat lag 并落库 HEARTBEAT_LAG 告警；检查 schedule fire failed 并落库 SCHEDULE_FIRE_FAILED 告警。第一版只落库，不外发通知。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "监控守护已执行"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunMonitorRunOnceResponse runMonitorOnce(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperRunMonitorRunOnceResponse.from(apiService.runMonitorOnce(paperRunId));
    }
}
