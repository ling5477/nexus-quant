package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OkxReconciliationPrivateReadTest {
    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void allowsOnlyTypedGetAndParsesSanitizedOrderSnapshotUsingFakeTransport() {
        AtomicInteger fakeCalls = new AtomicInteger();
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> {
            fakeCalls.incrementAndGet();
            assertEquals("openapi.okx.com", uri.getHost());
            assertEquals("/api/v5/trade/orders-pending", uri.getPath());
            assertEquals("instType=SPOT&instId=BTC-USDT&limit=100", uri.getQuery());
            return response("""
                    {"code":"0","data":[{"ordId":"123","clOrdId":"client-1","instType":"SPOT",
                    "instId":"BTC-USDT","side":"buy","ordType":"limit","px":"1.00","sz":"2",
                    "accFillSz":"0.5","state":"partially_filled","providerSecret":"must-not-escape"}]}
                    """);
        });

        OkxPrivateReadResult result = execute(transport, OkxPrivateReadRequest.openOrders("BTC-USDT", 100));

        assertEquals(1, fakeCalls.get());
        assertTrue(result.complete());
        assertEquals(1, result.orders().size());
        assertEquals("123", result.orders().getFirst().exchangeOrderId());
        assertEquals("partially_filled", result.orders().getFirst().status());
        assertEquals(NOW, result.orders().getFirst().observedAt());
        assertFalse(result.toString().contains("providerSecret"));
        assertTrue(new OkxSpotEndpointGuard().evaluatePrivateRead(
                OkxPrivateReadRequest.openOrders("BTC-USDT", 100)).allowed());
        assertFalse(new OkxSpotEndpointGuard().evaluate(
                ExchangeCapability.PRIVATE_OPEN_ORDERS_READ, "POST", "/api/v5/trade/orders-pending").allowed());
        assertFalse(new OkxSpotEndpointGuard().evaluate(
                ExchangeCapability.PRIVATE_OPEN_ORDERS_READ, "GET", "/api/v5/trade/order").allowed());
    }

    @Test
    void parsesRecentFillsAndMarksFullPageOrMalformedRowsPartial() {
        JdkOkxPrivateReadTransport fillTransport = transport((uri, headers, timeout) -> response("""
                {"code":"0","data":[{"ordId":"123","clOrdId":"client-1","tradeId":"trade-1",
                "instType":"SPOT","instId":"BTC-USDT","fillPx":"1.25","fillSz":"0.5",
                "fillTime":"1784016000000"}]}
                """));
        OkxPrivateReadResult fill = execute(fillTransport, OkxPrivateReadRequest.recentFills(
                "BTC-USDT", NOW.minusSeconds(3600), NOW, 100));
        assertTrue(fill.complete());
        assertEquals(1, fill.fills().size());
        assertEquals("trade-1", fill.fills().getFirst().exchangeTradeId());

        JdkOkxPrivateReadTransport fullPage = transport((uri, headers, timeout) -> response("""
                {"code":"0","data":[{"ordId":"123","clOrdId":"client-1","instType":"SPOT",
                "instId":"BTC-USDT","side":"buy","ordType":"limit","px":"1","sz":"2",
                "accFillSz":"0","state":"live"}]}
                """));
        assertFalse(execute(fullPage, OkxPrivateReadRequest.openOrders("BTC-USDT", 1)).complete());

        JdkOkxPrivateReadTransport malformed = transport((uri, headers, timeout) -> response("""
                {"code":"0","data":[{"ordId":"123","instType":"SWAP","instId":"BTC-USDT"}]}
                """));
        OkxPrivateReadResult malformedResult = execute(
                malformed, OkxPrivateReadRequest.openOrders("BTC-USDT", 100));
        assertFalse(malformedResult.complete());
        assertTrue(malformedResult.orders().isEmpty());
    }

    private static JdkOkxPrivateReadTransport transport(OkxPrivateHttpExchange exchange) {
        return new JdkOkxPrivateReadTransport(new ObjectMapper(), CLOCK, Duration.ofSeconds(5), exchange);
    }

    private static OkxPrivateReadResult execute(
            JdkOkxPrivateReadTransport transport,
            OkxPrivateReadRequest request
    ) {
        try (OkxPrivateCredentialContext credential = new OkxPrivateCredentialContext(
                "fake-key".toCharArray(), "fake-secret".toCharArray(), "fake-pass".toCharArray())) {
            return transport.execute(request, credential, OkxPrivateEnvironment.DEMO);
        }
    }

    private static OkxPrivateHttpExchange.Response response(String body) {
        return new OkxPrivateHttpExchange.Response(200, body.getBytes(StandardCharsets.UTF_8));
    }
}
