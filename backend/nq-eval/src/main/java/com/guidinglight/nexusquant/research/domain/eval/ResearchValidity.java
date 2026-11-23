package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts;
import java.math.BigDecimal;

/** 同一冻结运行的时间窗口解释；收益差仅为数值比较，不代表统计显著性。 */
public record ResearchValidity(String validationStatus, String reason, String splitPolicy,
        String segmentEquityPolicy, Identity identity, ResearchRunFacts.Assumptions assumptions,
        Segment full, Segment inSample, Segment outOfSample, ResearchRunFacts.Benchmark benchmark,
        BigDecimal strategyVsBenchmarkDifference) {
    public ResearchValidity {
        strategyVsBenchmarkDifference = ResearchRunFacts.decimal(strategyVsBenchmarkDifference);
    }
    public static final String SPLIT_POLICY = "CHRONOLOGICAL_CLOSED_BARS_70_30_FLOOR_V1";
    public record Identity(String backtestRunId, String datasetId, String barContentSha256,
                           String strategyVersionId, String strategyChecksum, String symbol,
                           String interval, String datasetSnapshotJson, String evaluatedAt) { }
    public record Segment(String status, String startTime, String endTime, int barCount,
                          BigDecimal startingEquity, BigDecimal finalEquity, BigDecimal strategyReturn,
                          BigDecimal netPnl, BigDecimal maxDrawdown, BigDecimal maxDrawdownRate,
                          Integer tradeCount, BigDecimal fee, BigDecimal slippage) {
        public Segment {
            startingEquity = ResearchRunFacts.decimal(startingEquity);
            finalEquity = ResearchRunFacts.decimal(finalEquity);
            strategyReturn = ResearchRunFacts.decimal(strategyReturn);
            netPnl = ResearchRunFacts.decimal(netPnl);
            maxDrawdown = ResearchRunFacts.decimal(maxDrawdown);
            maxDrawdownRate = ResearchRunFacts.decimal(maxDrawdownRate);
            fee = ResearchRunFacts.decimal(fee);
            slippage = ResearchRunFacts.decimal(slippage);
        }
    }
    public static ResearchValidity unavailable(String reason) {
        return new ResearchValidity("NOT_AVAILABLE", reason, SPLIT_POLICY,
                "CONTINUOUS_RUN_PREVIOUS_BAR_EQUITY", null, null, null, null, null, null, null);
    }
}
