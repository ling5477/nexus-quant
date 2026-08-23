package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Side;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkOkxRealTransportTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CLIENT_ORDER_ID = "00000000000000000000000000000001";
    private static final String SYMBOL = "BTC-USDT";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void collectsExactFourPrerequisitesWithVenueNotionalLeftUnpublished() {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, instrumentBody());
        exchange.enqueue(200, feeBody());
        exchange.enqueue(200, balanceBody());
        exchange.enqueue(200, timeBody());

        OkxPilotPrerequisiteSnapshot snapshot = withCredential(transport(exchange),
                (transport, credential) -> transport.observePrerequisites(
                        new OkxPilotPrerequisiteRequest(List.of(SYMBOL)), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(1, snapshot.instruments().size());
        assertEquals(SYMBOL, snapshot.instruments().get(0).instrument());
        assertEquals(new BigDecimal("0.1"), snapshot.instruments().get(0).minimumOrderSize());
        assertEquals("Lv1/1", snapshot.fees().get(0).tierIdentity());
        assertEquals(new BigDecimal("100.25"), snapshot.availableUsdtBalance());
        assertEquals(0, snapshot.observedSkewMs());
        assertEquals(List.of(
                "GET /api/v5/account/instruments?instType=SPOT&instId=BTC-USDT",
                "GET /api/v5/account/trade-fee?instType=SPOT&instId=BTC-USDT",
                "GET /api/v5/account/balance?ccy=USDT",
                "GET /api/v5/public/time"
        ), exchange.requestLines());
        assertTrue(exchange.authenticated().subList(0, 3).stream().allMatch(Boolean::booleanValue));
        assertFalse(exchange.authenticated().get(3));
    }

    @Test
    void placeUsesExactLimitCashBodyThenQueriesByClientOrderIdWithoutRetry() throws Exception {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, acknowledgementBody("0"));
        exchange.enqueue(200, orderBody("live", "0"));
        JdkOkxPrivateReadTransport transport = transport(exchange);

        OkxSpotProviderTransport.PlaceResponse response = withCredential(transport,
                (value, credential) -> value.placeLimit(placeCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(OkxSpotProviderTransport.ResponseOutcome.ACCEPTED, response.outcome());
        assertEquals("live", response.order().rawState());
        assertEquals(2, exchange.requestLines().size());
        assertEquals("POST /api/v5/trade/order", exchange.requestLines().get(0));
        assertEquals("GET /api/v5/trade/order?instId=BTC-USDT&clOrdId=" + CLIENT_ORDER_ID,
                exchange.requestLines().get(1));
        JsonNode body = MAPPER.readTree(exchange.bodies().get(0));
        assertEquals(List.of("instId", "tdMode", "clOrdId", "side", "ordType", "px", "sz"),
                iterable(body.fieldNames()));
        assertEquals("cash", body.get("tdMode").asText());
        assertEquals("limit", body.get("ordType").asText());
        assertEquals(CLIENT_ORDER_ID, body.get("clOrdId").asText());
    }

    @Test
    void explicitBusinessRejectionIsDefinitiveAndNeverQueriesOrRetries() {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, acknowledgementBody("51000"));

        OkxSpotProviderTransport.PlaceResponse response = withCredential(transport(exchange),
                (value, credential) -> value.placeLimit(placeCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(OkxSpotProviderTransport.ResponseOutcome.REJECTED, response.outcome());
        assertEquals(1, exchange.requestLines().size());
    }

    @Test
    void duplicateClientOrderIdAndRootRateLimitRemainQueryFirst() {
        FakeExchange duplicate = new FakeExchange();
        duplicate.enqueue(200, acknowledgementBody("51016"));

        OkxSpotProviderTransport.PlaceResponse duplicateResponse = withCredential(transport(duplicate),
                (value, credential) -> value.placeLimit(placeCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(OkxSpotProviderTransport.ResponseOutcome.ERROR, duplicateResponse.outcome());
        assertEquals(SpotProviderError.Category.EXCHANGE_BUSINESS_REJECTION,
                duplicateResponse.failure().category());
        assertTrue(duplicateResponse.failure().mutationMayHaveReachedVenue());
        assertEquals(1, duplicate.requestLines().size());

        FakeExchange rateLimited = new FakeExchange();
        rateLimited.enqueue(200, rootFailureBody("50011"));

        OkxSpotProviderTransport.PlaceResponse rateLimitedResponse = withCredential(transport(rateLimited),
                (value, credential) -> value.placeLimit(placeCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(OkxSpotProviderTransport.ResponseOutcome.ERROR, rateLimitedResponse.outcome());
        assertEquals(SpotProviderError.Category.RATE_LIMITED, rateLimitedResponse.failure().category());
        assertTrue(rateLimitedResponse.failure().mutationMayHaveReachedVenue());
        assertEquals(1, rateLimited.requestLines().size());
    }

    @Test
    void cancelTreatsAcknowledgementAsNonTerminalAndQueriesExactOrder() {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, acknowledgementBody("0"));
        exchange.enqueue(200, orderBody("canceled", "0"));

        OkxSpotProviderTransport.CancelResponse response = withCredential(transport(exchange),
                (value, credential) -> value.cancelOrder(cancelCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(OkxSpotProviderTransport.ResponseOutcome.ACCEPTED, response.outcome());
        assertEquals("canceled", response.order().rawState());
        assertEquals(List.of(
                "POST /api/v5/trade/cancel-order",
                "GET /api/v5/trade/order?instId=BTC-USDT&clOrdId=" + CLIENT_ORDER_ID
        ), exchange.requestLines());
    }

    @Test
    void fillsResolveExactOrderIdBeforeBoundedFillLookup() {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, orderBody("partially_filled", "0.25"));
        exchange.enqueue(200, fillsBody());

        OkxSpotProviderTransport.FillResponse response = withCredential(transport(exchange),
                (value, credential) -> value.readFills(fillCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertEquals(1, response.fills().size());
        assertEquals("trade-1", response.fills().get(0).exchangeTradeId());
        assertEquals(new BigDecimal("-0.025"), response.fills().get(0).fee());
        assertEquals("USDT", response.fills().get(0).feeCurrency());
        assertEquals("GET /api/v5/trade/order?instId=BTC-USDT&clOrdId=" + CLIENT_ORDER_ID,
                exchange.requestLines().get(0));
        assertEquals("GET /api/v5/trade/fills?instType=SPOT&instId=BTC-USDT&ordId=order-1"
                        + "&begin=1786881599000&end=1786881601000&limit=10",
                exchange.requestLines().get(1));
    }

    @Test
    void percentEncodesVenueOrderIdBeforeBuildingSignedFillsQuery() {
        FakeExchange exchange = new FakeExchange();
        exchange.enqueue(200, orderBody("partially_filled", "0.25", "order-1&limit=100"));
        exchange.enqueue(200, "{\"code\":\"0\",\"data\":[]}");

        OkxSpotProviderTransport.FillResponse response = withCredential(transport(exchange),
                (value, credential) -> value.readFills(fillCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));

        assertTrue(response.fills().isEmpty());
        assertEquals("GET /api/v5/trade/fills?instType=SPOT&instId=BTC-USDT"
                        + "&ordId=order-1%26limit%3D100&begin=1786881599000"
                        + "&end=1786881601000&limit=10",
                exchange.requestLines().get(1));
    }

    @Test
    void mutationTimeoutAndAmbiguousHttpFailuresRemainQueryFirstWithoutBlindRetry() {
        for (FailureCase failure : List.of(
                new FailureCase(new HttpTimeoutException("sensitive-timeout"),
                        SpotProviderError.Category.TRANSPORT_TIMEOUT),
                new FailureCase(null, SpotProviderError.Category.RATE_LIMITED, 429),
                new FailureCase(null, SpotProviderError.Category.HTTP_ERROR, 503))) {
            FakeExchange exchange = new FakeExchange();
            if (failure.exception() != null) {
                exchange.enqueue(failure.exception());
            } else {
                exchange.enqueue(failure.status(), "{}");
            }

            OkxSpotProviderTransport.PlaceResponse response = withCredential(transport(exchange),
                    (value, credential) -> value.placeLimit(placeCommand(), credential,
                            OkxPrivateEnvironment.PRODUCTION));

            assertEquals(OkxSpotProviderTransport.ResponseOutcome.ERROR, response.outcome());
            assertEquals(failure.category(), response.failure().category());
            assertTrue(response.failure().mutationMayHaveReachedVenue());
            assertEquals(1, exchange.requestLines().size());
        }
    }

    @Test
    void failsClosedForCredentialErrorsMalformedBodiesAndResponseCap() {
        for (int status : List.of(401, 403)) {
            FakeExchange exchange = new FakeExchange();
            exchange.enqueue(status, "{}");
            OkxSpotProviderTransport.OrderResponse response = withCredential(transport(exchange),
                    (value, credential) -> value.queryOrder(orderCommand(), credential,
                            OkxPrivateEnvironment.PRODUCTION));
            assertEquals(SpotProviderError.Category.PERMISSION_DENIED, response.failure().category());
        }

        FakeExchange malformed = new FakeExchange();
        malformed.enqueue(200, "{not-json");
        OkxSpotProviderTransport.OrderResponse malformedResponse = withCredential(transport(malformed),
                (value, credential) -> value.queryOrder(orderCommand(), credential,
                        OkxPrivateEnvironment.PRODUCTION));
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, malformedResponse.failure().category());

        FakeExchange oversized = new FakeExchange();
        oversized.enqueue(200, "x".repeat(2049));
        OkxSpotProviderTransport.OrderResponse oversizedResponse = withCredential(transport(oversized),
                (value, credential) -> value.queryOrder(orderCommand(2048), credential,
                        OkxPrivateEnvironment.PRODUCTION));
        assertEquals(SpotProviderError.Category.RESPONSE_TOO_LARGE, oversizedResponse.failure().category());
    }

    @Test
    void rejectsNonOfficialClientOrderIdBeforeAnyHttp() {
        FakeExchange exchange = new FakeExchange();
        OkxSpotProviderTransport.PlaceCommand invalid = new OkxSpotProviderTransport.PlaceCommand(
                "nq1-invalid-hyphen", SYMBOL, Side.BUY, BigDecimal.ONE, BigDecimal.ONE,
                context(), limit());

        assertThrows(IllegalArgumentException.class, () -> withCredential(transport(exchange),
                (value, credential) -> value.placeLimit(invalid, credential, OkxPrivateEnvironment.PRODUCTION)));
        assertTrue(exchange.requestLines().isEmpty());
    }

    private static JdkOkxPrivateReadTransport transport(OkxPrivateHttpExchange exchange) {
        return new JdkOkxPrivateReadTransport(MAPPER, CLOCK, Duration.ofSeconds(5), exchange);
    }

    private static OkxSpotProviderTransport.PlaceCommand placeCommand() {
        return new OkxSpotProviderTransport.PlaceCommand(
                CLIENT_ORDER_ID, SYMBOL, Side.BUY, new BigDecimal("100.00"), new BigDecimal("1.00"),
                context(), limit());
    }

    private static OkxSpotProviderTransport.OrderCommand orderCommand() {
        return orderCommand(4096);
    }

    private static OkxSpotProviderTransport.OrderCommand orderCommand(int maximumBytes) {
        return new OkxSpotProviderTransport.OrderCommand(
                CLIENT_ORDER_ID, SYMBOL, context(), new OkxSpotProviderTransport.ResponseReadLimit(maximumBytes, 10));
    }

    private static OkxSpotProviderTransport.CancelCommand cancelCommand() {
        return new OkxSpotProviderTransport.CancelCommand(
                CLIENT_ORDER_ID, SYMBOL, BigDecimal.ONE, context(), limit());
    }

    private static OkxSpotProviderTransport.FillCommand fillCommand() {
        return new OkxSpotProviderTransport.FillCommand(
                CLIENT_ORDER_ID, SYMBOL, NOW.minusSeconds(1), NOW.plusSeconds(1), 10, context(), limit());
    }

    private static OkxSpotProviderTransport.TransportContext context() {
        return new OkxSpotProviderTransport.TransportContext(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "reference", "trace", "correlation", NOW);
    }

    private static OkxSpotProviderTransport.ResponseReadLimit limit() {
        return new OkxSpotProviderTransport.ResponseReadLimit(4096, 10);
    }

    private static String instrumentBody() {
        return "{\"code\":\"0\",\"data\":[{\"instType\":\"SPOT\",\"instId\":\"BTC-USDT\","
                + "\"state\":\"live\",\"groupId\":\"1\",\"tickSz\":\"0.1\","
                + "\"lotSz\":\"0.0001\",\"minSz\":\"0.1\"}]}";
    }

    private static String feeBody() {
        return "{\"code\":\"0\",\"data\":[{\"level\":\"Lv1\",\"maker\":\"-0.0008\","
                + "\"taker\":\"-0.001\",\"ts\":\"1786881600000\",\"feeGroup\":["
                + "{\"groupId\":\"99\",\"maker\":\"-0.0007\",\"taker\":\"-0.0009\"},"
                + "{\"groupId\":\"1\","
                + "\"maker\":\"-0.0008\",\"taker\":\"-0.001\"}]}]}";
    }

    private static String balanceBody() {
        return "{\"code\":\"0\",\"data\":[{\"details\":[{\"ccy\":\"USDT\","
                + "\"availBal\":\"100.25\",\"cashBal\":\"999999\"}]}]}";
    }

    private static String timeBody() {
        return "{\"code\":\"0\",\"data\":[{\"ts\":\"1786881600000\"}]}";
    }

    private static String acknowledgementBody(String code) {
        return "{\"code\":\"0\",\"data\":[{\"ordId\":\"order-1\",\"clOrdId\":\""
                + CLIENT_ORDER_ID + "\",\"sCode\":\"" + code + "\"}]}";
    }

    private static String orderBody(String state, String filled) {
        return orderBody(state, filled, "order-1");
    }

    private static String orderBody(String state, String filled, String orderId) {
        return "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"ordId\":\"" + orderId + "\","
                + "\"clOrdId\":\"" + CLIENT_ORDER_ID + "\",\"state\":\"" + state
                + "\",\"sz\":\"1\",\"accFillSz\":\"" + filled + "\"}]}";
    }

    private static String rootFailureBody(String code) {
        return "{\"code\":\"" + code + "\",\"data\":[],\"msg\":\"sanitized\"}";
    }

    private static String fillsBody() {
        return "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"ordId\":\"order-1\","
                + "\"clOrdId\":\"" + CLIENT_ORDER_ID + "\",\"tradeId\":\"trade-1\","
                + "\"fillPx\":\"100\",\"fillSz\":\"0.25\",\"fee\":\"-0.025\","
                + "\"feeCcy\":\"USDT\",\"fillTime\":\"1786881600000\"}]}";
    }

    private static List<String> iterable(java.util.Iterator<String> iterator) {
        List<String> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static <T> T withCredential(
            JdkOkxPrivateReadTransport transport,
            CredentialAction<T> action
    ) {
        try (OkxPrivateCredentialContext credential = new OkxPrivateCredentialContext(
                "test-key".toCharArray(), "test-secret".toCharArray(), "test-pass".toCharArray())) {
            return action.execute(transport, credential);
        }
    }

    @FunctionalInterface
    private interface CredentialAction<T> {
        T execute(JdkOkxPrivateReadTransport transport, OkxPrivateCredentialContext credential);
    }

    private record FailureCase(
            IOException exception,
            SpotProviderError.Category category,
            int status
    ) {
        private FailureCase(IOException exception, SpotProviderError.Category category) {
            this(exception, category, 0);
        }

        private FailureCase(IOException exception, SpotProviderError.Category category, int status) {
            this.exception = exception;
            this.category = category;
            this.status = status;
        }
    }

    /**
     * In-memory fake；只记录 header 名称是否存在，不保留 credential/signature 值。
     */
    private static final class FakeExchange implements OkxPrivateHttpExchange {
        private final Queue<QueuedResponse> responses = new ArrayDeque<>();
        private final List<String> requestLines = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
        private final List<Boolean> authenticated = new ArrayList<>();

        private void enqueue(int status, String body) {
            responses.add(new QueuedResponse(status, body.getBytes(StandardCharsets.UTF_8), null));
        }

        private void enqueue(IOException failure) {
            responses.add(new QueuedResponse(0, null, failure));
        }

        @Override
        public Response get(URI uri, Map<String, String> headers, Duration timeout) throws IOException {
            return respond("GET", uri, headers, null);
        }

        @Override
        public Response post(
                URI uri,
                Map<String, String> headers,
                byte[] body,
                Duration timeout,
                int maximumResponseBytes
        ) throws IOException {
            return respond("POST", uri, headers, body);
        }

        private Response respond(String method, URI uri, Map<String, String> headers, byte[] body) throws IOException {
            requestLines.add(method + " " + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            authenticated.add(headers.containsKey("OK-ACCESS-KEY"));
            if (body != null) {
                bodies.add(new String(body, StandardCharsets.UTF_8));
            }
            QueuedResponse response = responses.remove();
            if (response.failure() != null) {
                throw response.failure();
            }
            return new Response(response.status(), Arrays.copyOf(response.body(), response.body().length));
        }

        private List<String> requestLines() {
            return List.copyOf(requestLines);
        }

        private List<String> bodies() {
            return List.copyOf(bodies);
        }

        private List<Boolean> authenticated() {
            return List.copyOf(authenticated);
        }
    }

    private record QueuedResponse(int status, byte[] body, IOException failure) {
    }
}
