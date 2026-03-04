package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * AdapterOrderAck 描述统一下单回执。
 * <p>
 * Why:
 * GateC-0 要先把“接单成功/失败”的语义冻结到 adapter-api，后续无论是 PAPER、
 * OKX 还是 Binance，都只允许把各自方言映射成这一层语义再返回给 core。
 */
public record AdapterOrderAck(
        boolean accepted,
        String venue,
        String externalOrderId,
        AdapterError error,
        Instant ts,
        String traceId
) {
}
