package com.guidinglight.nexusquant.trading.application.port;

import java.time.Instant;

/**
 * TradingOrderStatusSnapshot 描述边界层视角下的订单状态快照。
 * <p>
 * Why:
 * scheduler/paper matching 只需要判断外部状态是否已经与本地状态对齐，
 * 因此这里暴露最小快照，不把 adapter 查单协议泄漏到上游调用方。
 *
 * @param exchangeOrderId 外部订单号，可空
 * @param externalStatus  外部状态，可空
 * @param resultCategory  调用结果分类
 * @param failure         失败信息，可空
 * @param observedAt      快照时间，可空
 * @param tradeEnv        交易环境，可空
 */
public record TradingOrderStatusSnapshot(
        String exchangeOrderId,
        String externalStatus,
        TradingGatewayResultCategory resultCategory,
        TradingGatewayFailure failure,
        Instant observedAt,
        String tradeEnv
) {
}
