package com.guidinglight.nexusquant.livecontrol.execution.application.port;

/**
 * Query-only normalized status。PARTIAL_FILL/CANCEL_RACE 只用于证明未来语义，不替代 trades/orders 主事实。
 */
public record FakeExchangeQueryResult(
        Status status,
        String exchangeRequestId,
        String exchangeOrderId,
        String errorCategory,
        String errorCode
) {
    public enum Status {
        CONFIRMED,
        NOT_FOUND,
        PARTIAL_FILL_SIMULATION,
        CANCEL_RACE_SIMULATION,
        UNKNOWN
    }
}
