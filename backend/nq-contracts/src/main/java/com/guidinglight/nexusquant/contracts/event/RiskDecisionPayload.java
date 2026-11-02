package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import java.time.Instant;

/**
 * RiskDecisionPayload 描述风控判定结果事件。
 */
public record RiskDecisionPayload(
        @JsonProperty("scope") String scope,
        @JsonProperty("scope_id") String scopeId,
        @JsonProperty("decision") RiskDecision decision,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("severity") RiskSeverity severity,
        @JsonProperty("ts") Instant ts
) {
}
