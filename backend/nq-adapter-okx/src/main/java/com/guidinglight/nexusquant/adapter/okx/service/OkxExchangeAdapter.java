package com.guidinglight.nexusquant.adapter.okx.service;

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
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.adapter.okx.model.OkxFillRecord;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;
import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * OkxExchangeAdapter 是 GateC-1 的 OKX Spot REST-only 实现。
 * <p>
 * Why:
 * 交易所方言只能存在于 adapter-*，因此 OKX 的签名、字段映射、超时 query-confirm、
 * instruments trim 都必须在这里完成，core 只消费统一的 adapter-api 语义。
 */
public class OkxExchangeAdapter implements TradingAdapter {

    private static final String VENUE = "OKX";
    private static final String TRADE_ORDER_ENDPOINT = "/api/v5/trade/order";
    private static final String CANCEL_ORDER_ENDPOINT = "/api/v5/trade/cancel-order";
    private static final String ORDER_DETAIL_ENDPOINT = "/api/v5/trade/order";
    private static final String OPEN_ORDERS_ENDPOINT = "/api/v5/trade/orders-pending";
    private static final String FILLS_ENDPOINT = "/api/v5/trade/fills";
    private static final String OKX_SIMULATED_TRADING_HEADER = "x-simulated-trading";

    private final OkxHttpClient authenticatedHttpClient;
    private final OkxInstrumentsCache instrumentsCache;
    private final Clock clock;

    /**
     * 默认构造器供应用装配使用。
     * <p>
     * Why:
     * 当前工程仍由 `ModuleWiringConfiguration` 手工 new 出 adapter，
     * 因此这里需要提供一个可直接从环境变量启动的最小可运行配置。
     */
    public OkxExchangeAdapter() {
        this(createDefaultDependencies());
    }

    /**
     * 可测试构造器，允许在单测中注入固定时钟与本地 mock server。
     */
    public OkxExchangeAdapter(Dependencies dependencies) {
        Objects.requireNonNull(dependencies.objectMapper(), "objectMapper must not be null");
        this.authenticatedHttpClient = Objects.requireNonNull(
                dependencies.authenticatedHttpClient(),
                "authenticatedHttpClient must not be null"
        );
        this.instrumentsCache = Objects.requireNonNull(dependencies.instrumentsCache(), "instrumentsCache must not be null");
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
     * GateC-1 要在 adapter 内完成 instruments trim、OKX 字段映射与 query-confirm，
     * 避免 core 产生任何 OKX 方言或盲重试逻辑。
     */
    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        validatePlaceRequest(request);
        OkxInstrument instrument = instrumentsCache.getRequired(request.symbol(), request.traceId());
        AdapterError validationError = validateInstrumentTradable(instrument, request.traceId());
        if (validationError != null) {
            return rejectedAck(validationError, request.traceId());
        }

        BigDecimal trimmedQty = trim(request.qty(), instrument.lotSize(), NumericType.QTY);
        if (trimmedQty.compareTo(instrument.minSize()) < 0) {
            return rejectedAck(
                    new AdapterError(
                            "OKX_QTY_BELOW_MIN_SIZE",
                            "trimmed qty below minSz, symbol=" + request.symbol() + ", minSz=" + instrument.minSize(),
                            false
                    ),
                    request.traceId()
            );
        }
        BigDecimal trimmedPrice = request.price() == null
                ? null
                : trim(request.price(), instrument.tickSize(), NumericType.PRICE);
        String body = buildPlaceOrderBody(request, trimmedPrice, trimmedQty);
        try {
            JsonNode payload = authenticatedHttpClient.post(TRADE_ORDER_ENDPOINT, body, request.traceId());
            return parsePlaceOrderAck(payload, request.traceId());
        } catch (OkxApiException ex) {
            if ("HTTP_TIMEOUT".equals(ex.errorCode())) {
                return queryConfirmAfterTimeout(request);
            }
            return rejectedAck(new AdapterError(resolveErrorCode(ex), ex.getMessage(), false), request.traceId());
        }
    }

