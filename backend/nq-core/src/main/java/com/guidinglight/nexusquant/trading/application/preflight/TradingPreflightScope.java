package com.guidinglight.nexusquant.trading.application.preflight;

/**
 * TradingPreflightScope 回显本次只读 preflight 诊断范围。
 *
 * <p>Why: 诊断结果必须绑定到 exchange / account / market / symbol / strategy 的明确范围，
 * 避免把 broad diagnostic 误读为某个真实账户或真实交易所已经获得交易授权。
 */
public record TradingPreflightScope(
        String exchangeCode,
        Long accountId,
        String marketType,
        String symbol,
        String strategyId
) {
}
