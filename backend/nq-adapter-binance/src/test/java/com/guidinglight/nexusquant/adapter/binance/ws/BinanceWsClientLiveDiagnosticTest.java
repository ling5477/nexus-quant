package com.guidinglight.nexusquant.adapter.binance.ws;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRequestSigner;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRuntimeConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * BinanceWsClientLiveDiagnosticTest 只在手工开启时执行真实 Binance ws-api 诊断。
 * <p>
 * Why:
 * 1) 本轮目标是拿到 `1008/disconnected` 的原始证据，而不是继续改业务逻辑；
 * 2) 真实网络诊断不能进入默认 `mvn test` 门禁，否则会让 GateC 基础测试依赖本地凭证与外网；
 * 3) 因此这里要求显式传入 `-Dnq.binance.ws.live.diagnostic=true` 才执行。
 */
class BinanceWsClientLiveDiagnosticTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 同时运行“应用内 BinanceWsClient”与“极简 probe”，输出两端首帧与 close 行为。
     * <p>
     * Why:
     * 用户已经确认同一份 `.env` 下独立探针可拿到 `status=200`。
     * 这里把应用内客户端和最小 probe 放在同一个手工测试里，避免比较基线再被不同脚本/不同环境污染。
     */
    @Test
    void shouldPrintLiveDiagnosticsForClientAndProbe() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("nq.binance.ws.live.diagnostic"));

        Map<String, String> env = loadProjectEnv();
        env.put("NQ_BINANCE_WS_DIAGNOSTIC_ENABLED", "true");
        BinanceRuntimeConfig runtimeConfig = BinanceRuntimeConfig.fromEnvironment(env);
        assertNotNull(runtimeConfig.credentials());

        LiveProbeEvidence probeEvidence = runStandaloneProbe(runtimeConfig);
        BinanceWsClient.BinanceWsDiagnosticSnapshot clientSnapshot = runClient(runtimeConfig);

        System.out.println("=== binance ws live diagnostic: probe ===");
        System.out.println("probe.url=" + runtimeConfig.wsUrl());
        System.out.println("probe.request=" + probeEvidence.requestSummary());
        System.out.println("probe.signingPayload=" + probeEvidence.signaturePayload());
        System.out.println("probe.response=" + probeEvidence.response());
        System.out.println("probe.close=" + probeEvidence.closeSummary());
        System.out.println("probe.pingObserved=" + probeEvidence.serverPingObserved());
        System.out.println("probe.pongObserved=" + probeEvidence.serverPongObserved());

        System.out.println("=== binance ws live diagnostic: client ===");
        System.out.println("client.request=" + clientSnapshot.subscribeRequestSummary());
        System.out.println("client.signingPayload=" + clientSnapshot.signaturePayload());
        System.out.println("client.recentFrames=" + clientSnapshot.recentFrames());
        System.out.println("client.recentLifecycle=" + clientSnapshot.recentLifecycleEvents());
        System.out.println("client.close=" + clientSnapshot.lastCloseSummary());
        System.out.println("client.serverPingObserved=" + clientSnapshot.serverPingObserved());
        System.out.println("client.serverPongObserved=" + clientSnapshot.serverPongObserved());
        System.out.println("client.clientPingSent=" + clientSnapshot.clientPingSent());
        System.out.println("client.clientPongSent=" + clientSnapshot.clientPongSent());
        System.out.println("client.localCloseSent=" + clientSnapshot.localCloseSent());
    }

    private BinanceWsClient.BinanceWsDiagnosticSnapshot runClient(BinanceRuntimeConfig runtimeConfig) throws Exception {
        BinanceWsClient client = new BinanceWsClient(
                HttpClient.newHttpClient(),
                MAPPER,
                runtimeConfig,
                Clock.systemUTC()
        );
        CountDownLatch closeLatch = new CountDownLatch(1);
        client.addConnectionListener(new BinanceWsConnectionListener() {
            @Override
            public void onDisconnected(String reason, int attempt, long delayMs, String traceId) {
                closeLatch.countDown();
            }
        });
        client.start();
        try {
            closeLatch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Thread.sleep(1_000L);
            return client.diagnosticSnapshot();
        } finally {
            client.stop();
        }
    }

    private LiveProbeEvidence runStandaloneProbe(BinanceRuntimeConfig runtimeConfig) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        AtomicReference<String> responseRef = new AtomicReference<>("");
        AtomicReference<String> closeRef = new AtomicReference<>("");
        AtomicReference<Boolean> serverPingRef = new AtomicReference<>(false);
        AtomicReference<Boolean> serverPongRef = new AtomicReference<>(false);
        CountDownLatch latch = new CountDownLatch(1);

        ProbeRequest probeRequest = buildProbeRequest(runtimeConfig.credentials(), Clock.systemUTC());
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                webSocket.sendText(probeRequest.rawJson(), true);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                responseRef.compareAndSet("", data.toString());
                webSocket.request(1);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                serverPingRef.set(true);
                ByteBuffer copy = ByteBuffer.allocate(message.remaining());
                copy.put(message.slice());
                copy.flip();
                webSocket.sendPong(copy);
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                serverPongRef.set(true);
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closeRef.set("code=" + statusCode + ", reason=" + reason);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                closeRef.set("error=" + error.getMessage());
                latch.countDown();
            }
        };

        WebSocket webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(runtimeConfig.wsUrl()), listener)
                .join();
        latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        webSocket.abort();
        return new LiveProbeEvidence(
                probeRequest.requestSummary(),
                probeRequest.signaturePayload(),
                responseRef.get(),
                closeRef.get(),
                serverPingRef.get(),
                serverPongRef.get()
        );
    }

    private ProbeRequest buildProbeRequest(BinanceApiCredentials credentials, Clock clock) throws Exception {
        Map<String, String> signedParams = new HashMap<>();
        signedParams.put("apiKey", credentials.apiKey());
        signedParams.put("timestamp", String.valueOf(clock.instant().toEpochMilli()));
        String signaturePayload = signedParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        String signature = new BinanceRequestSigner().sign(signaturePayload, credentials);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("apiKey", maskApiKey(credentials.apiKey()));
        params.put("timestamp", Long.parseLong(signedParams.get("timestamp")));
        params.put("signature", maskSignature(signature));

        ObjectNode request = MAPPER.createObjectNode();
        request.put("id", 1);
        request.put("method", "userDataStream.subscribe.signature");
        request.set("params", params);

        ObjectNode rawParams = MAPPER.createObjectNode();
        rawParams.put("apiKey", credentials.apiKey());
        rawParams.put("timestamp", Long.parseLong(signedParams.get("timestamp")));
        rawParams.put("signature", signature);
        ObjectNode rawRequest = MAPPER.createObjectNode();
        rawRequest.put("id", 1);
        rawRequest.put("method", "userDataStream.subscribe.signature");
        rawRequest.set("params", rawParams);
        return new ProbeRequest(request.toString(), signaturePayload.replace(credentials.apiKey(), maskApiKey(credentials.apiKey())), rawRequest.toString());
    }

    private Map<String, String> loadProjectEnv() throws Exception {
        Path envPath = Path.of("..", "..", ".env").normalize();
        Map<String, String> env = new HashMap<>();
        for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int index = trimmed.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = trimmed.substring(0, index).trim();
            String value = trimmed.substring(index + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            env.put(key, value);
        }
        return env;
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "missing";
        }
        return apiKey.length() <= 8
                ? apiKey.charAt(0) + "***" + apiKey.charAt(apiKey.length() - 1)
                : apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private static String maskSignature(String signature) throws Exception {
        if (signature == null || signature.isBlank()) {
            return "missing";
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String sha256 = java.util.HexFormat.of().formatHex(digest.digest(signature.getBytes(StandardCharsets.UTF_8)));
        return signature.substring(0, Math.min(6, signature.length()))
                + "..."
                + signature.substring(Math.max(0, signature.length() - 6))
                + "#sha256="
                + sha256;
    }

    private record ProbeRequest(String requestSummary, String signaturePayload, String rawJson) {
    }

    private record LiveProbeEvidence(
            String requestSummary,
            String signaturePayload,
            String response,
            String closeSummary,
            boolean serverPingObserved,
            boolean serverPongObserved
    ) {
    }
}
