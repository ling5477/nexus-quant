package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * LedgerPostFailed 表示记账失败事实。
 *
 * @param tradeId 成交 ID
 * @param accountId 账户 ID
 * @param result 处理结果，固定为 FAILED
 * @param reason 失败原因
 * @param ts 失败时间
 */
public record LedgerPostFailed(
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("result") String result,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}
