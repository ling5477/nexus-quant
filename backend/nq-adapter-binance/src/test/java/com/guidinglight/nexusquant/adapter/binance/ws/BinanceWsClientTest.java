package com.guidinglight.nexusquant.adapter.binance.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRequestSigner;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRuntimeConfig;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * BinanceWsClientTest 覆盖 ws-api 直接入口、订阅确认保护窗与订阅确认后的官方会话校验。
 */
class BinanceWsClientTest {

    @Test
    void shouldConnectDirectlyToWsApiWithoutLegacyListenKeyRoundTrip() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        AtomicReference<URI> uriRef = new AtomicReference<>();
        AtomicReference<String> outboundText = new AtomicReference<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            uriRef.set(uri);
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };
        Mockito.doAnswer(invocation -> {
            outboundText.set(invocation.getArgument(0));
            return CompletableFuture.completedFuture(webSocket);
        }).when(webSocket).sendText(anyString(), Mockito.eq(true));

        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null && uriRef.get() != null);
            listenerRef.get().onOpen(webSocket);
            waitFor(() -> outboundText.get() != null);

            verifyNoInteractions(listenKeyClient);
            assertEquals("wss://ws-api.testnet.binance.vision/ws-api/v3", uriRef.get().toString());
            assertTrue(outboundText.get().contains("\"method\":\"userDataStream.subscribe.signature\""));
            assertTrue(new ObjectMapper().readTree(outboundText.get()).path("id").canConvertToLong());
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldRequestInboundDemandBeforeSendingSubscribeFrame() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));
        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };

        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);

            InOrder inOrder = Mockito.inOrder(webSocket);
            inOrder.verify(webSocket, Mockito.times(2)).request(1);
            inOrder.verify(webSocket).sendText(anyString(), Mockito.eq(true));
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldSuppressDisconnectedCallbackBeforeSubscriptionConfirmed() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };

        AtomicInteger disconnectedCallbacks = new AtomicInteger();
        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        client.addConnectionListener(new BinanceWsConnectionListener() {
            @Override
            public void onDisconnected(String reason, int attempt, long delayMs, String traceId) {
                disconnectedCallbacks.incrementAndGet();
            }
        });
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);

            client.triggerReconnectForSmoke("smoke_before_confirmed");
            listenerRef.get().onClose(webSocket, 1008, "disconnected").toCompletableFuture().join();

            waitFor(() -> client.metricsSnapshot().reconnectCount() >= 1);
            assertEquals(0, disconnectedCallbacks.get());
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldConfirmSubscriptionAndQuerySessionSubscriptions() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        List<String> outboundTexts = new ArrayList<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };
        Mockito.doAnswer(invocation -> {
            outboundTexts.add(invocation.getArgument(0));
            return CompletableFuture.completedFuture(webSocket);
        }).when(webSocket).sendText(anyString(), Mockito.eq(true));

        AtomicReference<BinanceWsRawMessage> rawMessageRef = new AtomicReference<>();
        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        client.addRawMessageListener(rawMessageRef::set);
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);
            waitFor(() -> outboundTexts.size() >= 1);

            String subscribeRequestId = new ObjectMapper().readTree(outboundTexts.get(0)).path("id").asText();
            listenerRef.get().onText(
                    webSocket,
                    "{\"id\":\"" + subscribeRequestId + "\",\"status\":200,\"result\":{\"subscriptionId\":7}}",
                    true
            ).toCompletableFuture().join();

            waitFor(() -> outboundTexts.size() >= 2);
            assertTrue(outboundTexts.get(1).contains("\"method\":\"session.subscriptions\""));
            assertTrue(new ObjectMapper().readTree(outboundTexts.get(1)).path("id").canConvertToLong());

            String sessionSubscriptionsRequestId = new ObjectMapper().readTree(outboundTexts.get(1)).path("id").asText();
            listenerRef.get().onText(
                    webSocket,
                    "{\"id\":\"" + sessionSubscriptionsRequestId + "\",\"status\":200,\"result\":[{\"subscriptionId\":7}]}",
                    true
            ).toCompletableFuture().join();

            listenerRef.get().onText(
                    webSocket,
                    "{\"subscriptionId\":7,\"event\":{\"e\":\"executionReport\",\"E\":1499405658658,\"s\":\"ETHBTC\",\"c\":\"cid-1\",\"C\":\"\",\"x\":\"NEW\",\"X\":\"NEW\",\"i\":4293153}}",
                    true
            ).toCompletableFuture().join();

            waitFor(() -> client.metricsSnapshot().wsConnected() == 1 && rawMessageRef.get() != null);
            assertEquals("executionReport", rawMessageRef.get().eventType());
            assertEquals("cid-1", rawMessageRef.get().payload().path("c").asText());
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldGenerateNumericRequestIdsWithinBinanceLimit() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        List<String> outboundTexts = new ArrayList<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };
        Mockito.doAnswer(invocation -> {
            outboundTexts.add(invocation.getArgument(0));
            return CompletableFuture.completedFuture(webSocket);
        }).when(webSocket).sendText(anyString(), Mockito.eq(true));

        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);
            waitFor(() -> !outboundTexts.isEmpty());

            JsonNode subscribeRequest = new ObjectMapper().readTree(outboundTexts.get(0));
            assertTrue(subscribeRequest.path("id").canConvertToLong());
            assertFalse(subscribeRequest.path("id").asText().length() > 36);

            String subscribeRequestId = subscribeRequest.path("id").asText();
            listenerRef.get().onText(
                    webSocket,
                    "{\"id\":" + subscribeRequestId + ",\"status\":200,\"result\":{\"subscriptionId\":7}}",
                    true
            ).toCompletableFuture().join();
            waitFor(() -> outboundTexts.size() >= 2);

            JsonNode sessionSubscriptionsRequest = new ObjectMapper().readTree(outboundTexts.get(1));
            assertTrue(sessionSubscriptionsRequest.path("id").canConvertToLong());
            assertFalse(sessionSubscriptionsRequest.path("id").asText().length() > 36);
            assertEquals(
                    subscribeRequest.path("id").asLong() + 1,
                    sessionSubscriptionsRequest.path("id").asLong()
            );
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldCaptureDiagnosticSubscribeSummaryWithMaskedSecrets() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };

        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);

            waitFor(() -> !client.diagnosticSnapshot().subscribeRequestSummary().isBlank());
            BinanceWsClient.BinanceWsDiagnosticSnapshot snapshot = client.diagnosticSnapshot();
            assertTrue(snapshot.subscribeRequestSummary().contains("\"method\":\"userDataStream.subscribe.signature\""));
            assertTrue(snapshot.subscribeRequestSummary().contains("\"apiKey\":\"test...-key\""));
            assertTrue(snapshot.subscribeRequestSummary().contains("\"timestamp\":1773100800000"));
            assertTrue(snapshot.subscribeRequestSummary().contains("#sha256="));
            assertEquals("apiKey=test...-key&timestamp=1773100800000", snapshot.signaturePayload());
        } finally {
            client.stop();
        }
    }

    @Test
    void shouldTrackPingPongAndCloseDiagnostics() throws Exception {
        BinanceListenKeyClient listenKeyClient = Mockito.mock(BinanceListenKeyClient.class);
        WebSocket webSocket = Mockito.mock(WebSocket.class);
        when(webSocket.sendText(anyString(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendPong(Mockito.any(ByteBuffer.class))).thenReturn(CompletableFuture.completedFuture(webSocket));

        AtomicReference<WebSocket.Listener> listenerRef = new AtomicReference<>();
        BinanceWsClient.WebSocketConnector connector = (uri, listener) -> {
            listenerRef.set(listener);
            return CompletableFuture.completedFuture(webSocket);
        };

        BinanceWsClient client = new BinanceWsClient(
                new ObjectMapper(),
                runtimeConfig(),
                listenKeyClient,
                new BinanceRequestSigner(),
                Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC),
                connector
        );
        try {
            client.start();
            waitFor(() -> listenerRef.get() != null);
            listenerRef.get().onOpen(webSocket);
            listenerRef.get().onPing(webSocket, ByteBuffer.wrap("srv".getBytes())).toCompletableFuture().join();
            listenerRef.get().onPong(webSocket, ByteBuffer.wrap("cli".getBytes())).toCompletableFuture().join();
            listenerRef.get().onClose(webSocket, 1008, "disconnected").toCompletableFuture().join();

            waitFor(() -> !client.diagnosticSnapshot().lastCloseSummary().isBlank());
            BinanceWsClient.BinanceWsDiagnosticSnapshot snapshot = client.diagnosticSnapshot();
            assertTrue(snapshot.serverPingObserved());
            assertTrue(snapshot.serverPongObserved());
            assertTrue(snapshot.clientPongSent());
            assertTrue(snapshot.localCloseSent());
            assertTrue(snapshot.recentFrames().contains("PING len=3"));
            assertTrue(snapshot.recentFrames().contains("PONG len=3"));
            assertTrue(snapshot.lastCloseSummary().contains("code=1008"));
            assertTrue(snapshot.lastCloseSummary().contains("payload_hex=646973636f6e6e6563746564"));
            assertTrue(snapshot.recentLifecycleEvents().contains("remote_close"));
        } finally {
            client.stop();
        }
    }

    private BinanceRuntimeConfig runtimeConfig() {
        return BinanceRuntimeConfig.fromEnvironment(Map.ofEntries(
                Map.entry("NQ_BINANCE_ENV", "dome"),
                Map.entry("NQ_BINANCE_DOME_BASE_URL", "https://testnet.binance.vision"),
                Map.entry("NQ_BINANCE_DOME_WS_URL", "wss://stream.testnet.binance.vision/ws"),
                Map.entry("NQ_BINANCE_DOME_API_KEY", "test-api-key"),
                Map.entry("NQ_BINANCE_DOME_API_SECRET", "test-secret"),
                Map.entry("NQ_BINANCE_WS_DIAGNOSTIC_ENABLED", "true"),
                Map.entry("NQ_BINANCE_TIMEOUT_MS", "100"),
                Map.entry("NQ_BINANCE_LISTENKEY_REFRESH_MS", "1000"),
                Map.entry("NQ_BINANCE_WS_RECONNECT_BASE_DELAY_MS", "20"),
                Map.entry("NQ_BINANCE_WS_RECONNECT_MAX_DELAY_MS", "20"),
                Map.entry("NQ_BINANCE_WS_HEARTBEAT_INTERVAL_MS", "1000")
        ));
    }

    private void waitFor(Check check) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.done()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("condition not met before timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }
}
