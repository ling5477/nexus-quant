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
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OkxExchangeAdapter 是 GateC-1 的 OKX Spot REST-only 实现。
 * <p>
 * Why:
 * 交易所方言只能存在于 adapter-*，因此 OKX 的签名、字段映射、超时 query-confirm、
 * instruments trim 都必须在这里完成，core 只消费统一的 adapter-api 语义。
 */
public class OkxExchangeAdapter implements TradingAdapter {

    private static final Logger log = LoggerFactory.getLogger(OkxExchangeAdapter.class);
    private static final String VENUE = "OKX";
    private static final String TRADE_ORDER_ENDPOINT = "/api/v5/trade/order";
    private static final String CANCEL_ORDER_ENDPOINT = "/api/v5/trade/cancel-order";
    private static final String ORDER_DETAIL_ENDPOINT = "/api/v5/trade/order";
    private static final String OPEN_ORDERS_ENDPOINT = "/api/v5/trade/orders-pending";
    private static final String FILLS_ENDPOINT = "/api/v5/trade/fills";
    private static final String OKX_SIMULATED_TRADING_HEADER = "x-simulated-trading";
    private static final String FORCE_PLACE_TIMEOUT_ONCE_ENV = "NQ_OKX_FORCE_PLACE_TIMEOUT_ONCE";
    private static final String FORCE_CANCEL_TIMEOUT_ONCE_ENV = "NQ_OKX_FORCE_CANCEL_TIMEOUT_ONCE";
    private static final String FORCE_PLACE_TIMEOUT_ONCE_PROPERTY = "nq.okx.force.place.timeout.once";
    private static final String FORCE_CANCEL_TIMEOUT_ONCE_PROPERTY = "nq.okx.force.cancel.timeout.once";
    private static final AtomicBoolean PLACE_TIMEOUT_EVIDENCE_CONSUMED = new AtomicBoolean(false);
    private static final AtomicBoolean CANCEL_TIMEOUT_EVIDENCE_CONSUMED = new AtomicBoolean(false);

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

