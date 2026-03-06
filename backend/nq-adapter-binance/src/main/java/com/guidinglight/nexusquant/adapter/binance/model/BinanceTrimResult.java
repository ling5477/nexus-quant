package com.guidinglight.nexusquant.adapter.binance.model;

import java.math.BigDecimal;

/**
 * BinanceTrimResult 表示下单前 trim 与合法性校验的结果。
 * <p>
 * Why:
 * 本 PR 明确禁止直接写 audit/event_store，因此 trim 阶段只能返回结构化结果，
 * 由后续 TradingAdapter 在真正下单链路中把 reject_code/reject_reason 落到审计证据链。
 *
 * @param accepted       是否通过 trim 与校验
 * @param exchangeSymbol Binance 原生 symbol，例如 BTCUSDT
 * @param internalSymbol 内部统一 symbol，例如 BTC-USDT
 * @param trimmedPrice   按 tickSize 截断后的价格；MARKET 可为空
 * @param trimmedQty     按 stepSize 截断后的数量
 * @param rejectCode     结构化拒因编码
 * @param rejectReason   可读拒因说明
 */
public record BinanceTrimResult(
        boolean accepted,
        String exchangeSymbol,
        String internalSymbol,
        BigDecimal trimmedPrice,
        BigDecimal trimmedQty,
        String rejectCode,
        String rejectReason
) {

    public static BinanceTrimResult accepted(String exchangeSymbol, String internalSymbol, BigDecimal trimmedPrice, BigDecimal trimmedQty) {
        return new BinanceTrimResult(true, exchangeSymbol, internalSymbol, trimmedPrice, trimmedQty, null, null);
    }

    public static BinanceTrimResult rejected(
            String exchangeSymbol,
            String internalSymbol,
            BigDecimal trimmedPrice,
            BigDecimal trimmedQty,
            String rejectCode,
            String rejectReason
    ) {
        return new BinanceTrimResult(false, exchangeSymbol, internalSymbol, trimmedPrice, trimmedQty, rejectCode, rejectReason);
    }
}
