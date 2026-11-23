package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PositionUpdated 表示仓位投影更新结果。
 *
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param qty 总仓位数量
 * @param availableQty 可用仓位
 * @param avgPrice 持仓均价
 * @param reason 更新原因，例如 TRADE
 * @param ts 投影更新时间
 */
public record PositionUpdated(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("available_qty") BigDecimal availableQty,
        @JsonProperty("avg_price") BigDecimal avgPrice,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}
