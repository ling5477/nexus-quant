package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AdapterOrderSnapshot 表示 adapter 视角下的订单快照。
 *
 * @param accountId        账户 ID
 * @param exchangeCode     统一交易所标识
 * @param symbol           交易对
 * @param clientOrderId    客户端幂等键
 * @param exchangeOrderId  交易所订单号，可空
 * @param externalStatus   adapter 视角的订单状态
 * @param resultCategory   调用结果分类
 * @param error            非成功场景下的统一错误结构
 * @param price            订单价格，可空
 * @param origQuantity     原始下单数量，可空
 * @param executedQuantity 已成交数量，可空
 * @param avgPrice         均价，可空
 * @param updateTs         快照更新时间，可空
 * @param rawPayload       原始响应文本，可空
 * @param traceId          链路追踪 ID
 */
public record AdapterOrderSnapshot(
        Long accountId,
        String exchangeCode,
        String symbol,
        String clientOrderId,
        String exchangeOrderId,
        String externalStatus,
        AdapterResultCategory resultCategory,
        AdapterError error,
        BigDecimal price,
        BigDecimal origQuantity,
        BigDecimal executedQuantity,
        BigDecimal avgPrice,
        Instant updateTs,
        String rawPayload,
        String traceId,
        String tradeEnv
) {

    public AdapterOrderSnapshot(
            Long accountId,
            String exchangeCode,
            String symbol,
            String clientOrderId,
            String exchangeOrderId,
            String externalStatus,
            BigDecimal price,
            BigDecimal origQuantity,
            BigDecimal executedQuantity,
            BigDecimal avgPrice,
            Instant updateTs,
            String rawPayload,
            String traceId
    ) {
        this(
                accountId,
                exchangeCode,
                symbol,
                clientOrderId,
                exchangeOrderId,
                externalStatus,
                AdapterResultCategory.SUCCESS,
                null,
                price,
                origQuantity,
                executedQuantity,
                avgPrice,
                updateTs,
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

    public boolean found() {
        return resultCategory == AdapterResultCategory.SUCCESS;
    }

    public boolean notFound() {
        return resultCategory == AdapterResultCategory.NOT_FOUND;
    }

    public boolean deferred() {
        return resultCategory == AdapterResultCategory.DEFERRED;
    }
}
