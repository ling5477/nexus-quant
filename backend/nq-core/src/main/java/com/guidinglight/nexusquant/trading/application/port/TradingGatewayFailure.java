package com.guidinglight.nexusquant.trading.application.port;

/**
 * TradingGatewayFailure 描述 trading 边界层返回的统一失败信息。
 * <p>
 * Why:
 * application 层只需要稳定的 `code/message/retryable` 语义做审计与状态决策，
 * 不应继续携带 adapter 专属错误结构。
 *
 * @param code      统一失败码
 * @param message   可审计失败说明
 * @param retryable 是否允许后续补偿路径继续尝试
 */
public record TradingGatewayFailure(
        String code,
        String message,
        boolean retryable
) {
}
