package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceTrimResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * BinanceOrderTrimmer 负责在真正下单前做本地截断与合法性校验。
 * <p>
 * Why:
 * Binance 的 tickSize / stepSize / notional 校验规则与 OKX 不同，
 * 这些交易所差异必须留在 adapter-binance 内，不能泄漏到 core。
 * 当前返回结构化 TrimResult，后续 PR-C12 再把 reject_code/reject_reason 接到审计与事件链。
 */
public class BinanceOrderTrimmer {

    /**
     * 从 cache 解析 symbol 并执行 trim 与校验。
     * <p>
     * Why:
     * Binance symbol 不存在或内部格式与交易所格式不一致时，拒因也必须由 adapter-binance 给出，
     * 不能把 `BTCUSDT/BTC-USDT` 的差异泄漏到 core。
     */
    public BinanceTrimResult trimAndValidate(AdapterOrderRequest orderDraft, BinanceFiltersCache filtersCache) {
        Objects.requireNonNull(orderDraft, "orderDraft must not be null");
        Objects.requireNonNull(filtersCache, "filtersCache must not be null");
        try {
            return trimAndValidate(orderDraft, filtersCache.getRequired(orderDraft.symbol(), orderDraft.traceId()));
        } catch (BinanceApiException ex) {
            if (!"BINANCE_SYMBOL_FILTERS_NOT_FOUND".equals(ex.errorCode())) {
                throw ex;
            }
            return BinanceTrimResult.rejected(
                    null,
                    orderDraft.symbol(),
                    orderDraft.price(),
                    orderDraft.qty(),
                    "BINANCE_SYMBOL_NOT_FOUND",
                    "symbol filters not found"
            );
        }
    }

    /**
     * 对统一下单草稿执行 trim 与校验。
     *
     * @param orderDraft    统一语义的下单请求，至少包含 symbol/side/type/qty/price
     * @param symbolFilters 该 symbol 对应的 Binance filters
     * @return 可直接供 TradingAdapter.placeOrder 复用的 trim 结果
     */
    public BinanceTrimResult trimAndValidate(AdapterOrderRequest orderDraft, BinanceSymbolFilters symbolFilters) {
        Objects.requireNonNull(orderDraft, "orderDraft must not be null");
        Objects.requireNonNull(symbolFilters, "symbolFilters must not be null");
        if (!symbolFilters.isTrading()) {
            return BinanceTrimResult.rejected(
                    symbolFilters.exchangeSymbol(),
                    symbolFilters.internalSymbol(),
                    orderDraft.price(),
                    orderDraft.qty(),
                    "BINANCE_SYMBOL_NOT_TRADING",
                    "symbol status is not TRADING"
            );
        }
        if (orderDraft.qty() == null || orderDraft.qty().compareTo(BigDecimal.ZERO) <= 0) {
            return BinanceTrimResult.rejected(
                    symbolFilters.exchangeSymbol(),
                    symbolFilters.internalSymbol(),
                    orderDraft.price(),
                    orderDraft.qty(),
                    "BINANCE_QTY_INVALID",
                    "qty must be positive"
            );
        }
        if (requiresPrice(orderDraft.type()) && (orderDraft.price() == null || orderDraft.price().compareTo(BigDecimal.ZERO) <= 0)) {
            return BinanceTrimResult.rejected(
                    symbolFilters.exchangeSymbol(),
                    symbolFilters.internalSymbol(),
                    orderDraft.price(),
                    orderDraft.qty(),
                    "BINANCE_PRICE_REQUIRED",
                    "LIMIT order requires positive price"
            );
        }

        BigDecimal trimmedQty = trimDown(orderDraft.qty(), symbolFilters.effectiveStepSize(orderDraft.type()));
        BigDecimal trimmedPrice = orderDraft.price() == null ? null : trimDown(orderDraft.price(), symbolFilters.tickSize());

        if (trimmedQty.compareTo(symbolFilters.effectiveMinQty(orderDraft.type())) < 0) {
            return BinanceTrimResult.rejected(
                    symbolFilters.exchangeSymbol(),
                    symbolFilters.internalSymbol(),
                    trimmedPrice,
                    trimmedQty,
                    "BINANCE_QTY_BELOW_MIN_QTY",
                    "trimmed qty below minQty"
            );
        }
        if (symbolFilters.effectiveMaxQty(orderDraft.type()) != null
                && trimmedQty.compareTo(symbolFilters.effectiveMaxQty(orderDraft.type())) > 0) {
            return BinanceTrimResult.rejected(
                    symbolFilters.exchangeSymbol(),
                    symbolFilters.internalSymbol(),
                    trimmedPrice,
                    trimmedQty,
                    "BINANCE_QTY_ABOVE_MAX_QTY",
                    "trimmed qty above maxQty"
            );
        }
        if (trimmedPrice != null) {
            if (symbolFilters.minPrice() != null && trimmedPrice.compareTo(symbolFilters.minPrice()) < 0) {
                return BinanceTrimResult.rejected(
                        symbolFilters.exchangeSymbol(),
                        symbolFilters.internalSymbol(),
                        trimmedPrice,
                        trimmedQty,
                        "BINANCE_PRICE_BELOW_MIN_PRICE",
                        "trimmed price below minPrice"
                );
            }
            if (symbolFilters.maxPrice() != null && trimmedPrice.compareTo(symbolFilters.maxPrice()) > 0) {
                return BinanceTrimResult.rejected(
                        symbolFilters.exchangeSymbol(),
                        symbolFilters.internalSymbol(),
                        trimmedPrice,
                        trimmedQty,
                        "BINANCE_PRICE_ABOVE_MAX_PRICE",
                        "trimmed price above maxPrice"
                );
            }
        }

        // Why:
        // Binance 的 MARKET notional 可能依赖交易所滚动均价；无实时行情时无法在本地精确推导。
        // 因此当前 PR 仅在存在 price 的情况下校验 notional，下个闭环 PR 再结合实时 quote 做更强约束。
        if (trimmedPrice != null) {
            BigDecimal notional = trimmedPrice.multiply(trimmedQty);
            if (symbolFilters.shouldValidateMinNotional(orderDraft.type())
                    && notional.compareTo(symbolFilters.minNotional()) < 0) {
                return BinanceTrimResult.rejected(
                        symbolFilters.exchangeSymbol(),
                        symbolFilters.internalSymbol(),
                        trimmedPrice,
                        trimmedQty,
                        "BINANCE_NOTIONAL_BELOW_MIN_NOTIONAL",
                        "order notional below minNotional"
                );
            }
            if (symbolFilters.shouldValidateMaxNotional(orderDraft.type())
                    && notional.compareTo(symbolFilters.maxNotional()) > 0) {
                return BinanceTrimResult.rejected(
                        symbolFilters.exchangeSymbol(),
                        symbolFilters.internalSymbol(),
                        trimmedPrice,
                        trimmedQty,
                        "BINANCE_NOTIONAL_ABOVE_MAX_NOTIONAL",
                        "order notional above maxNotional"
                );
            }
        }

        return BinanceTrimResult.accepted(symbolFilters.exchangeSymbol(), symbolFilters.internalSymbol(), trimmedPrice, trimmedQty);
    }

    private boolean requiresPrice(String orderType) {
        return !"MARKET".equalsIgnoreCase(orderType);
    }

    private BigDecimal trimDown(BigDecimal value, BigDecimal increment) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(increment, "increment must not be null");
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("increment must be positive");
        }
        BigDecimal units = value.divide(increment, 0, RoundingMode.DOWN);
        return units.multiply(increment).stripTrailingZeros();
    }
}
