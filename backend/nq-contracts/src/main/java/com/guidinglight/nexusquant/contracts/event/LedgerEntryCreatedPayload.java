package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.contracts.model.LedgerRefType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * LedgerEntryCreatedPayload 描述账本流水事件。
 */
public record LedgerEntryCreatedPayload(
        @JsonProperty("entry_id") String entryId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("currency") String currency,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("direction") LedgerDirection direction,
        @JsonProperty("ref_type") LedgerRefType refType,
        @JsonProperty("ref_id") String refId,
        @JsonProperty("ts") Instant ts
) {
}
