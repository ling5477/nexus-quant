package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/** 双actor扫描的理论调用上界，再由全生命周期producer预算截断；不增加任意headroom。 */
final class L6CalibrationBudget {
    static final JsonNode FROZEN = load();
    static final int ORDER_BUDGET = FROZEN.path("runOrderBudget").asInt();
    static final int ACTORS = FROZEN.path("actors").asInt();
    static final int SCHEDULES = FROZEN.path("schedules").asInt();
    static final int CADENCE_SECONDS = FROZEN.path("cadenceSeconds").asInt();
    static QualificationCapacity frozen() {
        var timing = new L6CalibrationContract(false);
        long cadence = java.util.concurrent.TimeUnit.SECONDS.toNanos(CADENCE_SECONDS);
        long waves = Math.ceilDiv(timing.warmup(), cadence) + Math.ceilDiv(timing.measurement(), cadence);
        // 每phase独立首轮；每actor每schedule每scan最多一个身份。恢复/重放和cleanup不新建身份。
        int maximum = Math.toIntExact(Math.min(ORDER_BUDGET, waves * ACTORS * SCHEDULES));
        return new QualificationCapacity(QualificationCapacity.Mode.L6_CALIBRATION, ORDER_BUDGET, maximum, maximum);
    }
    private static JsonNode load() {
        try (var input = L6CalibrationBudget.class.getResourceAsStream("/l6-calibration/budget.json")) {
            if (input == null) throw new IllegalStateException("missing calibration budget");
            return new ObjectMapper().readTree(input);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
