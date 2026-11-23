package com.guidinglight.nexusquant.research.application.paper.model;

import java.time.Instant;
import java.util.List;

/** 历史样本统计与规则分析来自同一次窗口；生成时间仅表示读取，不冒充事实发生时间或可用账户资本。 */
public record PaperLegacyAnalysisSnapshot(
        String source,
        int maxRuns,
        int includedRuns,
        boolean hasMore,
        Instant generatedAt,
        PaperPortfolioSummary portfolio,
        List<PaperPortfolioSummary.RunRef> runs,
        PaperExecutionDiagnostics diagnostics,
        PaperStrategyEvaluation evaluations,
        PaperAutoReview reviews) {
    /** 旧权益/日报没有币种事实，不以当前主数据或交易对名称回填历史计价单位。 */
    public String valuationStatus() {
        return "UNIT_NOT_RECORDED";
    }

    public PaperLegacyAnalysisSnapshot {
        runs = List.copyOf(runs);
    }
}
