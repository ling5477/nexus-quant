package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 回测生成并冻结的研究输入；评估只读这些事实，不重新读取可变行情或配置。 */
public record ResearchRunFacts(
        String backtestRunId, String datasetId, String barContentSha256,
        String strategyVersionId, String strategyChecksum, String symbol, String interval,
        String datasetSnapshotJson, Assumptions assumptions, List<BarWindow> bars, Benchmark benchmark
) {
    public record Assumptions(BigDecimal initialCapital, BigDecimal feeRate, BigDecimal slippageBps,
                              String executionTimingPolicy, String finalEquityPolicy,
                              String executionSpecJson, BigDecimal requestedInitialCapital) {
        public Assumptions {
            initialCapital = decimal(initialCapital);
            // 费率和原始资本允许更高输入精度；保留原值，不能把舍入后的假设冒充输入身份。
            feeRate = inputDecimal(feeRate);
            slippageBps = inputDecimal(slippageBps);
            requestedInitialCapital = inputDecimal(requestedInitialCapital);
        }
    }
    public record BarWindow(String startTime, String endTime, String snapshotTime) { }
    public record Benchmark(String status, String reason, String benchmarkType, String valuationPolicy,
                            String startTime, String endTime, String entryTime,
                            BigDecimal entryPrice, BigDecimal endPrice, BigDecimal initialCapital,
                            BigDecimal quantity, BigDecimal fee, BigDecimal slippage,
                            BigDecimal finalEquity, BigDecimal benchmarkReturn) {
        public Benchmark {
            entryPrice = decimal(entryPrice);
            endPrice = decimal(endPrice);
            initialCapital = decimal(initialCapital);
            quantity = decimal(quantity);
            fee = decimal(fee);
            slippage = decimal(slippage);
            finalEquity = decimal(finalEquity);
            benchmarkReturn = decimal(benchmarkReturn);
        }
    }
    // 数值和 scale 都固定，JSONB 的指数或尾零规范化不能改变读回对象的相等性。
    public static BigDecimal decimal(BigDecimal value) {
        return value == null ? null : value.setScale(18, RoundingMode.HALF_UP);
    }
    public static BigDecimal inputDecimal(BigDecimal value) {
        if (value == null) return null;
        BigDecimal canonical = value.stripTrailingZeros();
        return canonical.scale() < 0 ? canonical.setScale(0) : canonical;
    }
}
