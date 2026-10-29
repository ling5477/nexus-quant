package com.guidinglight.nexusquant.trading.application.query;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderQueryView 定义 trading 查询门面输出的内部订单投影。
 * <p>
 * Why:
 * query facade 不应直接返回 `nq-api` 的 web DTO，否则 controller 契约会反向污染
 * application / infra 读侧。
 */
public record OrderQueryView(
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String side,
        String type,
        BigDecimal price,
        BigDecimal quantity,
        OrderStatus status,
        String tradeEnv,
        Instant createdAt,
        Instant updatedAt,
        String traceId
) {
}
