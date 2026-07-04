package com.guidinglight.nexusquant.trading.application.preflight;

/**
 * TradingPreflightReadinessQuery 表示真实交易前置诊断的只读筛选条件。
 *
 * <p>Why: GateP Batch 4 需要让用户看到单交易所账号、credential metadata、权限探活与风控前置
 * 当前为什么不能放行真实交易。该 query 只承载筛选条件，不携带 credential material、订单意图或任何写操作字段。
 */
public record TradingPreflightReadinessQuery(
        Long ownerUserId,
        String exchangeCode,
        Long accountId,
        String marketType,
        String symbol,
        String strategyId
) {
}
