package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperExecutionDiagnosticsResponse —— Paper 执行诊断只读聚合响应体（GateK Batch K1）。
 *
 * 供前端执行诊断视图消费：诊断总览、主因分布、单 run 诊断、策略/发布维度聚合。
 * cause / severity / causeConfidence 以枚举名字符串序列化（前端按枚举值映射展示）。
 * safety 固定声明 SIM/Paper、LIVE 未开启、未触达真实交易所；诊断结论不构成真实投资建议。
 */
@Schema(name = "PaperExecutionDiagnosticsResponse", description = "Paper 执行诊断只读聚合事实源响应体")
public record PaperExecutionDiagnosticsResponse(
        OverviewResponse overview,
        List<CauseDistributionResponse> causeDistribution,
        List<RunDiagnosticsResponse> runDiagnostics,
        List<GroupDiagnosticsResponse> strategyDiagnostics,
        List<GroupDiagnosticsResponse> publishDiagnostics,
        SafetyResponse safety
) {

    public record OverviewResponse(
            int totalRuns,
            int noOrderRunCount,
            int orderNoFillRunCount,
            int filledRunCount,
            int filledLossRunCount,
            int riskBlockedRunCount,
            int dataInsufficientRunCount,
            int highDrawdownRunCount,
            int failedRunCount,
            int runningRunCount
    ) {}

    public record CauseDistributionResponse(
            String cause,
            int count,
            String severity,
            String confidence,
            String description
    ) {}

    public record RunDiagnosticsResponse(
            String paperRunId,
            String strategyVersionId,
            String publishId,
            String status,
            int orderCount,
            int tradeCount,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            boolean riskBlocked,
            int openAlertCount,
            String primaryCause,
            List<String> secondaryCauses,
            String severity,
            String causeConfidence,
            String explanation,
            String suggestedAction,
            Instant lastRunTime
    ) {}

    public record GroupDiagnosticsResponse(
            String key,
            int runCount,
            String primaryCause,
            List<String> topCauses,
            int noOrderCount,
            int orderNoFillCount,
            int filledLossCount,
            int riskBlockedCount,
            int dataInsufficientCount,
            int highDrawdownCount,
            String severity,
            String causeConfidence
    ) {}

    public record SafetyResponse(
            String environment,
            boolean liveEnabled,
            boolean realExchangeTouched,
            String message
    ) {}

    public static PaperExecutionDiagnosticsResponse from(PaperExecutionDiagnostics diagnostics) {
        PaperExecutionDiagnostics.Overview o = diagnostics.overview();
        var overview = new OverviewResponse(
                o.totalRuns(), o.noOrderRunCount(), o.orderNoFillRunCount(), o.filledRunCount(), o.filledLossRunCount(),
                o.riskBlockedRunCount(), o.dataInsufficientRunCount(), o.highDrawdownRunCount(),
                o.failedRunCount(), o.runningRunCount());

        var safety = new SafetyResponse(
                "SIM/PAPER", false, false,
                "该执行诊断仅基于 Paper 模拟运行与本地执行事实做规则化归因，不构成真实投资建议，不代表 LIVE 或真实交易表现");

        return new PaperExecutionDiagnosticsResponse(
                overview,
                diagnostics.causeDistribution().stream()
                        .map(PaperExecutionDiagnosticsResponse::causeDistribution).toList(),
                diagnostics.runDiagnostics().stream()
                        .map(PaperExecutionDiagnosticsResponse::runDiagnostics).toList(),
                diagnostics.strategyDiagnostics().stream()
                        .map(PaperExecutionDiagnosticsResponse::group).toList(),
                diagnostics.publishDiagnostics().stream()
                        .map(PaperExecutionDiagnosticsResponse::group).toList(),
                safety);
    }

    private static CauseDistributionResponse causeDistribution(PaperExecutionDiagnostics.CauseDistribution d) {
        return new CauseDistributionResponse(
                d.cause().name(), d.count(), d.severity().name(), d.confidence().name(), d.description());
    }

    private static RunDiagnosticsResponse runDiagnostics(PaperExecutionDiagnostics.RunDiagnostics d) {
        return new RunDiagnosticsResponse(
                d.paperRunId(), d.strategyVersionId(), d.publishId(), d.status(),
                d.orderCount(), d.tradeCount(),
                d.currentEquity(), d.initialEquity(), d.totalPnl(), d.totalReturn(), d.maxDrawdown(),
                d.riskBlocked(), d.openAlertCount(),
                d.primaryCause().name(),
                d.secondaryCauses().stream().map(Enum::name).toList(),
                d.severity().name(), d.causeConfidence().name(),
                d.explanation(), d.suggestedAction(), d.lastRunTime());
    }

    private static GroupDiagnosticsResponse group(PaperExecutionDiagnostics.GroupDiagnostics g) {
        return new GroupDiagnosticsResponse(
                g.key(), g.runCount(), g.primaryCause().name(),
                g.topCauses().stream().map(Enum::name).toList(),
                g.noOrderCount(), g.orderNoFillCount(), g.filledLossCount(),
                g.riskBlockedCount(), g.dataInsufficientCount(), g.highDrawdownCount(),
                g.severity().name(), g.causeConfidence().name());
    }
}
