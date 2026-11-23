package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * LedgerPosted 表示一笔成交的记账处理成功。
 *
 * @param tradeId 成交 ID
 * @param accountId 账户 ID
 * @param result 处理结果，固定为 POSTED
 * @param reason 成功说明
 * @param ts 记账时间
 */
public record LedgerPosted(
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("result") String result,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}
