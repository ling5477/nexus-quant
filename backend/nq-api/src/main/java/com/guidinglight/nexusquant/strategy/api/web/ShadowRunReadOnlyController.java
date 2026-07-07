package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ShadowRunReadOnlyController 暴露 GateR-6 Shadow Run detail / replay 只读 API。
 *
 * <p>Why: 后续前端 detail / replay view 需要读取本地 Shadow Run facts。本 controller 只处理 GET
 * 查询并委托 read-only query service；不提供 create / start / stop / cancel / rerun / execute endpoint，
 * 不调用 runner，不访问 adapter，不读取 credential，不触发真实交易，也不修改 account / ledger / order。
 */
@Validated
@RestController
@RequestMapping("/api/shadow-runs")
@Tag(name = "Shadow Run Read-only API", description = "GateR-6 Shadow Run detail / replay 只读诊断接口。")
public class ShadowRunReadOnlyController {

    private final ShadowRunReadOnlyQueryService queryService;
    private final ShadowRunOverviewQueryService overviewQueryService;

    public ShadowRunReadOnlyController(
            ShadowRunReadOnlyQueryService queryService,
            ShadowRunOverviewQueryService overviewQueryService
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.overviewQueryService = Objects.requireNonNull(overviewQueryService, "overviewQueryService must not be null");
    }

    /**
     * 查询 Shadow Run 列表。
     *
     * @param status            可选状态筛选
     * @param strategyVersionId 可选策略版本筛选
     * @param datasetId         可选 dataset 筛选
     * @param paperRunId        可选 Paper run 筛选
     * @param limit             最大返回条数，默认 50，最大 100
     * @param offset            偏移量，默认 0
     * @return 只读列表；不代表 trading authorization 或 LIVE ready
     */
    @GetMapping
    @Operation(
            summary = "查询 Shadow Run 列表",
            description = "只读返回本地 Shadow Run 主事实摘要，支持 status / strategyVersionId / datasetId / paperRunId "
                    + "筛选和 bounded limit/offset；不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "查询参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ShadowRunListResponse list(
            @RequestParam(required = false) ShadowRunStatus status,
            @RequestParam(required = false) String strategyVersionId,
            @RequestParam(required = false) UUID datasetId,
            @RequestParam(required = false) String paperRunId,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "limit must be positive") @Max(value = 100, message = "limit must not exceed 100") int limit,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "offset must not be negative") int offset
    ) {
        TraceIdContext.getOrCreate();
        return ShadowRunListResponse.from(queryService.list(new ShadowRunListQuery(
                status,
                strategyVersionId,
                datasetId,
                paperRunId,
                limit,
                offset
        )));
    }

    /**
     * 查询 Shadow Run operational overview。
     *
     * <p>该 endpoint 只读取本地 Shadow Run facts 并返回系统级诊断摘要。它不是 start/stop/execute
     * 入口，不创建 Shadow Run、不追加 event/snapshot/report、不启动 runner/scheduler、不调用真实交易所、
     * 不读取 credential，也不修改 account / ledger / order。
     *
     * @return GateS-1 read-only overview；固定 not trading authorization
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Shadow Run overview",
            description = "只读返回本地 Shadow Run 系统整体运行诊断；不启动 runner、不外联、不读取 credential、"
                    + "不触发交易，不表示 trading authorization。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    public ShadowRunOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return ShadowRunOverviewResponse.from(overviewQueryService.overview(traceId));
    }

    /**
     * 查询 Shadow Run detail。
     *
     * @param id 本地 Shadow Run id
     * @return 只读 detail；不代表 trading authorization 或 LIVE ready
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "查询 Shadow Run detail",
            description = "只读返回本地 Shadow Run 主事实、无副作用边界、blockers/warnings/nextSteps；"
                    + "不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Shadow Run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ShadowRunDetailResponse detail(@PathVariable UUID id) {
        TraceIdContext.getOrCreate();
        return ShadowRunDetailResponse.from(queryService.getDetail(id));
    }

    /**
     * 查询 Shadow Run append-only events。
     *
     * @param id 本地 Shadow Run id
     * @return 只读事件列表；不存在 run 返回 404，不伪装为空数据
     */
    @GetMapping("/{id}/events")
    @Operation(
            summary = "查询 Shadow Run events",
            description = "只读返回 Shadow Run 生命周期和诊断事件；不追加事件、不改变状态、不启动 runner。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Shadow Run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ShadowRunEventResponse> events(@PathVariable UUID id) {
        TraceIdContext.getOrCreate();
        return queryService.listEvents(id).stream()
                .map(ShadowRunEventResponse::from)
                .toList();
    }

    /**
     * 查询 Shadow Run snapshots。
     *
     * @param id 本地 Shadow Run id
     * @return 只读 snapshot 列表；payload 已由 guard 防止敏感字段原样返回
     */
    @GetMapping("/{id}/snapshots")
    @Operation(
            summary = "查询 Shadow Run snapshots",
            description = "只读返回 INPUT_MARKETDATA / STRATEGY_DECISION / RISK_PREFLIGHT / ORDER_INTENT_PREVIEW "
                    + "本地快照；不执行策略、不提交订单。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Shadow Run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ShadowRunSnapshotResponse> snapshots(@PathVariable UUID id) {
        TraceIdContext.getOrCreate();
        return queryService.listSnapshots(id).stream()
                .map(ShadowRunSnapshotResponse::from)
                .toList();
    }

    /**
     * 查询 Shadow Run 最新 consistency report。
     *
     * @param id 本地 Shadow Run id
     * @return 最新 report；run 不存在或 report 尚未生成时返回 404
     */
    @GetMapping("/{id}/consistency-report/latest")
    @Operation(
            summary = "查询 Shadow Run 最新 consistency report",
            description = "只读返回最新 Paper vs Shadow consistency report；comparisonStatus 仅为诊断结果，"
                    + "不是 approval、trading authorization 或 LIVE readiness。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Shadow Run 或 latest report 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ShadowConsistencyReportResponse latestConsistencyReport(@PathVariable UUID id) {
        TraceIdContext.getOrCreate();
        return ShadowConsistencyReportResponse.from(queryService.getLatestConsistencyReport(id));
    }
}
