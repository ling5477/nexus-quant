package com.guidinglight.nexusquant.app.smoke;

import java.time.Duration;

/** 固定绝对阶段边界；正式模式不读取任何时长覆盖参数。 */
record L6DurationContract(long warmupNanos, long activeNanos, long drainNanos) {
    enum Phase { WARMUP, ACTIVE, DRAIN, COMPLETE }

    L6DurationContract {
        if (warmupNanos <= 0 || activeNanos <= 0 || drainNanos <= 0) {
            throw new IllegalArgumentException("positive phase durations required");
        }
    }

    static L6DurationContract forMode(boolean readiness) {
        return readiness ? seconds(15, 90, 10) : seconds(600, 2400, 600);
    }

    private static L6DurationContract seconds(long warmup, long active, long drain) {
        return new L6DurationContract(Duration.ofSeconds(warmup).toNanos(),
                Duration.ofSeconds(active).toNanos(), Duration.ofSeconds(drain).toNanos());
    }

    long activeEnd() { return warmupNanos + activeNanos; }
    long total() { return activeEnd() + drainNanos; }

    Phase phase(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("negative elapsed time");
        if (elapsedNanos < warmupNanos) return Phase.WARMUP;
        if (elapsedNanos < activeEnd()) return Phase.ACTIVE;
        if (elapsedNanos < total()) return Phase.DRAIN;
        return Phase.COMPLETE;
    }
}
