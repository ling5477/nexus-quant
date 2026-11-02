package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * RiskEventRaised 表示风控或风险告警事件。
 *
 * @param scope 风险作用域，例如 ORDER/LEDGER
 * @param scopeId 作用域对象 ID
 * @param decision 判定结果，例如 ALLOW/REJECT
 * @param reason 判定原因
 * @param severity 风险级别
 * @param ts 事件时间
 */
public record RiskEventRaised(
        @JsonProperty("scope") String scope,
        @JsonProperty("scope_id") String scopeId,
        @JsonProperty("decision") String decision,
        @JsonProperty("reason") String reason,
        @JsonProperty("severity") String severity,
        @JsonProperty("ts") Instant ts
) {
}
