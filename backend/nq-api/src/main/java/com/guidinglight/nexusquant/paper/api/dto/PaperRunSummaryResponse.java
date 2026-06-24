package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperRunSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperRunSummaryResponse —— Paper run 只读聚合响应体（GateJ 后产品化 Loop-8）。
 *
 * 供前端详情区优先消费：运行结果复盘、异常原因聚合、运行事件时间线与关键计数。
 * safety 固定声明 SIM/Paper、LIVE 未开启、未触达真实交易所，不代表真实交易能力。
 */
@Schema(name = "PaperRunSummaryResponse", description = "Paper run 只读聚合事实源响应体")
public record PaperRunSummaryResponse(
        PaperTradingRunResponse run,
        CountsResponse counts,
        LatestResponse latest,
        ResultReviewResponse resultReview,
        List<DiagnosisResponse> diagnoses,
        List<TimelineEntryResponse> timeline,
        SafetyResponse safety
) {

    public record CountsResponse(
            int orderCount,
            int tradeCount,
            int fillCount,
            int positionCount,
            int openAlertCount,
            int recoveryEventCount
    ) {}

    public record LatestResponse(
            PaperTradingOrderResponse order,
            PaperTradingTradeResponse trade,
            PaperTradingPositionResponse position,
            EquityCurveSnapshotResponse equitySnapshot,
            PaperRunDailyReportResponse dailyReport,
            PaperRiskCheckResultResponse riskResult,
            PaperRunAlertResponse alert,
            PaperRunRecoveryEventResponse recoveryEvent
    ) {}

    public record ResultReviewResponse(
            String finalStatus,
            String runtimeDurationText,
            BigDecimal netPnl,
            String riskResult,
            String conclusion,
            String conclusionLevel
    ) {}

    public record DiagnosisResponse(
            String type,
            String severity,
            String title,
            String description,
            String checkTarget
    ) {}

    public record TimelineEntryResponse(
            String type,
            String status,
            Instant occurredAt,
            String title,
            String description
    ) {}

    public record SafetyResponse(
            String environment,
            boolean liveEnabled,
            boolean realExchangeTouched,
            String message
    ) {}

    public static PaperRunSummaryResponse from(PaperRunSummary summary) {
        PaperRunSummary.Counts c = summary.counts();
        PaperRunSummary.Latest l = summary.latest();
        PaperRunSummary.ResultReview r = summary.resultReview();

        var counts = new CountsResponse(
                c.orderCount(), c.tradeCount(), c.fillCount(),
                c.positionCount(), c.openAlertCount(), c.recoveryEventCount());

        var latest = new LatestResponse(
                l.order() != null ? PaperTradingOrderResponse.from(l.order()) : null,
                l.trade() != null ? PaperTradingTradeResponse.from(l.trade()) : null,
                l.position() != null ? PaperTradingPositionResponse.from(l.position()) : null,
                l.equitySnapshot() != null ? EquityCurveSnapshotResponse.from(l.equitySnapshot()) : null,
                l.dailyReport() != null ? PaperRunDailyReportResponse.from(l.dailyReport()) : null,
                l.riskResult() != null ? PaperRiskCheckResultResponse.from(l.riskResult()) : null,
                l.alert() != null ? PaperRunAlertResponse.from(l.alert()) : null,
                l.recoveryEvent() != null ? PaperRunRecoveryEventResponse.from(l.recoveryEvent()) : null);

        var resultReview = new ResultReviewResponse(
                r.finalStatus(), r.runtimeDurationText(), r.netPnl(),
                r.riskResult(), r.conclusion(), r.conclusionLevel());

        List<DiagnosisResponse> diagnoses = summary.diagnoses().stream()
                .map(d -> new DiagnosisResponse(d.type(), d.severity(), d.title(), d.description(), d.checkTarget()))
                .toList();

        List<TimelineEntryResponse> timeline = summary.timeline().stream()
                .map(t -> new TimelineEntryResponse(t.type(), t.status(), t.occurredAt(), t.title(), t.description()))
                .toList();

        var safety = new SafetyResponse(
                "SIM/PAPER", false, false,
                "SIM/Paper only · LIVE 未开启，不代表真实交易能力");

        return new PaperRunSummaryResponse(
                PaperTradingRunResponse.from(summary.run()),
                counts, latest, resultReview, diagnoses, timeline, safety);
    }
}
