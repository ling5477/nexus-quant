package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/**
 * AdapterOrderRequest 描述发送到交易所适配层的统一下单请求。
 *
 * Why:
 * docs/MODULES.md 要求 core 不直接依赖具体交易所实现，
 * 因此通过 adapter-api 冻结抽象请求模型。
 */
public record AdapterOrderRequest(
        String orderId,
        Long accountId,
        String symbol,
        String clientOrderId,
        String side,
        String type,
        BigDecimal price,
        BigDecimal qty,
        String traceId
) {
}
