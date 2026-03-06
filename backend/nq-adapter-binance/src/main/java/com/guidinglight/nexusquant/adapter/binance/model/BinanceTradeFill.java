package com.guidinglight.nexusquant.adapter.binance.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BinanceTradeFill 表示 Binance `/api/v3/myTrades` 返回的一笔成交事实。
 *
 * Why:
 * GateC-2 的 REST reconcile 必须把 Binance 原始成交事实与 scheduler/ledger 解耦。
 * 这里保留 tradeId/orderId/fee/commissionAsset 等关键信息，后续 `BinanceRestReconcileService`
 * 只消费稳定模型，不直接依赖原始 JSON 字段名。
 *
 * @param exchangeTradeId Binance 成交 ID（myTrades.id）
 * @param externalOrderId Binance 订单 ID（myTrades.orderId）
 * @param exchangeSymbol Binance 原生 symbol，例如 BTCUSDT
 * @param internalSymbol 内部统一 symbol，例如 BTC-USDT
 * @param side 成交方向，统一为 BUY/SELL
 * @param price 成交价格
 * @param qty 成交数量
 * @param fee 手续费，统一为非负金额
 * @param feeCurrency 手续费币种
 * @param ts 成交时间
 */
public record BinanceTradeFill(
        String exchangeTradeId,
        String externalOrderId,
        String exchangeSymbol,
        String internalSymbol,
        String side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        Instant ts
) {
}
