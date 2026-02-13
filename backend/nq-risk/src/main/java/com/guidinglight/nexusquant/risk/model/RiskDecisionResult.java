package com.guidinglight.nexusquant.risk.model;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;

/**
 * RiskDecisionResult 表示风控结果。
 */
public record RiskDecisionResult(
        RiskDecision decision,
        String reasonCode,
        RiskSeverity severity,
        String traceId
) {
}