    /**
     * 统一撤单入口。
     */
    @Override
    public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
        validateCancelRequest(request);
        String body = buildCancelOrderBody(request);
        try {
            JsonNode payload = authenticatedHttpClient.post(CANCEL_ORDER_ENDPOINT, body, request.traceId());
            return parseCancelAck(payload, request.traceId());
        } catch (OkxApiException ex) {
            if ("HTTP_TIMEOUT".equals(ex.errorCode())) {
                return queryCancelAfterTimeout(request);
            }
            return new AdapterCancelAck(
                    false,
                    venue(),
                    request.externalOrderId(),
                    new AdapterError(resolveErrorCode(ex), ex.getMessage(), false),
                    Instant.now(clock),
                    request.traceId()
            );
        }
    }

    /**
     * 查询单笔订单，用于 query-confirm 与同步器状态推进。
     */
    @Override
    public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
        validateOrderQuery(query);
        String endpoint = ORDER_DETAIL_ENDPOINT + "?instId=" + query.symbol() + buildOrderIdentityQuery(query);
        JsonNode payload = authenticatedHttpClient.get(endpoint, query.traceId());
        JsonNode item = requireSingleDataItem(payload, endpoint, query.traceId());
        return toOrderSnapshot(item, query.accountId(), query.traceId());
    }

    /**
     * 拉取当前挂单，用于恢复与 reconcile。
     */
    @Override
    public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
        StringBuilder endpoint = new StringBuilder(OPEN_ORDERS_ENDPOINT).append("?instType=SPOT");
        if (query.symbol() != null && !query.symbol().isBlank()) {
            endpoint.append("&instId=").append(query.symbol());
        }
        JsonNode payload = authenticatedHttpClient.get(endpoint.toString(), query.traceId());
        ensureSuccessEnvelope(payload, endpoint.toString(), query.traceId());
        List<AdapterOrderSnapshot> snapshots = new ArrayList<>();
        for (JsonNode item : payload.path("data")) {
            snapshots.add(toOrderSnapshot(item, query.accountId(), query.traceId()));
        }
        return snapshots;
    }

    /**
     * 拉取 fills，供 OKX REST reconcile 与恢复流程使用。
     * <p>
     * Why:
     * `TradingAdapter` 接口不包含 fills，但 GateC-1 的同步器明确允许依赖 `adapter-okx`，
     * 因此这里暴露 OKX 专属方法给 scheduler 消费，仍然把方言隔离在 adapter 模块内。
     */
    public List<OkxFillRecord> listFills(String symbol, String externalOrderId, String traceId) {
        StringBuilder endpoint = new StringBuilder(FILLS_ENDPOINT)
                .append("?instType=SPOT")
                .append("&instId=").append(symbol);
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            endpoint.append("&ordId=").append(externalOrderId);
        }
        JsonNode payload = authenticatedHttpClient.get(endpoint.toString(), traceId);
        ensureSuccessEnvelope(payload, endpoint.toString(), traceId);
        List<OkxFillRecord> fills = new ArrayList<>();
        for (JsonNode item : payload.path("data")) {
            fills.add(new OkxFillRecord(
                    text(item, "tradeId", "fillId", "billId"),
                    item.path("ordId").asText(),
                    item.path("instId").asText(),
                    upper(item.path("side").asText("BUY")),
                    decimal(item, "fillPx", "px"),
                    decimal(item, "fillSz", "fillQty", "sz"),
                    decimalOrZero(item, "fee"),
                    text(item, "feeCcy", "feeCurrency"),
                    Instant.ofEpochMilli(item.path("ts").asLong())
            ));
        }
        return fills;
    }

    /**
     * 导出 instruments 快照，供测试与诊断使用。
     */
    public OkxInstrumentsCache instrumentsCache() {
        return instrumentsCache;
    }

    private AdapterOrderAck queryConfirmAfterTimeout(AdapterOrderRequest request) {
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    null,
                    request.traceId()
            ));
            return new AdapterOrderAck(true, venue(), snapshot.externalOrderId(), null, Instant.now(clock), request.traceId());
        } catch (RuntimeException ignored) {
            for (AdapterOrderSnapshot snapshot : listOpenOrders(new AdapterOpenOrdersQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.traceId()
            ))) {
                if (request.clientOrderId().equals(snapshot.clientOrderId())) {
                    return new AdapterOrderAck(
                            true,
                            venue(),
                            snapshot.externalOrderId(),
                            null,
                            Instant.now(clock),
                            request.traceId()
                    );
                }
            }
            return rejectedAck(
                    new AdapterError(
                            "OKX_PLACE_TIMEOUT_UNCONFIRMED",
                            "OKX placeOrder timed out and query-confirm found no external order, trace_id=" + request.traceId(),
                            false
                    ),
                    request.traceId()
            );
        }
    }

    private AdapterCancelAck queryCancelAfterTimeout(AdapterCancelRequest request) {
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.traceId()
            ));
            if ("CANCELLED".equals(snapshot.status())) {
                return new AdapterCancelAck(true, venue(), snapshot.externalOrderId(), null, Instant.now(clock), request.traceId());
            }
        } catch (RuntimeException ignored) {
            // Why: timeout 后优先尝试 query-confirm；若查单本身失败，则按拒绝返回，交由恢复流程继续推进。
        }
        return new AdapterCancelAck(
                false,
                venue(),
                request.externalOrderId(),
                new AdapterError(
                        "OKX_CANCEL_TIMEOUT_UNCONFIRMED",
                        "OKX cancelOrder timed out and query-confirm did not reach CANCELLED, trace_id=" + request.traceId(),
                        false
                ),
                Instant.now(clock),
                request.traceId()
        );
    }

    private AdapterOrderAck parsePlaceOrderAck(JsonNode payload, String traceId) {
        ensureSuccessEnvelope(payload, TRADE_ORDER_ENDPOINT, traceId);
        JsonNode item = requireSingleDataItem(payload, TRADE_ORDER_ENDPOINT, traceId);
        String sCode = item.path("sCode").asText("0");
        if (!"0".equals(sCode)) {
            return rejectedAck(new AdapterError(sCode, item.path("sMsg").asText("OKX order rejected"), false), traceId);
        }
        return new AdapterOrderAck(
                true,
                venue(),
                item.path("ordId").asText(),
                null,
                Instant.now(clock),
                traceId
        );
    }

    private AdapterCancelAck parseCancelAck(JsonNode payload, String traceId) {
        ensureSuccessEnvelope(payload, CANCEL_ORDER_ENDPOINT, traceId);
        JsonNode item = requireSingleDataItem(payload, CANCEL_ORDER_ENDPOINT, traceId);
        String sCode = item.path("sCode").asText("0");
        if (!"0".equals(sCode)) {
            return new AdapterCancelAck(
                    false,
                    venue(),
                    item.path("ordId").asText(null),
                    new AdapterError(sCode, item.path("sMsg").asText("OKX cancel rejected"), false),
                    Instant.now(clock),
                    traceId
            );
        }
        return new AdapterCancelAck(
                true,
                venue(),
                item.path("ordId").asText(null),
                null,
                Instant.now(clock),
                traceId
        );
    }

    private AdapterOrderSnapshot toOrderSnapshot(JsonNode item, Long accountId, String traceId) {
        return new AdapterOrderSnapshot(
                accountId,
                venue(),
                item.path("instId").asText(),
                item.path("clOrdId").asText(null),
                item.path("ordId").asText(null),
                mapOrderState(item.path("state").asText()),
                traceId
        );
    }

    private String mapOrderState(String okxState) {
        return switch (okxState == null ? "" : okxState.trim().toLowerCase(Locale.ROOT)) {
            case "live", "effective" -> "ACCEPTED";
            case "partially_filled" -> "PARTIALLY_FILLED";
            case "filled" -> "FILLED";
            case "canceled", "cancelled" -> "CANCELLED";
            case "order_failed", "rejected" -> "REJECTED";
            default -> "SENT";
        };
    }

    private String buildPlaceOrderBody(AdapterOrderRequest request, BigDecimal trimmedPrice, BigDecimal trimmedQty) {
        StringBuilder body = new StringBuilder();
        body.append('{')
                .append("\"instId\":\"").append(request.symbol()).append("\",")
                .append("\"tdMode\":\"cash\",")
                .append("\"side\":\"").append(lower(request.side())).append("\",")
                .append("\"ordType\":\"").append(mapOrderType(request.type())).append("\",")
                .append("\"clOrdId\":\"").append(request.clientOrderId()).append("\",")
                .append("\"sz\":\"").append(trimmedQty.toPlainString()).append('\"');
        if (trimmedPrice != null) {
            body.append(',').append("\"px\":\"").append(trimmedPrice.toPlainString()).append('\"');
        }
        body.append('}');
        return body.toString();
    }

    private String buildCancelOrderBody(AdapterCancelRequest request) {
        StringBuilder body = new StringBuilder();
        body.append('{')
                .append("\"instId\":\"").append(request.symbol()).append('\"');
        if (request.externalOrderId() != null && !request.externalOrderId().isBlank()) {
            body.append(',').append("\"ordId\":\"").append(request.externalOrderId()).append('\"');
        } else {
            body.append(',').append("\"clOrdId\":\"").append(request.clientOrderId()).append('\"');
        }
        body.append('}');
        return body.toString();
    }

    private String buildOrderIdentityQuery(AdapterOrderQuery query) {
        if (query.externalOrderId() != null && !query.externalOrderId().isBlank()) {
            return "&ordId=" + query.externalOrderId();
        }
        if (query.clientOrderId() != null && !query.clientOrderId().isBlank()) {
            return "&clOrdId=" + query.clientOrderId();
        }
        throw new IllegalArgumentException("either externalOrderId or clientOrderId must be provided");
    }

    private void ensureSuccessEnvelope(JsonNode payload, String endpoint, String traceId) {
        String code = payload.path("code").asText("0");
        if (!"0".equals(code)) {
            JsonNode firstItem = payload.path("data").isArray() && !payload.path("data").isEmpty()
                    ? payload.path("data").get(0)
                    : null;
            String itemCode = firstItem == null ? null : firstItem.path("sCode").asText(null);
            String itemMessage = firstItem == null ? null : firstItem.path("sMsg").asText(null);
            String detail = (itemCode == null || itemCode.isBlank()) && (itemMessage == null || itemMessage.isBlank())
                    ? ""
                    : ", item_code=" + itemCode + ", item_msg=" + itemMessage;
            throw new OkxApiException(
                    "OKX business error, endpoint=" + endpoint + ", trace_id=" + traceId + ", code=" + code
                            + ", msg=" + payload.path("msg").asText() + detail,
                    200,
                    endpoint,
                    itemCode == null || itemCode.isBlank() ? code : itemCode,
                    traceId
            );
        }
    }

    private JsonNode requireSingleDataItem(JsonNode payload, String endpoint, String traceId) {
        ensureSuccessEnvelope(payload, endpoint, traceId);
        if (!payload.path("data").isArray() || payload.path("data").isEmpty()) {
            throw new OkxApiException(
                    "OKX response missing data item, endpoint=" + endpoint + ", trace_id=" + traceId,
                    200,
                    endpoint,
                    "OKX_EMPTY_DATA",
                    traceId
            );
        }
        return payload.path("data").get(0);
    }

    private AdapterError validateInstrumentTradable(OkxInstrument instrument, String traceId) {
        if (!"live".equalsIgnoreCase(instrument.state())) {
            return new AdapterError(
                    "OKX_INSTRUMENT_NOT_LIVE",
                    "instrument state is not live, instId=" + instrument.instId() + ", trace_id=" + traceId,
                    false
            );
        }
        return null;
    }

    private AdapterOrderAck rejectedAck(AdapterError error, String traceId) {
        return new AdapterOrderAck(false, venue(), null, error, Instant.now(clock), traceId);
    }

    private BigDecimal trim(BigDecimal value, BigDecimal step, NumericType numericType) {
        BigDecimal normalized = NumericPolicy.normalize(numericType, value);
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            return normalized;
        }
        BigDecimal units = normalized.divide(step, 0, RoundingMode.DOWN);
        return units.multiply(step).setScale(Math.max(step.scale(), normalized.scale()), RoundingMode.DOWN).stripTrailingZeros();
    }

    private BigDecimal decimal(JsonNode item, String... fields) {
        String raw = text(item, fields);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("missing decimal field in OKX payload");
        }
        return new BigDecimal(raw);
    }

    private BigDecimal decimalOrZero(JsonNode item, String field) {
        String raw = item.path(field).asText();
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.DOWN);
        }
        return new BigDecimal(raw);
    }

    private String text(JsonNode item, String... fields) {
        for (String field : fields) {
            String value = item.path(field).asText();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String mapOrderType(String type) {
        return switch (upper(type)) {
            case "MARKET" -> "market";
            case "LIMIT" -> "limit";
            default -> throw new IllegalArgumentException("unsupported order type: " + type);
        };
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveErrorCode(OkxApiException ex) {
        return ex.errorCode() == null || ex.errorCode().isBlank() ? "OKX_API_ERROR" : ex.errorCode();
    }

    private void validatePlaceRequest(AdapterOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.clientOrderId() == null || request.clientOrderId().isBlank()) {
            throw new IllegalArgumentException("clientOrderId must not be blank");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if ("LIMIT".equals(upper(request.type()))
                && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("price must be positive for LIMIT");
        }
    }

    private void validateCancelRequest(AdapterCancelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if ((request.externalOrderId() == null || request.externalOrderId().isBlank())
                && (request.clientOrderId() == null || request.clientOrderId().isBlank())) {
            throw new IllegalArgumentException("either externalOrderId or clientOrderId must be provided");
        }
    }

    private void validateOrderQuery(AdapterOrderQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (query.symbol() == null || query.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }

    private static Dependencies createDefaultDependencies() {
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.systemUTC();
        OkxRuntimeConfig runtimeConfig = OkxRuntimeConfig.fromSystemEnv();
        OkxRequestSigner signer = new OkxRequestSigner();
        OkxTimestampProvider timestampProvider = () -> DateTimeFormatter.ISO_INSTANT.format(
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS).atOffset(ZoneOffset.UTC)
        );
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(runtimeConfig.timeout()).build();
        OkxApiCredentials credentials = runtimeConfig.credentials();
        logConnectionFingerprint(runtimeConfig);
        OkxHttpClient publicHttpClient = new OkxHttpClient(
                httpClient,
                objectMapper,
                runtimeConfig.baseUrl(),
                runtimeConfig.timeout(),
                signer,
                timestampProvider,
                credentials,
                false
        );
        OkxHttpClient authenticatedHttpClient = new OkxHttpClient(
                httpClient,
                objectMapper,
                runtimeConfig.baseUrl(),
                runtimeConfig.timeout(),
                signer,
                timestampProvider,
                credentials,
                true,
                runtimeConfig.simulatedTrading() ? Map.of(OKX_SIMULATED_TRADING_HEADER, "1") : Map.of()
        );
        OkxInstrumentsCache instrumentsCache = new OkxInstrumentsCache(
                publicHttpClient,
                clock,
                runtimeConfig.instrumentRefresh()
        );
        return new Dependencies(objectMapper, authenticatedHttpClient, instrumentsCache, clock);
    }

    /**
     * 打印 OKX 连接指纹。
     * <p>
     * Why:
     * 模拟盘/实盘验收必须确认当前连接到哪套环境，但安全约束禁止把 secret/passphrase 写入日志，
     * 因此这里只打印环境名、baseUrl 和 API Key 脱敏摘要。
     */
    private static void logConnectionFingerprint(OkxRuntimeConfig runtimeConfig) {
        System.out.println("OKX adapter connection fingerprint: " + runtimeConfig.fingerprint());
    }

    /**
     * Dependencies 聚合可测试依赖，避免公开多个重载构造器。
     */
    public record Dependencies(
            ObjectMapper objectMapper,
            OkxHttpClient authenticatedHttpClient,
            OkxInstrumentsCache instrumentsCache,
            Clock clock
    ) {
    }
}
