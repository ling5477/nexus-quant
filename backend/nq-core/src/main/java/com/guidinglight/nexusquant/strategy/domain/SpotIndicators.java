package com.guidinglight.nexusquant.strategy.domain;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/** 两种正式现货策略共用的有界窗口计算；调用方只传入当前已可见的收盘前缀。 */
public final class SpotIndicators {
    private SpotIndicators() { }

    public static void validateClosedBars(List<HistoricalBar> bars) {
        Objects.requireNonNull(bars, "closedBars");
        for (int index = 0; index < bars.size(); index++) {
            HistoricalBar bar = Objects.requireNonNull(bars.get(index), "bar");
            if (!bar.closeTime().isAfter(bar.openTime()) || bar.availableAt() == null
                    || bar.availableAt().isBefore(bar.closeTime()) || bar.closePrice() == null
                    || bar.closePrice().signum() <= 0 || !"OK".equals(bar.qualityStatus())) {
                throw new IllegalArgumentException("INVALID_BAR");
            }
            if (index > 0) {
                HistoricalBar previous = bars.get(index - 1);
                if (!bar.openTime().isAfter(previous.openTime())) {
                    throw new IllegalArgumentException("DUPLICATE_OR_OUT_OF_ORDER_BAR");
                }
                if (!bar.openTime().equals(previous.openTime().plus(previous.interval().duration()))
                        || !bar.exchangeCode().equals(previous.exchangeCode())
                        || !bar.marketType().equals(previous.marketType())
                        || !bar.symbol().equals(previous.symbol()) || bar.interval() != previous.interval()) {
                    throw new IllegalArgumentException("MISSING_OR_MIXED_BAR");
                }
                if (bar.availableAt().isBefore(previous.availableAt())) {
                    throw new IllegalArgumentException("LATE_ARRIVING_BAR_IN_SIGNAL_WINDOW");
                }
            }
        }
    }

    public static BigDecimal closeAverage(List<HistoricalBar> bars, int window) {
        requireWindow(bars, window, 0);
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = bars.size() - window; index < bars.size(); index++) {
            sum = sum.add(bars.get(index).closePrice());
        }
        return sum.divide(BigDecimal.valueOf(window), MathContext.DECIMAL128);
    }

    /** 突破阈值排除最后一根信号 bar，避免把当前高低点泄漏到比较窗口。 */
    public static BigDecimal previousHigh(List<HistoricalBar> bars, int window) {
        requireWindow(bars, window, 1);
        BigDecimal result = bars.get(bars.size() - 2).highPrice();
        for (int index = bars.size() - window - 1; index < bars.size() - 1; index++) {
            result = result.max(bars.get(index).highPrice());
        }
        return result;
    }

    public static BigDecimal previousLow(List<HistoricalBar> bars, int window) {
        requireWindow(bars, window, 1);
        BigDecimal result = bars.get(bars.size() - 2).lowPrice();
        for (int index = bars.size() - window - 1; index < bars.size() - 1; index++) {
            result = result.min(bars.get(index).lowPrice());
        }
        return result;
    }

    private static void requireWindow(List<HistoricalBar> bars, int window, int excluded) {
        if (window < 2 || window > 200 || bars.size() < window + excluded) {
            throw new IllegalArgumentException("INSUFFICIENT_INDICATOR_HISTORY");
        }
    }
}
