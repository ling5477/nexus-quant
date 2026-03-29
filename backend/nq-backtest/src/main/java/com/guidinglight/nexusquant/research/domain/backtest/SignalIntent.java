package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SignalIntent 表示回测执行阶段的最小信号意图。
 * <p>
 * Why:
 * GateF-3 必须把“策略意图”与“模拟成交事实”分层，避免后续策略接入方式变化时直接冲击 sim_order / sim_trade 语义。
 */
public record SignalIntent(
        String backtestRunId,
        String symbol,
        SignalIntentType signalIntentType,
        BigDecimal quantity,
        String reason,
        Instant generatedAt
) {
}

