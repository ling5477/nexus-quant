package com.guidinglight.nexusquant.research.domain.eval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 年化是派生展示指标；不可计算或超过正式存储范围时保留原因，不使真实盈亏评估整体失败。 */
public final class AnnualizedReturnCalculator {
    // 与现有 annualized_return numeric(36,18) 对齐；舍入后绝对值必须严格小于此上限。
    private static final BigDecimal STORAGE_LIMIT = new BigDecimal("1000000000000000000");
    private static final double SECONDS_PER_YEAR = 31_536_000D;

    private AnnualizedReturnCalculator() {}

    /** 沿用首尾快照整秒和365日复利口径；空值不是零，也不以截顶值伪造可存储的年化收益。 */
    public static Result calculate(BigDecimal totalReturnRate, Instant startTime, Instant endTime) {
        Objects.requireNonNull(totalReturnRate, "totalReturnRate");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        long seconds = Duration.between(startTime, endTime).getSeconds();
        if (seconds <= 0) return new Result(null, Status.INVALID_PERIOD);
        if (totalReturnRate.compareTo(BigDecimal.valueOf(-1)) <= 0) {
            return new Result(null, Status.NON_POSITIVE_GROWTH);
        }
        BigDecimal value;
        if (seconds == (long) SECONDS_PER_YEAR) {
            // 一整年无需复利换算；保留十进制输入，避免浮点将边界上的值变成界内值。
            value = totalReturnRate.setScale(18, RoundingMode.HALF_UP);
        } else {
            // 接近全亏时先以十进制求正增长因子，避免转double后将有效收益舍入成-1；接近零时用log1p保留微小收益。
            double logGrowth =
                    totalReturnRate.compareTo(new BigDecimal("-0.5")) <= 0
                            ? Math.log(BigDecimal.ONE.add(totalReturnRate).doubleValue())
                            : Math.log1p(totalReturnRate.doubleValue());
            double annualized = Math.expm1(logGrowth * (SECONDS_PER_YEAR / seconds));
            if (!Double.isFinite(annualized)) return new Result(null, Status.OUT_OF_RANGE);
            value = BigDecimal.valueOf(annualized).setScale(18, RoundingMode.HALF_UP);
        }
        if (value.abs().compareTo(STORAGE_LIMIT) >= 0) return new Result(null, Status.OUT_OF_RANGE);
        return new Result(value, Status.AVAILABLE);
    }

    /** 原因与可空值一同进入冻结评估指标，历史缺少原因的记录由前端明确表达为未提供。 */
    public record Result(BigDecimal value, Status status) {}

    public enum Status {
        AVAILABLE,
        INVALID_PERIOD,
        NON_POSITIVE_GROWTH,
        OUT_OF_RANGE
    }
}
