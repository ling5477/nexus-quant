package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Side;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelCommand;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelResponse;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ClockCommand;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ClockResponse;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillCommand;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillResponse;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderCommand;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderResponse;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceCommand;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceResponse;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawFill;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawOrder;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseMetadata;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseOutcome;
import static com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.TransportFailure;

/**
 * package-private typed OKX v5 client；只由复用既有 signer/exchange 的 transport 调用。
 */
final class OkxJdkRealClient {

    private static final int CLOCK_SAMPLE_COUNT = 3;
    private static final String INSTRUMENT_PATH = "/api/v5/account/instruments";
    private static final String FEE_PATH = "/api/v5/account/trade-fee";
    private static final String BALANCE_PATH = "/api/v5/account/balance";
    private static final String TIME_PATH = "/api/v5/public/time";
    private static final String TICKER_PATH = "/api/v5/market/ticker";
    private static final String ORDER_PATH = "/api/v5/trade/order";
    private static final String CANCEL_PATH = "/api/v5/trade/cancel-order";
    private static final String FILLS_PATH = "/api/v5/trade/fills";
    private static final Set<String> ORDER_STATES = Set.of(
            "live", "partially_filled", "filled", "canceled", "mmp_canceled"
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OkxPrivateRequestSigner signer;
    private final OkxPrivateHttpExchange exchange;
    private final Duration requestTimeout;

    OkxJdkRealClient(
            ObjectMapper objectMapper,
            Clock clock,
            OkxPrivateRequestSigner signer,
            OkxPrivateHttpExchange exchange,
            Duration requestTimeout
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.signer = Objects.requireNonNull(signer);
        this.exchange = Objects.requireNonNull(exchange);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    OkxPilotPrerequisiteSnapshot observePrerequisites(
            OkxPilotPrerequisiteRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        try {
            List<OkxPilotPrerequisiteSnapshot.InstrumentFact> instruments = new ArrayList<>();
            List<OkxPilotPrerequisiteSnapshot.FeeFact> fees = new ArrayList<>();
            List<OkxPilotPrerequisiteSnapshot.MarketFact> markets = new ArrayList<>();
            for (String instrument : request.instruments()) {
                OkxPilotPrerequisiteSnapshot.InstrumentFact instrumentFact =
                        readInstrument(instrument, credential, environment);
                instruments.add(instrumentFact);
                fees.add(readFee(instrument, instrumentFact.feeGroupId(), credential, environment));
                markets.add(readMarket(instrument));
            }
            BigDecimal balance = readAvailableBalance(credential, environment);
            Instant before = clock.instant();
            Instant serverTime = readServerTime();
            Instant after = clock.instant();
            Instant midpoint = before.plusMillis(Duration.between(before, after).toMillis() / 2);
            long skew = Math.subtractExact(serverTime.toEpochMilli(), midpoint.toEpochMilli());
            return new OkxPilotPrerequisiteSnapshot(instruments, fees, markets, balance, serverTime, midpoint, skew);
        } catch (WireFailure failure) {
            throw new OkxPrivateReadException(toReadError(failure.category()));
        } catch (ArithmeticException | IllegalArgumentException failure) {
            throw new OkxPrivateReadException(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH);
        }
    }

    PlaceResponse placeLimit(
            PlaceCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireOrderIdentity(command.clientOrderId(), command.instrument());
        requirePositive(command.price(), "price");
        requirePositive(command.quantity(), "quantity");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instId", command.instrument());
        body.put("tdMode", "cash");
        body.put("clOrdId", command.clientOrderId());
        body.put("side", command.side() == Side.BUY ? "buy" : "sell");
        body.put("ordType", "limit");
        body.put("px", command.price().toPlainString());
        body.put("sz", command.quantity().toPlainString());
        try {
            WireResponse placed = privateRequest(
                    "POST", ORDER_PATH, json(body), credential, environment,
                    command.responseLimit().maximumResponseBytes(), true);
            if (!rootSuccess(placed.root())) {
                throw new WireFailure(providerCategory(text(placed.root(), "code")), true);
            }
            JsonNode acknowledgement = exactRow(placed.root());
            String itemCode = text(acknowledgement, "sCode");
            if (!"0".equals(itemCode)) {
                if ("51000".equals(itemCode)) {
                    return rejectedPlace(placed.bytes());
                }
                throw new WireFailure(providerCategory(itemCode), true);
            }
            requireExact(text(acknowledgement, "clOrdId"), command.clientOrderId());
            requireIdentifier(text(acknowledgement, "ordId"), "ordId", 64);
            QueryResult queried = queryOrder(command.clientOrderId(), command.instrument(), credential, environment,
                    command.responseLimit().maximumResponseBytes());
            if (queried.order() == null) {
                throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, true);
            }
            return new PlaceResponse(
                    metadata(OkxSpotProviderOperation.PLACE_LIMIT, placed.bytes() + queried.bytes()),
                    ResponseOutcome.ACCEPTED, queried.order(), null);
        } catch (WireFailure failure) {
            return new PlaceResponse(metadata(OkxSpotProviderOperation.PLACE_LIMIT, 0), ResponseOutcome.ERROR, null,
                    new TransportFailure(failure.category(), true));
        }
    }

    OrderResponse queryOrder(
            OrderCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment,
            OkxSpotProviderOperation operation
    ) {
        requireOrderIdentity(command.clientOrderId(), command.instrument());
        try {
            QueryResult result = queryOrder(command.clientOrderId(), command.instrument(), credential, environment,
                    command.responseLimit().maximumResponseBytes());
            return new OrderResponse(metadata(operation, result.bytes()), result.order(), null);
        } catch (WireFailure failure) {
            return new OrderResponse(metadata(operation, 0), null,
                    new TransportFailure(failure.category(), false));
        }
    }

    CancelResponse cancelOrder(
            CancelCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireOrderIdentity(command.clientOrderId(), command.instrument());
        requirePositive(command.confirmedRemainingQuantity(), "confirmedRemainingQuantity");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instId", command.instrument());
        body.put("clOrdId", command.clientOrderId());
        try {
            WireResponse canceled = privateRequest(
                    "POST", CANCEL_PATH, json(body), credential, environment,
                    command.responseLimit().maximumResponseBytes(), true);
            if (!rootSuccess(canceled.root())) {
                throw new WireFailure(providerCategory(text(canceled.root(), "code")), true);
            }
            JsonNode acknowledgement = exactRow(canceled.root());
            if (!"0".equals(text(acknowledgement, "sCode"))) {
                throw new WireFailure(
                        providerCategory(text(acknowledgement, "sCode")), true);
            }
            requireExact(text(acknowledgement, "clOrdId"), command.clientOrderId());
            QueryResult queried = queryOrder(command.clientOrderId(), command.instrument(), credential, environment,
                    command.responseLimit().maximumResponseBytes());
            if (queried.order() == null) {
                throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, true);
            }
            return new CancelResponse(
                    metadata(OkxSpotProviderOperation.CANCEL_ORDER, canceled.bytes() + queried.bytes()),
                    ResponseOutcome.ACCEPTED, queried.order(), null);
        } catch (WireFailure failure) {
            return new CancelResponse(metadata(OkxSpotProviderOperation.CANCEL_ORDER, 0), ResponseOutcome.ERROR, null,
                    new TransportFailure(failure.category(), true));
        }
    }

