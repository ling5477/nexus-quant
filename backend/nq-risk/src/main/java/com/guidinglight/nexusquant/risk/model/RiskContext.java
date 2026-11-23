package com.guidinglight.nexusquant.risk.model;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import java.time.Instant;

/**
 * RiskContext 表示一次风控判定所需的最小上下文。
 */
public record RiskContext(
        PlaceOrderCommand command,
        Instant now,
        String traceId,
        TradeEnvironment tradeEnvironment
) {
    /** 旧的无环境调用按未知环境处理，绝不推断为 SIM。 */
    public RiskContext(PlaceOrderCommand command, Instant now, String traceId) {
        this(command, now, traceId, TradeEnvironment.UNKNOWN);
    }
}
