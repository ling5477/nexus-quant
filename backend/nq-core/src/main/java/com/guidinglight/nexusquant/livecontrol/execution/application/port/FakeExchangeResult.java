package com.guidinglight.nexusquant.livecontrol.execution.application.port;

/** Deterministic fake mutation result；不承载 raw request/response。 */
public record FakeExchangeResult(
        Outcome outcome,
        String exchangeRequestId,
        String exchangeOrderId,
        String errorCategory,
        String errorCode
) {
    public enum Outcome {
        ACKNOWLEDGED,
        REJECTED,
        TIMEOUT,
        TRANSPORT_ERROR,
        UNKNOWN
    }
}
