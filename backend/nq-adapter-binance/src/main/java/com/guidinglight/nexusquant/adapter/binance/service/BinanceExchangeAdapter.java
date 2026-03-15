package com.guidinglight.nexusquant.adapter.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceTradeFill;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceTrimResult;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * BinanceExchangeAdapter 是 GateC-2 的 Binance Spot REST-only 适配实现。
 * <p>
 * Why:
 * Binance 的 symbol、filters、签名与错误码都属于交易所方言，必须封装在 `adapter-binance` 内。
 * core 只能看到统一的 `AdapterOrderAck / AdapterOrderSnapshot` 语义，不能感知 `orderId / clientOrderId`
 * 之外的 Binance 细节，更不能自己拼接 query 或处理 filters。
 */
public class BinanceExchangeAdapter implements TradingAdapter {

    private static final String VENUE = "BINANCE";
    private static final String ORDER_ENDPOINT = "/api/v3/order";
    private static final String OPEN_ORDERS_ENDPOINT = "/api/v3/openOrders";
    private static final String MY_TRADES_ENDPOINT = "/api/v3/myTrades";

    private final BinanceHttpClient authenticatedHttpClient;
    private final BinanceFiltersCache filtersCache;
    private final BinanceOrderTrimmer orderTrimmer;
    private final Clock clock;

    /**
     * 默认构造器供应用装配使用。
     * <p>
     * Why:
     * 当前工程仍使用 `ModuleWiringConfiguration` 手工 new 出 adapter，
     * 因此需要在 adapter 模块内部完成运行时配置、public metadata client 与 authenticated client 的最小装配。
     */
    public BinanceExchangeAdapter() {
        this(createDefaultDependencies());
    }

    /**
     * 可测试构造器，允许单测注入固定时钟、本地 mock server 与 stub cache。
     */
    public BinanceExchangeAdapter(Dependencies dependencies) {
        this.authenticatedHttpClient = Objects.requireNonNull(
                dependencies.authenticatedHttpClient(),
                "authenticatedHttpClient must not be null"
        );
        this.filtersCache = Objects.requireNonNull(dependencies.filtersCache(), "filtersCache must not be null");
        this.orderTrimmer = Objects.requireNonNull(dependencies.orderTrimmer(), "orderTrimmer must not be null");
        this.clock = Objects.requireNonNull(dependencies.clock(), "clock must not be null");
    }

    @Override
    public String venue() {
        return VENUE;
    }

    /**
     * 统一下单入口。
     * <p>
     * Why:
     * GateC-2 明确要求下单前必须完成 Binance filters trim，并在 timeout 时执行 query-confirm。
     * 这些交易所差异都必须留在 adapter 中，不能把 `timestamp/recvWindow/newClientOrderId` 等细节泄漏给 core。
     */
    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        validatePlaceRequest(request);
        BinanceTrimResult trimResult = orderTrimmer.trimAndValidate(request, filtersCache);
        if (!trimResult.accepted()) {
            return rejectedAck(trimResult.rejectCode(), trimResult.rejectReason(), request.traceId());
        }

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", trimResult.exchangeSymbol());
        params.put("side", upper(request.side()));
        params.put("type", upper(request.orderType()));
        params.put("newClientOrderId", request.clientOrderId());
        params.put("quantity", trimResult.trimmedQty().toPlainString());
        if (trimResult.trimmedPrice() != null) {
            params.put("timeInForce", request.timeInForce());
            params.put("price", trimResult.trimmedPrice().toPlainString());
        }

