package com.guidinglight.nexusquant.trading.application.port;

import java.time.Instant;

/**
 * TradingPlaceGatewayResult 表示内部统一的下单边界回执。
 * <p>
 * Why:
 * core 编排只关心“是否 accepted”“是否拿到 external order id”“当前应进入哪种结果分类”，
 * 不应继续感知 adapter 的 request/ack 结构。
 *
 * @param accepted         是否被边界层接受
 * @param exchangeOrderId  外部订单号，可空
 * @param externalStatus   外部状态，可空
 * @param resultCategory   统一结果分类
 * @param failure          失败信息，可空
 * @param acknowledgedAt   回执时间，可空
 * @param tradeEnv         交易环境，可空
 */
public record TradingPlaceGatewayResult(
        boolean accepted,
        String exchangeOrderId,
        String externalStatus,
        TradingGatewayResultCategory resultCategory,
        TradingGatewayFailure failure,
        Instant acknowledgedAt,
        String tradeEnv
) {
}
