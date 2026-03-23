package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * AdapterOrderAck 描述统一下单回执。
 * <p>
 * Why:
 * GateD 要先把“接单成功/失败”的语义冻结到 adapter-api，后续无论是 PAPER、
 * OKX 还是 Binance，都只允许把各自方言映射成这一层语义再返回给 core。
 */
public record AdapterOrderAck(
        boolean accepted,
        String exchangeCode,
        Long accountId,
        String symbol,
        String clientOrderId,
        String exchangeOrderId,
        String externalStatus,
        AdapterResultCategory resultCategory,
        AdapterError error,
        Instant ackTs,
        String rawPayload,
        String traceId,
        String tradeEnv
) {

    public AdapterOrderAck(
            boolean accepted,
            String exchangeCode,
            Long accountId,
            String symbol,
            String clientOrderId,
            String exchangeOrderId,
            String externalStatus,
            AdapterError error,
            Instant ackTs,
            String rawPayload,
            String traceId
    ) {
        this(
                accepted,
                exchangeCode,
                accountId,
                symbol,
                clientOrderId,
                exchangeOrderId,
                externalStatus,
                accepted ? AdapterResultCategory.ACCEPTED : resolveCategory(error),
                error,
                ackTs,
                rawPayload,
                traceId,
                null
        );
    }

    public String venue() {
        return exchangeCode;
    }

    public String externalOrderId() {
        return exchangeOrderId;
    }

    private static AdapterResultCategory resolveCategory(AdapterError error) {
        return error == null ? AdapterResultCategory.FATAL_FAILURE : error.category();
    }
}
