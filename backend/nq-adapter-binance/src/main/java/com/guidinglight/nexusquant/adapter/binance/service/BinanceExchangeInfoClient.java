package com.guidinglight.nexusquant.adapter.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * BinanceExchangeInfoClient 负责拉取并解析 Binance Spot `exchangeInfo`。
 * <p>
 * Why:
 * GateC-2 的下单前 trim 依赖交易所公开 filters，而这些规则在 Binance 中以 exchangeInfo + filters 数组表达。
 * 这里把原始 JSON 解析收敛到 adapter-binance 内，避免后续 TradingAdapter 再次散落处理交易所方言。
 */
public class BinanceExchangeInfoClient {

    private static final String EXCHANGE_INFO_ENDPOINT = "/api/v3/exchangeInfo";

    private final BinanceHttpClient publicHttpClient;

    public BinanceExchangeInfoClient(BinanceHttpClient publicHttpClient) {
        this.publicHttpClient = Objects.requireNonNull(publicHttpClient, "publicHttpClient must not be null");
    }

    /**
     * 拉取并解析 Spot exchangeInfo。
     *
     * @param traceId 调用链 traceId，仅用于日志/错误定位
     * @return 以 Binance 原生 symbol 为 key 的 filters 快照
     */
    public Map<String, BinanceSymbolFilters> fetchSpotExchangeInfo(String traceId) {
        JsonNode payload = publicHttpClient.get(EXCHANGE_INFO_ENDPOINT, Map.of(), false, traceId);
        Map<String, BinanceSymbolFilters> filtersBySymbol = new LinkedHashMap<>();
        for (JsonNode symbolNode : payload.path("symbols")) {
            BinanceSymbolFilters filters = toSymbolFilters(symbolNode);
            if (filters != null) {
                filtersBySymbol.put(filters.exchangeSymbol(), filters);
            }
        }
        if (filtersBySymbol.isEmpty()) {
            throw new BinanceApiException(
                    "Binance exchangeInfo returned zero symbol filters, endpoint=" + EXCHANGE_INFO_ENDPOINT + ", trace_id=" + traceId,
                    200,
                    EXCHANGE_INFO_ENDPOINT,
                    "BINANCE_EXCHANGE_INFO_EMPTY",
                    "exchangeInfo returned zero symbols",
                    traceId
            );
        }
        return filtersBySymbol;
    }

    private BinanceSymbolFilters toSymbolFilters(JsonNode symbolNode) {
        String exchangeSymbol = symbolNode.path("symbol").asText();
        String baseAsset = symbolNode.path("baseAsset").asText();
        String quoteAsset = symbolNode.path("quoteAsset").asText();
        if (isBlank(exchangeSymbol) || isBlank(baseAsset) || isBlank(quoteAsset)) {
            return null;
        }

        FilterAccumulator accumulator = new FilterAccumulator();
        for (JsonNode filterNode : symbolNode.path("filters")) {
            accumulator.accept(filterNode);
        }

        if (accumulator.tickSize == null || accumulator.stepSize == null || accumulator.minQty == null) {
            return null;
        }

        return new BinanceSymbolFilters(
                exchangeSymbol,
                baseAsset + "-" + quoteAsset,
                symbolNode.path("status").asText(),
                accumulator.tickSize,
                accumulator.minPrice,
                accumulator.maxPrice,
                accumulator.stepSize,
                accumulator.minQty,
                accumulator.maxQty,
                accumulator.marketStepSize,
                accumulator.marketMinQty,
                accumulator.marketMaxQty,
                accumulator.minNotional,
                accumulator.maxNotional,
                accumulator.minNotionalAppliesToMarket,
                accumulator.maxNotionalAppliesToMarket
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        String raw = node.path(fieldName).asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw);
    }

    private static boolean bool(JsonNode node, String fieldName) {
        return node.has(fieldName) && node.path(fieldName).asBoolean(false);
    }

    /**
     * Why:
     * Binance filters 是异构数组；在单个 symbol 解析期间先聚合到临时结构，
     * 可以显式表达每种 filter 的覆盖优先级，而不把解析逻辑写成难维护的多层 if/else。
     */
    private static final class FilterAccumulator {
        private BigDecimal tickSize;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal stepSize;
        private BigDecimal minQty;
        private BigDecimal maxQty;
        private BigDecimal marketStepSize;
        private BigDecimal marketMinQty;
        private BigDecimal marketMaxQty;
        private BigDecimal minNotional;
        private BigDecimal maxNotional;
        private boolean minNotionalAppliesToMarket;
        private boolean maxNotionalAppliesToMarket;

        private void accept(JsonNode filterNode) {
            String filterType = filterNode.path("filterType").asText();
            switch (filterType) {
                case "PRICE_FILTER" -> {
                    tickSize = decimal(filterNode, "tickSize");
                    minPrice = decimal(filterNode, "minPrice");
                    maxPrice = decimal(filterNode, "maxPrice");
                }
                case "LOT_SIZE" -> {
                    stepSize = decimal(filterNode, "stepSize");
                    minQty = decimal(filterNode, "minQty");
                    maxQty = decimal(filterNode, "maxQty");
                }
                case "MARKET_LOT_SIZE" -> {
                    marketStepSize = decimal(filterNode, "stepSize");
                    marketMinQty = decimal(filterNode, "minQty");
                    marketMaxQty = decimal(filterNode, "maxQty");
                }
                case "MIN_NOTIONAL" -> {
                    minNotional = decimal(filterNode, "minNotional");
                    minNotionalAppliesToMarket = bool(filterNode, "applyToMarket");
                }
                case "NOTIONAL" -> {
                    minNotional = decimal(filterNode, "minNotional");
                    maxNotional = decimal(filterNode, "maxNotional");
                    minNotionalAppliesToMarket = bool(filterNode, "applyMinToMarket");
                    maxNotionalAppliesToMarket = bool(filterNode, "applyMaxToMarket");
                }
                default -> {
                    // Why:
                    // PR-C11 只关心下单前 trim 所需 filters，其余如 ICEBERG_PARTS/MAX_NUM_ORDERS 暂不参与计算。
                }
            }
        }
    }
}
