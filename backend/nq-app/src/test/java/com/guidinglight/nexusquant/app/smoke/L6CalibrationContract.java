package com.guidinglight.nexusquant.app.smoke;

import java.util.concurrent.TimeUnit;

/** 正式校准与短时入口显式区分；短时数据永远不能成为正式校准结果。 */
record L6CalibrationContract(boolean smoke) implements L6SamplingSchedule {
    String mode() { return "CALIBRATION"; }
    long warmup() { return TimeUnit.SECONDS.toNanos(smoke ? 10 : 300); }
    long measurement() { return TimeUnit.SECONDS.toNanos(smoke ? 40 : 600); }
    public long total() { return warmup() + measurement(); }
    public String samplePhase(long elapsed) {
        if (elapsed < 0) throw new IllegalArgumentException("negative elapsed");
        return elapsed < warmup() ? "WARMUP" : elapsed < total() ? "MEASUREMENT" : "CLEANUP";
    }
}
