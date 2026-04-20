package com.guidinglight.nexusquant.trading.application.port;

import java.time.Instant;

/**
 * TradingCancelGatewayResult 表示内部统一的撤单边界回执。
 * <p>
 * Why:
 * 撤单链路只需要知道“是否被接受”“失败是否可延迟处理”，
 * 这层语义应该在 anti-corruption boundary 之后就稳定下来。
 *
 * @param accepted       是否被边界层接受
 * @param resultCategory 统一结果分类
 * @param failure        失败信息，可空
 * @param acknowledgedAt 回执时间，可空
 * @param tradeEnv       交易环境，可空
 */
public record TradingCancelGatewayResult(
        boolean accepted,
        TradingGatewayResultCategory resultCategory,
        TradingGatewayFailure failure,
        Instant acknowledgedAt,
        String tradeEnv
) {
}