        try {
            JsonNode payload = authenticatedHttpClient.post(ORDER_ENDPOINT, params, true, request.traceId());
            return parsePlaceAck(payload, request.traceId());
        } catch (BinanceApiException ex) {
            if ("HTTP_TIMEOUT".equals(ex.errorCode())) {
                return queryConfirmAfterTimeout(request, trimResult.exchangeSymbol());
            }
            return rejectedAck(resolveErrorCode(ex), resolveErrorMessage(ex), request.traceId());
        }
    }

    /**
     * 统一撤单入口。
     * <p>
     * Why:
     * Binance 支持优先按 `orderId` 撤单，若外部单号缺失再退化到 `origClientOrderId`。
     * 该优先级必须固定在 adapter 内，避免 core 依赖 Binance 参数命名。
     */
    @Override
    public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
        validateCancelRequest(request);
        BinanceSymbolFilters filters = filtersCache.getRequired(request.symbol(), request.traceId());
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", filters.exchangeSymbol());
        if (request.externalOrderId() != null && !request.externalOrderId().isBlank()) {
            params.put("orderId", request.externalOrderId());
        } else {
            params.put("origClientOrderId", request.clientOrderId());
        }

        try {
            JsonNode payload = authenticatedHttpClient.delete(ORDER_ENDPOINT, params, true, request.traceId());
            return parseCancelAck(payload, request.traceId());
        } catch (BinanceApiException ex) {
            if ("HTTP_TIMEOUT".equals(ex.errorCode())) {
                return queryCancelAfterTimeout(request, filters.exchangeSymbol());
            }
            return new AdapterCancelAck(
                    false,
                    venue(),
                    request.externalOrderId(),
                    new AdapterError(resolveErrorCode(ex), resolveErrorMessage(ex), false),
                    Instant.now(clock),
                    request.traceId()
            );
        }
    }

    /**
     * 查询单笔订单，用于 query-confirm 与 reconcile。
     */
    @Override
    public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
        validateOrderQuery(query);
        BinanceSymbolFilters filters = filtersCache.getRequired(query.symbol(), query.traceId());
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", filters.exchangeSymbol());
        if (query.externalOrderId() != null && !query.externalOrderId().isBlank()) {
            params.put("orderId", query.externalOrderId());
        } else {
            params.put("origClientOrderId", query.clientOrderId());
        }
        JsonNode payload = authenticatedHttpClient.get(ORDER_ENDPOINT, params, true, query.traceId());
        return toOrderSnapshot(payload, filters.internalSymbol(), query.accountId(), query.traceId());
    }

    /**
     * 拉取当前未完成订单，用于恢复与对账。
     */
    @Override
    public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        String internalSymbol = null;
        if (query.symbol() != null && !query.symbol().isBlank()) {
            BinanceSymbolFilters filters = filtersCache.getRequired(query.symbol(), query.traceId());
            params.put("symbol", filters.exchangeSymbol());
            internalSymbol = filters.internalSymbol();
        }
        JsonNode payload = authenticatedHttpClient.get(OPEN_ORDERS_ENDPOINT, params, true, query.traceId());
        List<AdapterOrderSnapshot> snapshots = new ArrayList<>();
        for (JsonNode item : payload) {
            String resolvedInternalSymbol = internalSymbol == null
                    ? toInternalSymbol(item.path("symbol").asText(), query.traceId())
                    : internalSymbol;
            snapshots.add(toOrderSnapshot(item, resolvedInternalSymbol, query.accountId(), query.traceId()));
        }
        return snapshots;
    }

    /**
     * 拉取 Binance myTrades，供 REST reconcile 写 trades 并触发 ledger。
     * <p>
     * Why:
     * `TradingAdapter` 接口没有成交拉取方法，但 GateC-2 允许 Binance reconcile 直接依赖 adapter-binance。
     * 该方法仍然把 Binance 字段映射为稳定的 `BinanceTradeFill`，把交易所差异隔离在 adapter 模块内。
     */
    public List<BinanceTradeFill> listTrades(String symbol, String externalOrderId, String traceId) {
        BinanceSymbolFilters filters = filtersCache.getRequired(symbol, traceId);
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", filters.exchangeSymbol());
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            params.put("orderId", externalOrderId);
        }
        JsonNode payload = authenticatedHttpClient.get(MY_TRADES_ENDPOINT, params, true, traceId);
        List<BinanceTradeFill> fills = new ArrayList<>();
        for (JsonNode item : payload) {
            fills.add(new BinanceTradeFill(
                    item.path("id").asText(),
                    item.path("orderId").asText(),
                    filters.exchangeSymbol(),
                    filters.internalSymbol(),
                    item.path("isBuyer").asBoolean(false) ? "BUY" : "SELL",
                    decimal(item, "price"),
                    decimal(item, "qty"),
                    decimalOrZero(item, "commission"),
                    blankToNull(item.path("commissionAsset").asText()),
                    Instant.ofEpochMilli(item.path("time").asLong())
            ));
        }
        return fills;
    }

    /**
     * 暴露 filters cache 供运行态诊断/测试复用。
     */
    public BinanceFiltersCache filtersCache() {
        return filtersCache;
    }

    private AdapterOrderAck queryConfirmAfterTimeout(AdapterOrderRequest request, String exchangeSymbol) {
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    null,
                    request.traceId()
            ));
            if (snapshot.externalOrderId() != null && !snapshot.externalOrderId().isBlank()) {
                return new AdapterOrderAck(
                        true,
                        venue(),
                        request.accountId(),
                        request.symbol(),
                        request.clientOrderId(),
                        snapshot.externalOrderId(),
                        snapshot.externalStatus(),
                        null,
                        Instant.now(clock),
                        snapshot.rawPayload(),
                        request.traceId()
                );
            }
        } catch (RuntimeException ignored) {
            // Why: timeout 后优先查单；若查单失败，再退化到 open orders，最终仍不能盲重试。
        }
        try {
            for (AdapterOrderSnapshot snapshot : listOpenOrders(new AdapterOpenOrdersQuery(
                    request.accountId(),
                    venue(),
                    request.symbol(),
                    request.traceId()
            ))) {
                if (request.clientOrderId().equals(snapshot.clientOrderId())
                        && snapshot.externalOrderId() != null
                        && !snapshot.externalOrderId().isBlank()) {
                    return new AdapterOrderAck(
                            true,
                            venue(),
                            request.accountId(),
                            request.symbol(),
                            request.clientOrderId(),
                            snapshot.externalOrderId(),
                            snapshot.externalStatus(),
                            null,
                            Instant.now(clock),
                            snapshot.rawPayload(),
                            request.traceId()
                    );
                }
            }
        } catch (RuntimeException ignored) {
            // Why: query-confirm 只做事实确认；若 open orders 同样失败，则返回未确认拒绝，交由恢复/重试策略处理。
        }
        return rejectedAck(
                "BINANCE_PLACE_TIMEOUT_UNCONFIRMED",
                "Binance placeOrder timed out and query-confirm found no external order, symbol=" + exchangeSymbol,
                request.traceId()
        );
    }

    private AdapterCancelAck queryCancelAfterTimeout(AdapterCancelRequest request, String exchangeSymbol) {
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.traceId()
            ));
            if ("CANCELLED".equals(snapshot.externalStatus())) {
                return new AdapterCancelAck(true, venue(), snapshot.externalOrderId(), null, Instant.now(clock), request.traceId());
            }
        } catch (RuntimeException ignored) {
            // Why: cancel timeout 后优先查单；若失败则按未确认拒绝返回，等待恢复流程继续推进。
        }
        return new AdapterCancelAck(
                false,
                venue(),
                request.externalOrderId(),
                new AdapterError(
                        "BINANCE_CANCEL_TIMEOUT_UNCONFIRMED",
                        "Binance cancelOrder timed out and query-confirm did not reach CANCELLED, symbol=" + exchangeSymbol,
                        false
                ),
                Instant.now(clock),
                request.traceId()
        );
    }

    private AdapterOrderAck parsePlaceAck(JsonNode payload, String traceId) {
        String externalOrderId = payload.path("orderId").asText(null);
        if (externalOrderId == null || externalOrderId.isBlank()) {
            return rejectedAck("BINANCE_PLACE_RESPONSE_INVALID", "Binance place response missing orderId", traceId);
        }
        return new AdapterOrderAck(
                true,
                venue(),
                null,
                blankToNull(payload.path("symbol").asText()),
                blankToNull(payload.path("clientOrderId").asText()),
                externalOrderId,
                mapOrderStatus(payload.path("status").asText("NEW")),
                null,
                Instant.now(clock),
                payload.toString(),
                traceId
        );
    }

    private AdapterCancelAck parseCancelAck(JsonNode payload, String traceId) {
        String externalOrderId = payload.path("orderId").asText(null);
        return new AdapterCancelAck(true, venue(), externalOrderId, null, Instant.now(clock), traceId);
    }

    private AdapterOrderSnapshot toOrderSnapshot(JsonNode payload, String internalSymbol, Long accountId, String traceId) {
        return new AdapterOrderSnapshot(
                accountId,
                venue(),
                internalSymbol,
                blankToNull(payload.path("clientOrderId").asText()),
                blankToNull(payload.path("orderId").asText()),
                mapOrderStatus(payload.path("status").asText()),
                blankToNullDecimal(payload, "price"),
                blankToNullDecimal(payload, "origQty"),
                blankToNullDecimal(payload, "executedQty"),
                calculateAveragePrice(payload),
                Instant.now(clock),
                payload.toString(),
                traceId
        );
    }

    private String mapOrderStatus(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase(Locale.ROOT)) {
            case "NEW" -> "ACCEPTED";
            case "PARTIALLY_FILLED" -> "PARTIALLY_FILLED";
            case "FILLED" -> "FILLED";
            case "CANCELED", "CANCELLED" -> "CANCELLED";
            case "REJECTED", "EXPIRED", "EXPIRED_IN_MATCH", "PENDING_CANCEL" -> "REJECTED";
            default -> "SENT";
        };
    }

    private void validatePlaceRequest(AdapterOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.accountId() == null || request.accountId() <= 0) {
            throw new IllegalArgumentException("request.accountId must be positive");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("request.symbol must not be blank");
        }
        if (request.clientOrderId() == null || request.clientOrderId().isBlank()) {
            throw new IllegalArgumentException("request.clientOrderId must not be blank");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("request.quantity must be positive");
        }
    }

    private void validateCancelRequest(AdapterCancelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("request.symbol must not be blank");
        }
        if ((request.externalOrderId() == null || request.externalOrderId().isBlank())
                && (request.clientOrderId() == null || request.clientOrderId().isBlank())) {
            throw new IllegalArgumentException("cancel requires externalOrderId or clientOrderId");
        }
    }

    private void validateOrderQuery(AdapterOrderQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.symbol() == null || query.symbol().isBlank()) {
            throw new IllegalArgumentException("query.symbol must not be blank");
        }
        if ((query.externalOrderId() == null || query.externalOrderId().isBlank())
                && (query.clientOrderId() == null || query.clientOrderId().isBlank())) {
            throw new IllegalArgumentException("getOrder requires externalOrderId or clientOrderId");
        }
    }

    private AdapterOrderAck rejectedAck(String code, String message, String traceId) {
        return new AdapterOrderAck(
                false,
                venue(),
                null,
                null,
                null,
                null,
                "REJECTED",
                new AdapterError(code, message, false),
                Instant.now(clock),
                null,
                traceId
        );
    }

    private String resolveErrorCode(BinanceApiException exception) {
        return exception.errorCode() == null || exception.errorCode().isBlank()
                ? "BINANCE_ADAPTER_ERROR"
                : exception.errorCode();
    }

    private String resolveErrorMessage(BinanceApiException exception) {
        return exception.errorMessage() == null || exception.errorMessage().isBlank()
                ? exception.getMessage()
                : exception.errorMessage();
    }

    private String toInternalSymbol(String exchangeSymbol, String traceId) {
        return filtersCache.getRequired(exchangeSymbol, traceId).internalSymbol();
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(JsonNode payload, String fieldName) {
        return new BigDecimal(payload.path(fieldName).asText());
    }

    private static BigDecimal decimalOrZero(JsonNode payload, String fieldName) {
        String raw = payload.path(fieldName).asText();
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw).abs();
    }

    private static BigDecimal blankToNullDecimal(JsonNode payload, String fieldName) {
        String raw = payload.path(fieldName).asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw).abs();
    }

    private static BigDecimal calculateAveragePrice(JsonNode payload) {
        BigDecimal executedQty = blankToNullDecimal(payload, "executedQty");
        BigDecimal cumulativeQuoteQty = blankToNullDecimal(payload, "cummulativeQuoteQty");
        if (executedQty == null || executedQty.compareTo(BigDecimal.ZERO) <= 0 || cumulativeQuoteQty == null) {
            return null;
        }
        return cumulativeQuoteQty.divide(executedQty, 8, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static Dependencies createDefaultDependencies() {
        Clock clock = Clock.systemUTC();
        ObjectMapper objectMapper = new ObjectMapper();
        BinanceRuntimeConfig runtimeConfig = BinanceRuntimeConfig.fromSystemEnv();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(runtimeConfig.timeout()).build();
        BinanceApiCredentials credentials = runtimeConfig.credentials();
        BinanceTimestampProvider signedTimestampProvider = new BinanceSynchronizedTimestampProvider(
                httpClient,
                objectMapper,
                runtimeConfig.baseUrl(),
                runtimeConfig.timeout(),
                clock,
                runtimeConfig.signedTimestampOffset().toMillis()
        );
        BinanceHttpClient authenticatedClient = new BinanceHttpClient(
                httpClient,
                objectMapper,
                runtimeConfig.baseUrl(),
                runtimeConfig.timeout(),
                new BinanceRequestSigner(),
                signedTimestampProvider,
                credentials
        );
        BinanceHttpClient publicClient = new BinanceHttpClient(
                httpClient,
                objectMapper,
                runtimeConfig.baseUrl(),
                runtimeConfig.timeout(),
                new BinanceRequestSigner(),
                System::currentTimeMillis,
                new BinanceApiCredentials("", "")
        );
        BinanceFiltersCache filtersCache = new BinanceFiltersCache(
                new BinanceExchangeInfoClient(publicClient),
                clock,
                runtimeConfig.exchangeInfoRefreshInterval()
        );
        return new Dependencies(authenticatedClient, filtersCache, new BinanceOrderTrimmer(), clock);
    }

    /**
     * Dependencies 聚合可注入依赖，避免测试为单个 mock 反射改字段。
     */
    public record Dependencies(
            BinanceHttpClient authenticatedHttpClient,
            BinanceFiltersCache filtersCache,
            BinanceOrderTrimmer orderTrimmer,
            Clock clock
    ) {
    }
}