    FillResponse readFills(
            FillCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireOrderIdentity(command.clientOrderId(), command.instrument());
        try {
            QueryResult queried = queryOrder(command.clientOrderId(), command.instrument(), credential, environment,
                    command.responseLimit().maximumResponseBytes());
            if (queried.order() == null) {
                throw new WireFailure(SpotProviderError.Category.EXCHANGE_BUSINESS_REJECTION, false);
            }
            String orderId = queried.order().exchangeOrderId();
            String path = FILLS_PATH + "?instType=SPOT&instId=" + command.instrument()
                    + "&ordId=" + encodeQueryValue(orderId) + "&begin=" + command.begin().toEpochMilli()
                    + "&end=" + command.end().toEpochMilli() + "&limit=" + command.maxRecords();
            WireResponse response = privateRequest(
                    "GET", path, "", credential, environment,
                    command.responseLimit().maximumResponseBytes(), false);
            requireRootSuccess(response.root());
            JsonNode data = response.root().get("data");
            if (data == null || !data.isArray() || data.size() > command.maxRecords()) {
                throw new WireFailure(SpotProviderError.Category.RESPONSE_TOO_LARGE, false);
            }
            List<RawFill> fills = new ArrayList<>();
            for (JsonNode row : data) {
                requireExact(text(row, "instId"), command.instrument());
                requireExact(text(row, "ordId"), orderId);
                requireExact(text(row, "clOrdId"), command.clientOrderId());
                String tradeId = requireIdentifier(text(row, "tradeId"), "tradeId", 64);
                BigDecimal price = positiveDecimal(text(row, "fillPx"), "fillPx");
                BigDecimal quantity = positiveDecimal(text(row, "fillSz"), "fillSz");
                BigDecimal fee = decimal(text(row, "fee"), "fee");
                String feeCurrency = requireIdentifier(text(row, "feeCcy"), "feeCcy", 16);
                Instant filledAt = epochMillis(text(row, "fillTime"), "fillTime");
                if (filledAt.isBefore(command.begin()) || filledAt.isAfter(command.end())) {
                    throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
                }
                fills.add(new RawFill(tradeId, price, quantity, fee, feeCurrency, filledAt));
            }
            return new FillResponse(
                    metadata(OkxSpotProviderOperation.READ_FILLS, queried.bytes() + response.bytes()),
                    fills, data.size() < command.maxRecords(), null);
        } catch (WireFailure failure) {
            return new FillResponse(metadata(OkxSpotProviderOperation.READ_FILLS, 0), List.of(), false,
                    new TransportFailure(failure.category(), false));
        }
    }

