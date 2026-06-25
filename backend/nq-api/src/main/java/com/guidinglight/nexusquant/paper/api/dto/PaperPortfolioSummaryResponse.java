package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperPortfolioSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperPortfolioSummaryResponse —— Paper 组合看板只读聚合响应体（GateJ 后产品化 Loop-13）。
 *
 * 供前端组合看板消费：组合总览、策略/发布维度排行、Run 排行与数据质量提示。
 * safety 固定声明 SIM/Paper、LIVE 未开启、未触达真实交易所，不代表真实交易能力。
 */
@Schema(name = "PaperPortfolioSummaryResponse", description = "Paper 组合看板只读聚合事实源响应体")
public record PaperPortfolioSummaryResponse(
        OverviewResponse overview,
        List<GroupResponse> strategyGroups,
        List<GroupResponse> publishGroups,
        HighlightsResponse highlights,
        DataQualityResponse dataQuality,
        SafetyResponse safety,
        PortfolioCurveResponse portfolioCurve
) {

    public record OverviewResponse(
            int totalRuns,
            int runningCount,
            int stoppedCount,
            int failedCount,
            int cancelledCount,
            int createdCount,
            BigDecimal totalInitialEquity,
            BigDecimal totalCurrentEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            int returnEligibleRunCount,
            BigDecimal worstRunDrawdown,
            int openAlertCount,
            int riskBlockedRunCount,
            int noTradeRunCount,
            int dataInsufficientRunCount
    ) {}

    public record GroupResponse(
            String key,
            int runCount,
            BigDecimal currentEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal worstDrawdown,
            int riskBlockedCount,
            int openAlertCount,
            Instant lastRunTime
    ) {}

    public record RunRefResponse(
            String paperRunId,
            String status,
            String symbol,
            String strategyVersionId,
            String publishId,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            boolean riskBlocked,
            int openAlertCount,
            int tradeCount,
            Instant lastActivityAt
    ) {}

    public record HighlightsResponse(
            RunRefResponse topWinner,
            RunRefResponse worstDrawdown,
            RunRefResponse highestRisk,
            RunRefResponse mostRecent,
            List<RunRefResponse> noTradeRuns,
            List<RunRefResponse> riskBlockedRuns
    ) {}

    public record DataQualityResponse(
            List<RunRefResponse> missingEquityRuns,
            List<RunRefResponse> dataInsufficientRuns,
            List<RunRefResponse> missingBacktestSourceRuns,
            List<RunRefResponse> missingPublishSourceRuns
    ) {}

    public record SafetyResponse(
            String environment,
            boolean liveEnabled,
            boolean realExchangeTouched,
            String message
    ) {}

    /**
     * 组合级 equity / drawdown 时间序列响应（Loop-15 向后兼容新增）。
     * 数据不足时 points 为空列表、指标为 null（前端展示「数据不足」并回退单 run 口径）；仅代表 SIM/Paper 模拟。
     */
    public record PortfolioCurveResponse(
            List<CurvePointResponse> points,
            BigDecimal latestEquity,
            BigDecimal peakEquity,
            BigDecimal currentDrawdown,
            BigDecimal maxDrawdown,
            int pointCount,
            CurveCoverageResponse coverage
    ) {}

    public record CurvePointResponse(
            Instant timestamp,
            BigDecimal totalEquity,
            BigDecimal totalInitialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal peakEquity,
            BigDecimal drawdown,
            int sourceRunCount,
            int missingRunCount
    ) {}

    public record CurveCoverageResponse(
            int comparableRunCount,
            int missingEquityRunCount,
            int incompletePointCount
    ) {}

    public static PaperPortfolioSummaryResponse from(PaperPortfolioSummary summary) {
        PaperPortfolioSummary.Overview o = summary.overview();
        var overview = new OverviewResponse(
                o.totalRuns(), o.runningCount(), o.stoppedCount(), o.failedCount(), o.cancelledCount(), o.createdCount(),
                o.totalInitialEquity(), o.totalCurrentEquity(), o.totalPnl(), o.totalReturn(), o.returnEligibleRunCount(),
                o.worstRunDrawdown(), o.openAlertCount(), o.riskBlockedRunCount(), o.noTradeRunCount(), o.dataInsufficientRunCount());

        PaperPortfolioSummary.Highlights h = summary.highlights();
        var highlights = new HighlightsResponse(
                runRef(h.topWinner()), runRef(h.worstDrawdown()), runRef(h.highestRisk()), runRef(h.mostRecent()),
                runRefs(h.noTradeRuns()), runRefs(h.riskBlockedRuns()));

        PaperPortfolioSummary.DataQuality dq = summary.dataQuality();
        var dataQuality = new DataQualityResponse(
                runRefs(dq.missingEquityRuns()), runRefs(dq.dataInsufficientRuns()),
                runRefs(dq.missingBacktestSourceRuns()), runRefs(dq.missingPublishSourceRuns()));

        var safety = new SafetyResponse(
                "SIM/PAPER", false, false,
                "该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现");

        return new PaperPortfolioSummaryResponse(
                overview,
                summary.strategyGroups().stream().map(PaperPortfolioSummaryResponse::group).toList(),
                summary.publishGroups().stream().map(PaperPortfolioSummaryResponse::group).toList(),
                highlights, dataQuality, safety, portfolioCurve(summary.portfolioCurve()));
    }

    private static PortfolioCurveResponse portfolioCurve(PaperPortfolioSummary.PortfolioCurve curve) {
        if (curve == null) {
            // 兼容防御：理论上 assembler 总返回非空曲线（含空结构），此处仍保证响应稳定不为 null。
            return new PortfolioCurveResponse(List.of(), null, null, null, null, 0,
                    new CurveCoverageResponse(0, 0, 0));
        }
        PaperPortfolioSummary.PortfolioCurve.Coverage c = curve.coverage();
        return new PortfolioCurveResponse(
                curve.points().stream().map(PaperPortfolioSummaryResponse::curvePoint).toList(),
                curve.latestEquity(), curve.peakEquity(), curve.currentDrawdown(), curve.maxDrawdown(),
                curve.pointCount(),
                new CurveCoverageResponse(c.comparableRunCount(), c.missingEquityRunCount(), c.incompletePointCount()));
    }

    private static CurvePointResponse curvePoint(PaperPortfolioSummary.PortfolioCurve.CurvePoint p) {
        return new CurvePointResponse(
                p.timestamp(), p.totalEquity(), p.totalInitialEquity(), p.totalPnl(), p.totalReturn(),
                p.peakEquity(), p.drawdown(), p.sourceRunCount(), p.missingRunCount());
    }

    private static GroupResponse group(PaperPortfolioSummary.Group g) {
        return new GroupResponse(
                g.key(), g.runCount(), g.currentEquity(), g.totalPnl(), g.totalReturn(),
                g.worstDrawdown(), g.riskBlockedCount(), g.openAlertCount(), g.lastRunTime());
    }

    private static RunRefResponse runRef(PaperPortfolioSummary.RunRef r) {
        if (r == null) {
            return null;
        }
        return new RunRefResponse(
                r.paperRunId(), r.status(), r.symbol(), r.strategyVersionId(), r.publishId(),
                r.currentEquity(), r.initialEquity(), r.totalPnl(), r.totalReturn(), r.maxDrawdown(),
                r.riskBlocked(), r.openAlertCount(), r.tradeCount(), r.lastActivityAt());
    }

    private static List<RunRefResponse> runRefs(List<PaperPortfolioSummary.RunRef> refs) {
        return refs.stream().map(PaperPortfolioSummaryResponse::runRef).toList();
    }
}
