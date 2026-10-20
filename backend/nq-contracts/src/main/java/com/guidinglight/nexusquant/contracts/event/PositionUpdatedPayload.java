package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PositionUpdatedPayload 描述仓位投影更新结果。
 */
public record PositionUpdatedPayload(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("available_qty") BigDecimal availableQty,
        @JsonProperty("frozen_qty") BigDecimal frozenQty,
        @JsonProperty("ts") Instant ts
) {
}
