package com.guidinglight.nexusquant.adapter.binance.model;

import java.math.BigDecimal;

/**
 * BinanceSymbolFilters 聚合单个 symbol 在 exchangeInfo 中与下单合法性直接相关的过滤器。
 * <p>
 * Why:
 * Binance 的价格、数量、最小名义金额规则分散在多个 filterType 中。
 * 这里先把与 GateC-2 下单前 trim 强相关的字段收敛成一个只读模型，
 * 后续 TradingAdapter 只消费统一的规则对象，而不直接解析原始 JSON。
 *
 * @param exchangeSymbol             Binance 交易所原生 symbol，例如 BTCUSDT
 * @param internalSymbol             内部统一 symbol，例如 BTC-USDT
 * @param status                     Binance symbol 状态，例如 TRADING
 * @param tickSize                   PRICE_FILTER.tickSize
 * @param minPrice                   PRICE_FILTER.minPrice
 * @param maxPrice                   PRICE_FILTER.maxPrice
 * @param stepSize                   LOT_SIZE.stepSize
 * @param minQty                     LOT_SIZE.minQty
 * @param maxQty                     LOT_SIZE.maxQty
 * @param marketStepSize             MARKET_LOT_SIZE.stepSize，可为空
 * @param marketMinQty               MARKET_LOT_SIZE.minQty，可为空
 * @param marketMaxQty               MARKET_LOT_SIZE.maxQty，可为空
 * @param minNotional                MIN_NOTIONAL/NOTIONAL 约束的最小名义金额，可为空
 * @param maxNotional                NOTIONAL 约束的最大名义金额，可为空
 * @param minNotionalAppliesToMarket 最小名义金额是否作用于 MARKET
 * @param maxNotionalAppliesToMarket 最大名义金额是否作用于 MARKET
 */
public record BinanceSymbolFilters(
        String exchangeSymbol,
        String internalSymbol,
        String status,
        BigDecimal tickSize,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal stepSize,
        BigDecimal minQty,
        BigDecimal maxQty,
        BigDecimal marketStepSize,
        BigDecimal marketMinQty,
        BigDecimal marketMaxQty,
        BigDecimal minNotional,
        BigDecimal maxNotional,
        boolean minNotionalAppliesToMarket,
        boolean maxNotionalAppliesToMarket
) {

    /**
     * Why:
     * 只有 TRADING 状态的 symbol 才允许进入后续 trim 与下单链路；
     * BREAK/HALT/PRE_TRADING 等状态必须在 adapter 层先拦截，避免 core 感知交易所方言。
     */
    public boolean isTrading() {
        return "TRADING".equalsIgnoreCase(status);
    }

    /**
     * 根据订单类型选择实际生效的数量步长。
     * <p>
     * Why:
     * Binance Testnet 的 `MARKET_LOT_SIZE.stepSize` 可能返回 `0`，这表示当前 symbol
     * 不额外提供 market 专用步长，而不是允许用 0 做截断。
     * 因此这里必须在 `marketStepSize <= 0` 时回退到 `LOT_SIZE.stepSize`，
     * 否则 MARKET 单会在 trim 阶段因为除以 0 被本地错误拒绝。
     */
    public BigDecimal effectiveStepSize(String orderType) {
        if (isMarketOrder(orderType)
                && marketStepSize != null
                && marketStepSize.compareTo(BigDecimal.ZERO) > 0) {
            return marketStepSize;
        }
        return stepSize;
    }

    /**
     * 根据订单类型选择实际生效的最小数量。
     */
    public BigDecimal effectiveMinQty(String orderType) {
        if (isMarketOrder(orderType) && marketMinQty != null) {
            return marketMinQty;
        }
        return minQty;
    }

    /**
     * 根据订单类型选择实际生效的最大数量。
     */
    public BigDecimal effectiveMaxQty(String orderType) {
        if (isMarketOrder(orderType) && marketMaxQty != null) {
            return marketMaxQty;
        }
        return maxQty;
    }

    /**
     * 最小名义金额是否应该在当前订单类型上校验。
     */
    public boolean shouldValidateMinNotional(String orderType) {
        return minNotional != null && (!isMarketOrder(orderType) || minNotionalAppliesToMarket);
    }

    /**
     * 最大名义金额是否应该在当前订单类型上校验。
     */
    public boolean shouldValidateMaxNotional(String orderType) {
        return maxNotional != null && (!isMarketOrder(orderType) || maxNotionalAppliesToMarket);
    }

    private boolean isMarketOrder(String orderType) {
        return orderType != null && "MARKET".equalsIgnoreCase(orderType);
    }
}
