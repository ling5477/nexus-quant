package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Duration;

/**
 * BarInterval 表示 GateF-2 最小历史 K 线周期。
 * <p>
 * Why:
 * 回测输入需要独立于实时订阅语义描述时间粒度，因此这里显式收口为历史 bar 的 interval，
 * 避免直接复用执行域 MarketDataAdapter 的实时语义。
 */
public enum BarInterval {
    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    FOUR_HOURS("4h", Duration.ofHours(4)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String wireValue;
    private final Duration duration;

    BarInterval(String wireValue, Duration duration) {
        this.wireValue = wireValue;
        this.duration = duration;
    }

    public String wireValue() {
        return wireValue;
    }

    public Duration duration() {
        return duration;
    }

    public static BarInterval fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("interval must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        for (BarInterval candidate : values()) {
            if (candidate.wireValue.equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unsupported interval: " + value);
    }
}

