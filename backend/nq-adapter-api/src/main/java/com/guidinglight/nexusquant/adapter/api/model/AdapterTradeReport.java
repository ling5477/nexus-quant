package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AdapterTradeReport 描述适配层成交回报。
 * <p>
 * Why:
 * GateD 要把成交同步、账本和恢复链路都绑定到统一 fill 语义，因此 `exchangeTradeId / quantity / fee / rawPayload`
 * 不能继续留在各交易所私有 DTO 中。
 */
public record AdapterTradeReport(
        String venue,
        Long accountId,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String exchangeTradeId,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal fee,
        String feeAsset,
        Instant tradeTs,
        String rawPayload,
        String traceId
) {
}
