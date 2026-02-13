package com.guidinglight.nexusquant.risk.model;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import java.time.Instant;

/**
 * RiskContext 表示一次风控判定所需的最小上下文。
 */
public record RiskContext(
        PlaceOrderCommand command,
        Instant now,
        String traceId
) {
}
