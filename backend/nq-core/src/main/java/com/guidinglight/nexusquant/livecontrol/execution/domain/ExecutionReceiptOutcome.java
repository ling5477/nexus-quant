package com.guidinglight.nexusquant.livecontrol.execution.domain;

public enum ExecutionReceiptOutcome {
    ACKNOWLEDGED,
    REJECTED,
    TIMEOUT,
    TRANSPORT_ERROR,
    UNKNOWN,
    QUERY_CONFIRMED,
    QUERY_NOT_FOUND
}