    private OkxPilotPrerequisiteSnapshot.InstrumentFact readInstrument(
            String instrument,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        WireResponse response = privateRequest(
                "GET", INSTRUMENT_PATH + "?instType=SPOT&instId=" + instrument, "",
                credential, environment, JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES, false);
        requireRootSuccess(response.root());
        JsonNode row = exactRow(response.root());
        requireExact(text(row, "instId"), instrument);
        requireExact(text(row, "instType"), "SPOT");
        return new OkxPilotPrerequisiteSnapshot.InstrumentFact(
                instrument,
                requireIdentifier(text(row, "state"), "state", 32).toLowerCase(Locale.ROOT),
                requireIdentifier(text(row, "groupId"), "groupId", 32),
                positiveDecimal(text(row, "tickSz"), "tickSz"),
                positiveDecimal(text(row, "lotSz"), "lotSz"),
                positiveDecimal(text(row, "minSz"), "minSz")
        );
    }

    private OkxPilotPrerequisiteSnapshot.FeeFact readFee(
            String instrument,
            String expectedGroupId,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        WireResponse response = privateRequest(
                "GET", FEE_PATH + "?instType=SPOT&instId=" + instrument, "",
                credential, environment, JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES, false);
        requireRootSuccess(response.root());
        JsonNode row = exactRow(response.root());
        String level = requireIdentifier(text(row, "level"), "level", 32);
        BigDecimal maker = rate(text(row, "maker"), "maker");
        BigDecimal taker = rate(text(row, "taker"), "taker");
        Instant providerTimestamp = epochMillis(text(row, "ts"), "ts");
        JsonNode groups = row.get("feeGroup");
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        JsonNode group = null;
        for (JsonNode candidate : groups) {
            if (expectedGroupId.equals(text(candidate, "groupId"))) {
                if (group != null) {
                    throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
                }
                group = candidate;
            }
        }
        if (group == null) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        String groupId = requireIdentifier(text(group, "groupId"), "groupId", 32);
        if (maker.compareTo(rate(text(group, "maker"), "feeGroup.maker")) != 0
                || taker.compareTo(rate(text(group, "taker"), "feeGroup.taker")) != 0) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return new OkxPilotPrerequisiteSnapshot.FeeFact(
                instrument, level, groupId, maker, taker, providerTimestamp);
    }

