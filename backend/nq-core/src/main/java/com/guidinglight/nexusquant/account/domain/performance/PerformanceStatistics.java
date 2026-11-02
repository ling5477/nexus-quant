package com.guidinglight.nexusquant.account.domain.performance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 只读统计共用口径；资金流调整沿用期初权益基准，Sharpe 为零无风险利率、总体方差、非年化值。 */
public final class PerformanceStatistics {
    private static final MathContext MATH = MathContext.DECIMAL128;

    private PerformanceStatistics() {}

    /** 采样桶必须连续且每桶仅一点；缺测、重复、非正基数及零方差不伪装成零 Sharpe。 */
    public static Sharpe sharpe(List<Point> input, long samplingSeconds) {
        return sharpe(input, samplingSeconds, 1000);
    }

    /** 长观察沿用原公式，只接受两个固定资源预算；旧调用仍限制一千点。 */
    public static Sharpe sharpe(List<Point> input, long samplingSeconds, int maximumPoints) {
        if ((maximumPoints != 1000 && maximumPoints != 10000) || samplingSeconds <= 0 || input.size() > maximumPoints)
            throw new IllegalArgumentException("invalid bounded statistics input");
        var points = input.stream().sorted(Comparator.comparing(Point::at)).toList();
        if (points.size() < 3) return unavailable("INSUFFICIENT_SAMPLES", samplingSeconds, 0);
        if (points.stream().anyMatch(p -> p.equity() == null || p.netFlow() == null))
            return unavailable("INCOMPLETE_SAMPLES", samplingSeconds, 0);
        BigDecimal initialFlow = points.getFirst().netFlow();
        var returns = new ArrayList<BigDecimal>();
        for (int index = 1; index < points.size(); index++) {
            var previous = points.get(index - 1);
            var current = points.get(index);
            if (!previous.at().isBefore(current.at())
                    || Math.floorDiv(current.at().getEpochSecond(), samplingSeconds)
                                    - Math.floorDiv(previous.at().getEpochSecond(), samplingSeconds)
                            != 1) {
                return unavailable("IRREGULAR_INTERVALS", samplingSeconds, 0);
            }
            BigDecimal before =
                    previous.equity().subtract(previous.netFlow().subtract(initialFlow));
            BigDecimal after = current.equity().subtract(current.netFlow().subtract(initialFlow));
            if (before.signum() <= 0 || after.signum() <= 0)
                return unavailable("NON_POSITIVE_EQUITY", samplingSeconds, 0);
            returns.add(after.subtract(before).divide(before, 18, RoundingMode.HALF_UP));
        }
        BigDecimal mean =
                returns.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(returns.size()), MATH);
        BigDecimal variance =
                returns.stream()
                        .map(r -> r.subtract(mean).pow(2, MATH))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(returns.size()), MATH);
        if (variance.signum() == 0)
            return unavailable("ZERO_VARIANCE", samplingSeconds, returns.size());
        return new Sharpe(
                mean.divide(variance.sqrt(MATH), 18, RoundingMode.HALF_UP),
                "AVAILABLE",
                returns.size(),
                samplingSeconds,
                "FLOW_ADJUSTED_NON_ANNUALIZED_POPULATION",
                BigDecimal.ZERO);
    }

    public static Sharpe unavailable(String status, long samplingSeconds, int returnCount) {
        return new Sharpe(
                null,
                status,
                returnCount,
                samplingSeconds,
                "FLOW_ADJUSTED_NON_ANNUALIZED_POPULATION",
                BigDecimal.ZERO);
    }

    /** 同一对象、同一计价币种的实际观察；netFlow 为累计外部资金流，不含成交现金变动。 */
    public record Point(Instant at, BigDecimal equity, BigDecimal netFlow) {}

    public record Sharpe(
            BigDecimal value,
            String status,
            int returnCount,
            long samplingSeconds,
            String method,
            BigDecimal riskFreeRate) {}
}