        BigDecimal trimmedQty = trim(request.quantity(), instrument.lotSize(), NumericType.QTY);
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
            maybeForcePlaceTimeoutEvidenceOnce(request);
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
        // Why:
        // GateD 当前只剩 ForceCancelTimeoutOnce 的真实消费点没有锁死，因此在 cancel 主路径入口
        // 单独打点，确认请求是否真的进入了 adapter 的 cancel 编排，而不是在更上游就被短路。
        log.info(
                "okx_cancel_path_entered trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        String body = buildCancelOrderBody(request);
        try {
            // Why:
            // 如果看到了 path_entered 但没有 before_http_call，说明 cancel 请求在 adapter 内部
            // 的 body 组装或更前置逻辑被提前中断。
            log.info(
                    "okx_cancel_before_http_call trace_id={} client_order_id={} external_order_id={} symbol={}",
                    request.traceId(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.symbol()
            );
            JsonNode payload = authenticatedHttpClient.post(CANCEL_ORDER_ENDPOINT, body, request.traceId());
            // Why:
            // timeout 强制注入当前设计发生在“真实 HTTP 已成功返回之后”，因此只有看到这个日志，
            // 才有资格继续判断为什么没有命中 consumed/throwing_http_timeout。
            log.info(
                    "okx_cancel_after_http_call_success trace_id={} client_order_id={} external_order_id={} symbol={}",
                    request.traceId(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.symbol()
            );
            maybeForceCancelTimeoutEvidenceOnce(request);
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

    /**
     * 仅用于 GateD 真实验收补证：在 OKX 已成功受理 place 请求后，一次性伪造本地超时并强制走 query-confirm。
     * <p>
     * Why:
     * 当前真实网络环境下很难稳定把 place 请求压进 `HttpTimeoutException`，但 GateD 冻结前必须补齐
     * `okx_query_confirm_place_*` 的真实执行证据。这里采用“请求已真实提交，再把本地返回改走 query-confirm”的
     * 一次性注入方案，只在显式开关开启时生效，默认关闭，不改变默认主链语义。
     */
    private void maybeForcePlaceTimeoutEvidenceOnce(AdapterOrderRequest request) {
        if (!consumePlaceTimeoutEvidenceOnce()) {
            return;
        }
        log.warn(
                "okx_force_timeout_place_once_consumed trace_id={} client_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.symbol()
        );
        // Why:
        // 这里在真正抛出 HTTP_TIMEOUT 之前单独打点，方便区分“开关已进入 JVM，但请求没有命中”
        // 与“请求已经命中并准备走 queryConfirmAfterTimeout”两种状态。
        log.warn(
                "okx_force_timeout_place_once_throwing_http_timeout trace_id={} client_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.symbol()
        );
        throw new OkxApiException(
                "Forced OKX place timeout for query-confirm evidence, endpoint=" + TRADE_ORDER_ENDPOINT + ", trace_id=" + request.traceId(),
                0,
                TRADE_ORDER_ENDPOINT,
                "HTTP_TIMEOUT",
                request.traceId()
        );
    }

    /**
     * 仅用于 GateD 真实验收补证：在 OKX 已成功受理 cancel 请求后，一次性伪造本地超时并强制走 cancel query-confirm。
     * <p>
     * Why:
     * cancel timeout 分支的进入条件和 place 一样严格依赖 `HttpTimeoutException`；为避免持续靠真实网络抖动碰运气，
     * 这里提供同样默认关闭、显式开启、一次性消费的补证开关。
     */
    private void maybeForceCancelTimeoutEvidenceOnce(AdapterCancelRequest request) {
        // Why:
        // 先记录“已经到达 timeout 注入检查点”，这样可以把问题继续缩小到：
        // 1) 开关没有进入 JVM
        // 2) 开关进入 JVM 但在检查点被判定为未启用/已消费
        // 3) 开关被消费并真正抛出 HTTP_TIMEOUT
        log.info(
                "okx_cancel_timeout_injection_check trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        if (!consumeCancelTimeoutEvidenceOnce()) {
            log.info(
                    "okx_cancel_timeout_injection_skipped trace_id={} client_order_id={} external_order_id={} symbol={}",
                    request.traceId(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.symbol()
            );
            return;
        }
        log.warn(
                "okx_cancel_timeout_injection_consumed trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        log.warn(
                "okx_force_timeout_cancel_once_consumed trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        // Why:
        // cancel 分支和 place 分支一样，需要能从日志中明确看到“一次性开关已被消费，下一步就是伪造 HTTP_TIMEOUT”。
        log.warn(
                "okx_force_timeout_cancel_once_throwing_http_timeout trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        throw new OkxApiException(
                "Forced OKX cancel timeout for query-confirm evidence, endpoint=" + CANCEL_ORDER_ENDPOINT + ", trace_id=" + request.traceId(),
                0,
                CANCEL_ORDER_ENDPOINT,
                "HTTP_TIMEOUT",
                request.traceId()
        );
    }

    private AdapterOrderAck queryConfirmAfterTimeout(AdapterOrderRequest request) {
        log.info(
                "okx_query_confirm_place_started trace_id={} client_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.symbol()
        );
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    null,
                    request.traceId()
            ));
            log.info(
                    "okx_query_confirm_place_resolved trace_id={} strategy=getOrder external_order_id={} external_status={}",
                    request.traceId(),
                    snapshot.externalOrderId(),
                    snapshot.externalStatus()
            );
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
        } catch (RuntimeException ignored) {
            for (AdapterOrderSnapshot snapshot : listOpenOrders(new AdapterOpenOrdersQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.traceId()
            ))) {
                if (request.clientOrderId().equals(snapshot.clientOrderId())) {
                    log.info(
                            "okx_query_confirm_place_resolved trace_id={} strategy=listOpenOrders external_order_id={} external_status={}",
                            request.traceId(),
                            snapshot.externalOrderId(),
                            snapshot.externalStatus()
                    );
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
            log.warn(
                    "okx_query_confirm_place_unconfirmed trace_id={} client_order_id={} symbol={}",
                    request.traceId(),
                    request.clientOrderId(),
                    request.symbol()
            );
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
        log.info(
                "okx_query_confirm_cancel_started trace_id={} client_order_id={} external_order_id={} symbol={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId(),
                request.symbol()
        );
        try {
            AdapterOrderSnapshot snapshot = getOrder(new AdapterOrderQuery(
                    request.accountId(),
                    request.venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    request.externalOrderId(),
                    request.traceId()
            ));
            if ("CANCELLED".equals(snapshot.externalStatus())) {
                log.info(
                        "okx_query_confirm_cancel_resolved trace_id={} external_order_id={} external_status={}",
                        request.traceId(),
                        snapshot.externalOrderId(),
                        snapshot.externalStatus()
                );
                return new AdapterCancelAck(true, venue(), snapshot.externalOrderId(), null, Instant.now(clock), request.traceId());
            }
        } catch (RuntimeException ignored) {
            // Why: timeout 后优先尝试 query-confirm；若查单本身失败，则按拒绝返回，交由恢复流程继续推进。
            log.warn(
                    "okx_query_confirm_cancel_lookup_failed trace_id={} client_order_id={} external_order_id={}",
                    request.traceId(),
                    request.clientOrderId(),
                    request.externalOrderId()
            );
        }
        log.warn(
                "okx_query_confirm_cancel_unconfirmed trace_id={} client_order_id={} external_order_id={}",
                request.traceId(),
                request.clientOrderId(),
                request.externalOrderId()
        );
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
                null,
                item.path("instId").asText(null),
                item.path("clOrdId").asText(null),
                item.path("ordId").asText(),
                "ACCEPTED",
                null,
                Instant.now(clock),
                payload.toString(),
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
                decimalOrNull(item, "px"),
                decimalOrNull(item, "sz"),
                decimalOrNull(item, "accFillSz"),
                decimalOrNull(item, "avgPx"),
                Instant.now(clock),
                item.toString(),
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
                .append("\"ordType\":\"").append(mapOrderType(request.orderType())).append("\",")
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
            String normalizedCode = itemCode == null || itemCode.isBlank() ? code : itemCode;
            String detail = (itemCode == null || itemCode.isBlank()) && (itemMessage == null || itemMessage.isBlank())
                    ? ""
                    : ", item_code=" + itemCode + ", item_msg=" + itemMessage;
            throw new OkxApiException(
                    "OKX business error, endpoint=" + endpoint + ", trace_id=" + traceId + ", code=" + code
                            + ", msg=" + payload.path("msg").asText() + detail,
                    200,
                    endpoint,
                    normalizedCode,
                    OkxErrorCode.fromRawCode(normalizedCode),
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
        return new AdapterOrderAck(false, venue(), null, null, null, null, "REJECTED", error, Instant.now(clock), null, traceId);
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

    /**
     * Why:
     * 订单快照里的价格和数量字段并不总是存在；GateD 第三批需要把这些字段映射到统一 snapshot，
     * 但不能在缺字段时直接把查询链路打挂，因此这里提供可空解析。
     */
    private BigDecimal decimalOrNull(JsonNode item, String field) {
        String raw = item.path(field).asText();
        if (raw == null || raw.isBlank()) {
            return null;
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
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if ("LIMIT".equals(upper(request.orderType()))
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
        logForcedTimeoutEvidenceFlags();
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
     * Why:
     * HTTP_TIMEOUT 取证批的首要问题不是“逻辑有没有写”，而是“强制开关到底有没有进入新 JVM”。
     * 这里在 adapter 默认依赖创建阶段打印一次启动期日志，直接回答开关是否已被当前进程读取。
     */
    private static void logForcedTimeoutEvidenceFlags() {
        if (isTruthy(System.getProperty(FORCE_PLACE_TIMEOUT_ONCE_PROPERTY))
                || isTruthy(System.getenv(FORCE_PLACE_TIMEOUT_ONCE_ENV))) {
            log.warn(
                    "okx_force_timeout_place_once_enabled property={} env={}",
                    System.getProperty(FORCE_PLACE_TIMEOUT_ONCE_PROPERTY),
                    System.getenv(FORCE_PLACE_TIMEOUT_ONCE_ENV)
            );
        }
        if (isTruthy(System.getProperty(FORCE_CANCEL_TIMEOUT_ONCE_PROPERTY))
                || isTruthy(System.getenv(FORCE_CANCEL_TIMEOUT_ONCE_ENV))) {
            log.warn(
                    "okx_force_timeout_cancel_once_enabled property={} env={}",
                    System.getProperty(FORCE_CANCEL_TIMEOUT_ONCE_PROPERTY),
                    System.getenv(FORCE_CANCEL_TIMEOUT_ONCE_ENV)
            );
        }
    }

    /**
     * Why:
     * 单测需要可控地重置一次性取证开关，否则静态原子状态会污染后续用例。
     */
    static void resetTimeoutEvidenceForTest() {
        PLACE_TIMEOUT_EVIDENCE_CONSUMED.set(false);
        CANCEL_TIMEOUT_EVIDENCE_CONSUMED.set(false);
    }

    /**
     * Why:
     * place/cancel 取证都要求“默认关闭、显式开启、每个 JVM 只消费一次”。这里把一次性开关做成 package-private，
     * 既能被单测验证，也不把补证细节暴露到 adapter-api 对外契约。
     */
    static boolean consumePlaceTimeoutEvidenceOnce() {
        return consumeTimeoutEvidenceOnce(
                FORCE_PLACE_TIMEOUT_ONCE_PROPERTY,
                FORCE_PLACE_TIMEOUT_ONCE_ENV,
                PLACE_TIMEOUT_EVIDENCE_CONSUMED
        );
    }

    static boolean consumeCancelTimeoutEvidenceOnce() {
        return consumeTimeoutEvidenceOnce(
                FORCE_CANCEL_TIMEOUT_ONCE_PROPERTY,
                FORCE_CANCEL_TIMEOUT_ONCE_ENV,
                CANCEL_TIMEOUT_EVIDENCE_CONSUMED
        );
    }

    private static boolean consumeTimeoutEvidenceOnce(String propertyKey, String envKey, AtomicBoolean consumedMarker) {
        if (!isTruthy(System.getProperty(propertyKey)) && !isTruthy(System.getenv(envKey))) {
            return false;
        }
        return consumedMarker.compareAndSet(false, true);
    }

    private static boolean isTruthy(String raw) {
        if (raw == null) {
            return false;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
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
