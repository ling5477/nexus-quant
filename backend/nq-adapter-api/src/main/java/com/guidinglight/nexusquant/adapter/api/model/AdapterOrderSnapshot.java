package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AdapterOrderSnapshot 表示 adapter 视角下的订单快照。
 *
 * @param accountId        账户 ID
 * @param venue            交易场所
 * @param symbol           交易对
 * @param clientOrderId    客户端幂等键
 * @param externalOrderId  外部订单号，可空
 * @param externalStatus   adapter 视角的订单状态
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
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String externalStatus,
        BigDecimal price,
        BigDecimal origQuantity,
        BigDecimal executedQuantity,
        BigDecimal avgPrice,
        Instant updateTs,
        String rawPayload,
        String traceId
) {
}
