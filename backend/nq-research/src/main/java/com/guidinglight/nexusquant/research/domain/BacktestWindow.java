package com.guidinglight.nexusquant.research.domain;

import com.fasterxml.jackson.databind.JsonNode;

/** 窗口索引始终相对于完整冻结输入；预热不继承策略状态、订单或资金。 */
public record BacktestWindow(int startBar, int endBar) {
    public static final String VERSION = "FLAT_WINDOW_V1";

    public static BacktestWindow read(JsonNode spec, int barCount) {
        if (!spec.has("windowExecution")) return new BacktestWindow(0, barCount);
        JsonNode window = spec.path("windowExecution");
        if (!VERSION.equals(window.path("version").asText())
                || !window.path("startBar").isIntegralNumber() || !window.path("endBar").isIntegralNumber()
                || !window.path("startBar").canConvertToInt() || !window.path("endBar").canConvertToInt()
                || !"FLAT".equals(window.path("initialPosition").asText())
                || !"DISABLED".equals(window.path("warmupTrading").asText())
                || !"HISTORY_ONLY_RESET_STATE_AT_START".equals(window.path("warmupPolicy").asText()))
            throw new IllegalArgumentException("INVALID_WINDOW_EXECUTION_POLICY");
        int start = window.path("startBar").intValue(), end = window.path("endBar").intValue();
        if (start < 0 || end <= start || end > barCount || end - start < 2)
            throw new IllegalArgumentException("INSUFFICIENT_DATA");
        return new BacktestWindow(start, end);
    }
}
