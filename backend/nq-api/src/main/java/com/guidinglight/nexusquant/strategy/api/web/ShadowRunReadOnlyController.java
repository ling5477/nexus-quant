package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public ShadowRunReadOnlyController(ShadowRunReadOnlyQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
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
