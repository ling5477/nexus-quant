package com.guidinglight.nexusquant.adapter.okx.privateread.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OkxPrivatePositionsReadTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emptyPositionSetIsCompleteAndUsesOneFixedGetWithNoQuery() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Map<String, String>> headersReference = new AtomicReference<>();
        byte[] payload = "{\"code\":\"0\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> {
            calls.incrementAndGet();
            assertEquals(URI.create("https://openapi.okx.com/api/v5/account/positions"), uri);
            assertNull(uri.getRawQuery());
            assertEquals(Duration.ofSeconds(5), timeout);
            assertFalse(headers.containsKey("x-simulated-trading"));
            headersReference.set(headers);
            return new OkxPrivateHttpExchange.Response(200, payload);
        });
        OkxPrivateReadResult result = execute(transport);
        assertTrue(result.complete());
        assertEquals(NOW, result.observedAt());
        assertTrue(result.positions().isEmpty());
        assertEquals(1, calls.get());
        assertTrue(Arrays.equals(payload, new byte[payload.length]));
        assertTrue(headersReference.get().isEmpty());
    }

    @Test
    void retainsSignedContractUnitsSeparatelyFromMarginCurrencyAndRedactsDiagnostics() {
        ObjectNode swap = row("1", "SWAP", "BTC-USDT-SWAP", "net", "-2", "");
        swap.put("uid", "private-account-marker");
        ObjectNode futures = row("2", "FUTURES", "BTC-USDT-261225", "long", "3", "");
        ObjectNode margin = row("3", "MARGIN", "BTC-USDT", "net", "0.0125", "BTC");
        ObjectNode option = row("4", "OPTION", "BTC-USD-261225-100000-C", "net", "-1", "");
        ObjectNode events = row("5", "EVENTS", "BTC-USDT-EVENTS", "net", "1", "");
        OkxPrivateReadResult result = parse(response(swap, futures, margin, option, events));
        assertTrue(result.complete());
        assertEquals(5, result.positions().size());
        assertEquals("-2", result.positions().get(0).positionQuantity().toPlainString());
        assertNull(result.positions().get(0).positionCurrency());
        assertEquals("USDT", result.positions().get(0).marginCurrency());
        assertEquals("BTC", result.positions().get(2).positionCurrency());
        assertEquals("0.0125", result.positions().get(2).positionQuantity().toPlainString());
        assertTrue(result.balances().isEmpty());
        assertEquals("OkxPrivateReadResult[REDACTED]", result.toString());
        assertEquals("OkxPrivatePositionFact[REDACTED]", result.positions().get(0).toString());
        assertThrows(UnsupportedOperationException.class, () -> result.positions().clear());
    }

    @Test
    void acceptsZeroMarginPositionsAndBothHedgeSidesWithoutCollapsingThem() {
        ObjectNode margin = row("1", "MARGIN", "BTC-USDT", "net", "0", "USDT");
        ObjectNode longSide = row("2", "SWAP", "BTC-USDT-SWAP", "long", "1", "");
        ObjectNode shortSide = row("3", "SWAP", "BTC-USDT-SWAP", "short", "2", "");
        OkxPrivateReadResult result = parse(response(margin, longSide, shortSide));
        assertTrue(result.complete());
        assertEquals(3, result.positions().size());
        assertEquals(0, result.positions().get(0).positionQuantity().signum());
    }

    @Test
    void rejectsMissingRequiredFieldsWithoutReturningTheEarlierValidRow() {
        for (String field : List.of("instType", "instId", "mgnMode", "posSide", "pos", "ccy", "uTime", "posId")) {
            ObjectNode malformed = row("2", "SWAP", "ETH-USDT-SWAP", "net", "1", "");
            malformed.remove(field);
            assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH,
                    response(row("1", "SWAP", "BTC-USDT-SWAP", "net", "1", ""), malformed));
        }
        ObjectNode margin = row("1", "MARGIN", "BTC-USDT", "net", "0", "BTC");
        margin.remove("posCcy");
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(margin));
    }

    @Test
    void rejectsMalformedEnumsNumbersCurrencyAndTimestamps() {
        Map<String, List<String>> invalidValues = Map.of(
                "instType", List.of("SPOT", "swap", "FUTURE", "UNKNOWN"),
                "instId", List.of("", "BTC-USDT", "https://example.invalid", "BTC-USDT-SWAP/other"),
                "mgnMode", List.of("", "cash", "CROSS"),
                "posSide", List.of("", "buy", "NET"),
                "pos", List.of("", "NaN", "0junk", "1e2", "0.0000000000000000001", "1".repeat(39)),
                "ccy", List.of("", "USDT/other", "usdt"),
                "uTime", List.of("", "0", "-1", "9999999999999999999", "not-time", Long.toString(NOW.plusSeconds(31).toEpochMilli())),
                "posId", List.of("", "0", "1x"));
        invalidValues.forEach((field, values) -> values.forEach(value -> {
            ObjectNode row = row("1", "SWAP", "BTC-USDT-SWAP", "net", "0", "");
            row.put(field, value);
            assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(row));
        }));
        for (String field : List.of("instType", "instId", "mgnMode", "posSide", "pos", "ccy", "uTime", "posId", "posCcy")) {
            ObjectNode row = row("1", "SWAP", "BTC-USDT-SWAP", "net", "0", "");
            row.put(field, 0);
            assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(row));
        }
    }

    @Test
    void rejectsSideSignAndMarginCurrencyContradictions() {
        for (ObjectNode invalid : List.of(
                row("1", "SWAP", "BTC-USDT-SWAP", "short", "-1", ""),
                row("1", "FUTURES", "BTC-USDT-261225", "long", "-1", ""),
                row("1", "MARGIN", "BTC-USDT", "net", "-1", "BTC"),
                row("1", "MARGIN", "BTC-USDT", "long", "1", "BTC"),
                row("1", "MARGIN", "BTC-USDT", "net", "1", "ETH"),
                row("1", "SWAP", "BTC-USDT-SWAP", "net", "1", "BTC"))) {
            assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(invalid));
        }
    }

    @Test
    void rejectsDuplicateProviderIdentityNaturalIdentityAndConflictingPositionModes() {
        ObjectNode first = row("1", "SWAP", "BTC-USDT-SWAP", "net", "0", "");
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(first, first));
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(first,
                row("1", "SWAP", "ETH-USDT-SWAP", "net", "1", "")));
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(first,
                row("2", "SWAP", "BTC-USDT-SWAP", "net", "1", "")));
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, response(first,
                row("2", "SWAP", "BTC-USDT-SWAP", "long", "1", "")));
    }

    @Test
    void acceptsExactlyOneHundredRowsAndRejectsOneHundredAndOneWithoutTruncation() {
        ObjectNode body = MAPPER.createObjectNode().put("code", "0");
        ArrayNode data = body.putArray("data");
        for (int index = 0; index < 100; index++) {
            data.add(row(Integer.toString(index + 1), "SWAP", "C" + index + "-USDT-SWAP", "net", "0", ""));
        }
        assertEquals(100, parse(body.toString()).positions().size());
        data.add(row("101", "SWAP", "C100-USDT-SWAP", "net", "1", ""));
        assertRejected(OkxPrivateReadError.POSITION_RESPONSE_OVER_LIMIT, body.toString());
    }

    @Test
    void rejectsMissingNonArrayDataAndPartialJsonWithoutRawPayloadLeakage() {
        for (String json : List.of("{\"code\":\"0\"}", "{\"code\":\"0\",\"data\":null}", "{\"code\":\"0\",\"data\":{}}")) {
            assertRejected(OkxPrivateReadError.PARTIAL_RESPONSE, json);
        }
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, "{\"code\":\"0\",\"data\":[null]}");
        assertRejected(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH, "{\"code\":0,\"data\":[]}");
        for (String malformed : List.of("{\"code\":\"0\",\"data\":[", "{\"code\":\"0\",\"data\":[]} {}",
                response(row("1", "SWAP", "BTC-USDT-SWAP", "net", "0", "")).replace("\"pos\":\"0\"", "\"pos\":\"1\",\"pos\":\"0\""))) {
            assertRejected(OkxPrivateReadError.RESPONSE_PARSE_FAILED, malformed);
        }
        OkxPrivateReadException error = assertThrows(OkxPrivateReadException.class, () -> parse("private-raw-marker"));
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private-raw-marker"));
    }

    @Test
    void positionsHttpFailureAndTimeoutMakeNoRetryAndBodyCapStillApplies() {
        AtomicInteger calls = new AtomicInteger();
        JdkOkxPrivateReadTransport failed = transport((uri, headers, timeout) -> {
            calls.incrementAndGet();
            return new OkxPrivateHttpExchange.Response(503, "private-marker".getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(OkxPrivateReadError.HTTP_SERVER_ERROR, assertThrows(OkxPrivateReadException.class, () -> execute(failed)).category());
        assertEquals(1, calls.get());
        JdkOkxPrivateReadTransport timeout = transport((uri, headers, requestTimeout) -> {
            calls.incrementAndGet();
            throw new HttpTimeoutException("timeout");
        });
        assertEquals(OkxPrivateReadError.NETWORK_TIMEOUT, assertThrows(OkxPrivateReadException.class, () -> execute(timeout)).category());
        assertEquals(2, calls.get());
        JdkOkxPrivateReadTransport oversized = transport((uri, headers, requestTimeout) ->
                new OkxPrivateHttpExchange.Response(200, new byte[JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES + 1]));
        assertEquals(OkxPrivateReadError.RESPONSE_TOO_LARGE,
                assertThrows(OkxPrivateReadException.class, () -> execute(oversized)).category());
    }

    private static ObjectNode row(String id, String type, String instrument, String side, String quantity, String positionCurrency) {
        return MAPPER.createObjectNode().put("posId", id).put("instType", type).put("instId", instrument)
                .put("mgnMode", "isolated").put("posSide", side).put("pos", quantity)
                .put("posCcy", positionCurrency).put("ccy", "USDT").put("uTime", Long.toString(NOW.toEpochMilli()));
    }

    private static String response(ObjectNode... rows) {
        ObjectNode body = MAPPER.createObjectNode().put("code", "0");
        ArrayNode data = body.putArray("data");
        for (ObjectNode row : rows) data.add(row);
        return body.toString();
    }

    private static OkxPrivateReadResult parse(String json) {
        return execute(transport((uri, headers, timeout) ->
                new OkxPrivateHttpExchange.Response(200, json.getBytes(StandardCharsets.UTF_8))));
    }

    private static void assertRejected(OkxPrivateReadError expected, String json) {
        OkxPrivateReadException error = assertThrows(OkxPrivateReadException.class, () -> parse(json));
        assertEquals(expected, error.category());
        assertNull(error.getCause());
    }

    private static JdkOkxPrivateReadTransport transport(OkxPrivateHttpExchange exchange) {
        return new JdkOkxPrivateReadTransport(MAPPER, CLOCK, Duration.ofSeconds(5), exchange);
    }

    private static OkxPrivateReadResult execute(JdkOkxPrivateReadTransport transport) {
        try (OkxPrivateCredentialContext credential = new OkxPrivateCredentialContext(
                "synthetic-key".toCharArray(), "synthetic-secret".toCharArray(), "synthetic-pass".toCharArray())) {
            return transport.execute(OkxPrivateReadRequest.accountPositions(), credential, OkxPrivateEnvironment.PRODUCTION);
        }
    }
}