    private BigDecimal readAvailableBalance(
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        WireResponse response = privateRequest(
                "GET", BALANCE_PATH + "?ccy=USDT", "", credential, environment,
                JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES, false);
        requireRootSuccess(response.root());
        JsonNode account = exactRow(response.root());
        JsonNode details = account.get("details");
        if (details == null || !details.isArray() || details.size() != 1) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        JsonNode usdt = details.get(0);
        requireExact(text(usdt, "ccy"), "USDT");
        BigDecimal available = decimal(text(usdt, "availBal"), "availBal");
        if (available.signum() < 0) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return available;
    }

    private Instant readServerTime() {
        WireResponse response = publicRequest(TIME_PATH, JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES);
        requireRootSuccess(response.root());
        return epochMillis(text(exactRow(response.root()), "ts"), "ts");
    }

    /** 为query-only recovery读取当前venue clock；该public request不携带credential headers。 */
    ClockResponse readClock(ClockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            ClockSample selected = null;
            int totalResponseBytes = 0;
            for (int sample = 0; sample < CLOCK_SAMPLE_COUNT; sample++) {
                Instant before = clock.instant();
                WireResponse response = publicRequest(TIME_PATH, command.responseLimit().maximumResponseBytes());
                requireRootSuccess(response.root());
                Instant serverTime = epochMillis(text(exactRow(response.root()), "ts"), "ts");
                Instant after = clock.instant();
                Duration roundTrip = Duration.between(before, after);
                if (roundTrip.isNegative()) {
                    throw new WireFailure(SpotProviderError.Category.CLOCK_SKEW, false);
                }
                Instant midpoint = before.plusMillis(roundTrip.toMillis() / 2);
                ClockSample current = new ClockSample(
                        serverTime, midpoint, Duration.between(midpoint, serverTime), roundTrip);
                if (selected == null || current.roundTrip().compareTo(selected.roundTrip()) < 0) {
                    selected = current;
                }
                totalResponseBytes = Math.addExact(totalResponseBytes, response.bytes());
            }
            if (selected == null) {
                throw new WireFailure(SpotProviderError.Category.UNKNOWN_RESULT, false);
            }
            return new ClockResponse(
                    metadata(OkxSpotProviderOperation.READ_CLOCK, totalResponseBytes),
                    selected.serverTime(),
                    selected.localClockMidpoint(),
                    selected.observedSkew(),
                    null);
        } catch (ArithmeticException | WireFailure failure) {
            SpotProviderError.Category category = failure instanceof WireFailure wire
                    ? wire.category() : SpotProviderError.Category.MALFORMED_RESPONSE;
            return new ClockResponse(
                    metadata(OkxSpotProviderOperation.READ_CLOCK, 0),
                    null,
                    null,
                    null,
                    new TransportFailure(category, false));
        }
    }

    private OkxPilotPrerequisiteSnapshot.MarketFact readMarket(String instrument) {
        WireResponse response = publicRequest(
                TICKER_PATH + "?instId=" + instrument,
                JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES);
        requireRootSuccess(response.root());
        JsonNode row = exactRow(response.root());
        requireExact(text(row, "instId"), instrument);
        return new OkxPilotPrerequisiteSnapshot.MarketFact(
                instrument,
                positiveDecimal(text(row, "askPx"), "askPx"),
                epochMillis(text(row, "ts"), "ts"));
    }

    private QueryResult queryOrder(
            String clientOrderId,
            String instrument,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment,
            int maximumResponseBytes
    ) {
        WireResponse response = privateRequest(
                "GET", ORDER_PATH + "?instId=" + instrument + "&clOrdId=" + clientOrderId, "",
                credential, environment, maximumResponseBytes, false);
        requireRootSuccess(response.root());
        JsonNode data = response.root().get("data");
        if (data == null || !data.isArray()) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        if (data.isEmpty()) {
            return new QueryResult(null, response.bytes());
        }
        if (data.size() != 1) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return new QueryResult(parseOrder(data.get(0), clientOrderId, instrument), response.bytes());
    }

    private RawOrder parseOrder(JsonNode row, String clientOrderId, String instrument) {
        requireExact(text(row, "instId"), instrument);
        requireExact(text(row, "clOrdId"), clientOrderId);
        String orderId = requireIdentifier(text(row, "ordId"), "ordId", 64);
        String state = requireIdentifier(text(row, "state"), "state", 32).toLowerCase(Locale.ROOT);
        if (!ORDER_STATES.contains(state)) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        BigDecimal original = positiveDecimal(text(row, "sz"), "sz");
        BigDecimal executed = decimal(text(row, "accFillSz"), "accFillSz");
        BigDecimal remaining = original.subtract(executed);
        if (executed.signum() < 0 || remaining.signum() < 0) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return new RawOrder(clientOrderId, orderId, state, original, executed, remaining, List.of());
    }

    private WireResponse privateRequest(
            String method,
            String path,
            String body,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment,
            int maximumResponseBytes,
            boolean mutation
    ) {
        Objects.requireNonNull(credential, "credential must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        try (OkxPrivateRequestSigner.SignedHeaders signed = signer.sign(method, path, body, credential)) {
            if (environment.simulatedTradingHeader()) {
                signed.values().put("x-simulated-trading", "1");
            }
            if ("POST".equals(method)) {
                signed.values().put("Content-Type", "application/json");
            }
            return exchange(method, path, body, signed.values(), maximumResponseBytes, mutation);
        }
    }

    private WireResponse publicRequest(String path, int maximumResponseBytes) {
        return exchange("GET", path, "", Map.of(), maximumResponseBytes, false);
    }

    private WireResponse exchange(
            String method,
            String path,
            String body,
            Map<String, String> headers,
            int maximumResponseBytes,
            boolean mutation
    ) {
        URI uri = JdkOkxPrivateReadTransport.GLOBAL_HOST.resolve(path);
        if (!"https".equals(uri.getScheme())
                || !JdkOkxPrivateReadTransport.GLOBAL_HOST.getHost().equals(uri.getHost())
                || uri.getPort() != -1) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, mutation);
        }
        byte[] requestBody = body.getBytes(StandardCharsets.UTF_8);
        byte[] responseBody = null;
        try {
            OkxPrivateHttpExchange.Response response = "POST".equals(method)
                    ? exchange.post(uri, headers, requestBody, requestTimeout, maximumResponseBytes)
                    : exchange.get(uri, headers, requestTimeout, maximumResponseBytes);
            responseBody = response.body();
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new WireFailure(SpotProviderError.Category.PERMISSION_DENIED, mutation);
            }
            if (response.statusCode() == 429) {
                throw new WireFailure(SpotProviderError.Category.RATE_LIMITED, mutation);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WireFailure(SpotProviderError.Category.HTTP_ERROR, mutation);
            }
            if (responseBody == null || responseBody.length > maximumResponseBytes) {
                throw new WireFailure(SpotProviderError.Category.RESPONSE_TOO_LARGE, mutation);
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isObject() || text(root, "code") == null) {
                throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, mutation);
            }
            return new WireResponse(root, responseBody.length);
        } catch (HttpTimeoutException failure) {
            throw new WireFailure(SpotProviderError.Category.TRANSPORT_TIMEOUT, mutation);
        } catch (WireFailure failure) {
            throw failure;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new WireFailure(SpotProviderError.Category.UNKNOWN_RESULT, mutation);
        } catch (JsonProcessingException failure) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, mutation);
        } catch (IOException failure) {
            SpotProviderError.Category category = hasCause(
                    failure, OkxPrivateHttpExchange.ResponseLimitExceededIOException.class)
                    || hasCause(failure, JdkOkxPrivateReadTransport.ResponseTooLargeIOException.class)
                    ? SpotProviderError.Category.RESPONSE_TOO_LARGE
                    : SpotProviderError.Category.UNKNOWN_RESULT;
            throw new WireFailure(category, mutation);
        } finally {
            Arrays.fill(requestBody, (byte) 0);
            if (responseBody != null) {
                Arrays.fill(responseBody, (byte) 0);
            }
        }
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception failure) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private ResponseMetadata metadata(OkxSpotProviderOperation operation, int responseBytes) {
        return new ResponseMetadata(operation, Math.max(0, responseBytes), null, clock.instant());
    }

    private PlaceResponse rejectedPlace(int bytes) {
        return new PlaceResponse(metadata(OkxSpotProviderOperation.PLACE_LIMIT, bytes),
                ResponseOutcome.REJECTED, null, null);
    }

    private static JsonNode exactRow(JsonNode root) {
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray() || data.size() != 1 || !data.get(0).isObject()) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return data.get(0);
    }

    private static boolean rootSuccess(JsonNode root) {
        return "0".equals(text(root, "code"));
    }

    private static void requireRootSuccess(JsonNode root) {
        if (!rootSuccess(root)) {
            throw new WireFailure(providerCategory(text(root, "code")), false);
        }
    }

    private static SpotProviderError.Category providerCategory(String code) {
        return switch (code == null ? "" : code) {
            case "50011" -> SpotProviderError.Category.RATE_LIMITED;
            case "50105", "50110", "50111", "50113", "50120", "50035" -> SpotProviderError.Category.PERMISSION_DENIED;
            default -> SpotProviderError.Category.EXCHANGE_BUSINESS_REJECTION;
        };
    }

    private static OkxPrivateReadError toReadError(SpotProviderError.Category category) {
        return switch (category) {
            case TRANSPORT_TIMEOUT -> OkxPrivateReadError.NETWORK_TIMEOUT;
            case RATE_LIMITED -> OkxPrivateReadError.HTTP_RATE_LIMITED;
            case PERMISSION_DENIED, IP_RESTRICTION -> OkxPrivateReadError.OKX_PERMISSION_DENIED;
            case RESPONSE_TOO_LARGE -> OkxPrivateReadError.RESPONSE_TOO_LARGE;
            case EXCHANGE_BUSINESS_REJECTION -> OkxPrivateReadError.OKX_BUSINESS_REJECTED;
            case MALFORMED_RESPONSE -> OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH;
            default -> OkxPrivateReadError.NETWORK_IO_ERROR;
        };
    }

    private static void requireOrderIdentity(String clientOrderId, String instrument) {
        if (clientOrderId == null || !clientOrderId.matches("[A-Za-z0-9]{1,32}")) {
            throw new IllegalArgumentException("clientOrderId violates the OKX clOrdId contract");
        }
        if (instrument == null || !instrument.matches("[A-Z0-9]{2,20}-USDT")) {
            throw new IllegalArgumentException("instrument violates the OKX Spot contract");
        }
    }

    private static String requireIdentifier(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return value;
    }

    private static void requireExact(String actual, String expected) {
        if (!Objects.equals(actual, expected)) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.scale() > 18) {
            throw new IllegalArgumentException(field + " must be positive and bounded");
        }
    }

    private static BigDecimal positiveDecimal(String value, String field) {
        BigDecimal decimal = decimal(value, field);
        if (decimal.signum() <= 0) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return decimal;
    }

    private static BigDecimal rate(String value, String field) {
        BigDecimal decimal = decimal(value, field);
        if (decimal.compareTo(BigDecimal.ONE.negate()) < 0 || decimal.compareTo(BigDecimal.ONE) > 0
                || decimal.scale() > 12) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
        return decimal;
    }

    private static BigDecimal decimal(String value, String field) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException(field);
            }
            return new BigDecimal(value);
        } catch (NumberFormatException failure) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
    }

    private static Instant epochMillis(String value, String field) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException(field);
            }
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (RuntimeException failure) {
            throw new WireFailure(SpotProviderError.Category.MALFORMED_RESPONSE, false);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record WireResponse(JsonNode root, int bytes) {
    }

    private record QueryResult(RawOrder order, int bytes) {
    }

    private record ClockSample(
            Instant serverTime,
            Instant localClockMidpoint,
            Duration observedSkew,
            Duration roundTrip
    ) {
    }

    private static final class WireFailure extends RuntimeException {
        private final SpotProviderError.Category category;

        private WireFailure(SpotProviderError.Category category, boolean mutationMayHaveReachedVenue) {
            super("OKX typed transport failed");
            this.category = Objects.requireNonNull(category);
        }

        private SpotProviderError.Category category() {
            return category;
        }
    }
}
