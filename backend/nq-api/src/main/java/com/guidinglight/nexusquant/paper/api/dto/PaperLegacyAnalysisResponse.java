package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.model.PaperLegacyAnalysisSnapshot;

import java.time.Instant;
import java.util.List;

/** 仅返回既有规则分析的白名单视图与来源范围，不返回发布配置、费用原文或经济事实整包。 */
public record PaperLegacyAnalysisResponse(
        String source,
        String valuationStatus,
        int maxRuns,
        int includedRuns,
        boolean hasMore,
        Instant generatedAt,
        PaperPortfolioSummaryResponse portfolio,
        List<PaperPortfolioSummaryResponse.RunRefResponse> runs,
        PaperExecutionDiagnosticsResponse diagnostics,
        PaperStrategyEvaluationResponse evaluations,
        PaperAutoReviewResponse reviews) {
    public static PaperLegacyAnalysisResponse from(PaperLegacyAnalysisSnapshot snapshot) {
        return new PaperLegacyAnalysisResponse(
                snapshot.source(),
                snapshot.valuationStatus(),
                snapshot.maxRuns(),
                snapshot.includedRuns(),
                snapshot.hasMore(),
                snapshot.generatedAt(),
                PaperPortfolioSummaryResponse.from(snapshot.portfolio()),
                snapshot.runs().stream().map(PaperPortfolioSummaryResponse::runRef).toList(),
                PaperExecutionDiagnosticsResponse.from(snapshot.diagnostics()),
                PaperStrategyEvaluationResponse.from(snapshot.evaluations()),
                PaperAutoReviewResponse.from(snapshot.reviews()));
    }
}
