package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * AdapterOrderAck 描述交易所接单回执占位。
 */
public record AdapterOrderAck(
        String orderId,
        String externalOrderId,
        String status,
        Instant ts,
        String traceId
) {
}
