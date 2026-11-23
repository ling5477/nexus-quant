package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * AdapterCancelAck 表示统一的撤单回执。
 *
 * @param accepted        是否被 adapter 接受
 * @param exchangeCode    统一交易所标识
 * @param exchangeOrderId 交易所订单号，可空
 * @param resultCategory  统一结果分类
 * @param error           失败时的统一错误结构
 * @param ts              回执时间
 * @param traceId         链路追踪 ID
 */
public record AdapterCancelAck(
        boolean accepted,
        String exchangeCode,
        String exchangeOrderId,
        AdapterResultCategory resultCategory,
        AdapterError error,
        Instant ts,
        String traceId,
        String tradeEnv
) {

    public AdapterCancelAck(
            boolean accepted,
            String exchangeCode,
            String exchangeOrderId,
            AdapterError error,
            Instant ts,
            String traceId
    ) {
        this(
                accepted,
                exchangeCode,
                exchangeOrderId,
                accepted ? AdapterResultCategory.ACCEPTED : resolveCategory(error),
                error,
                ts,
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
