package com.guidinglight.nexusquant.adapter.binance.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 仅127.0.0.1上的合成供应方与即时生成密钥；断言固定GET、签名、权限及超时，不连接Binance或真实私有网络。 */
class BinanceCommissionHttpReaderTest {
    private HttpServer server;
    private ExecutorService workers;
    private HttpClient client;
    private final Clock clock = Clock.systemUTC();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final AtomicInteger validSignatures = new AtomicInteger();
    private String commission = BinanceCommissionCodecTest.payload();
    private String permissions =
            """
            {"ipRestrict":true,"enableReading":true,"enableWithdrawals":false,"enableInternalTransfer":false,
             "enableSpotAndMarginTrading":false,"enableMargin":false,"enableFutures":false,"enableVanillaOptions":false}
            """;
    private int commissionStatus = 200;
    private boolean stallBody;
    private PublicKey edPublic;
    private String requestedSymbol;
    private static final String KEY = "fixture-key";
    private static final String SECRET = "fixture-only-signer-material";

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/", this::respond);
        server.start();
        client = BinanceCommissionHttpReader.directHttpClient();
    }

    @AfterEach
    void close() throws InterruptedException {
        server.stop(0);
        workers.shutdownNow();
        client.shutdownNow();
        client.awaitTermination(Duration.ofSeconds(3));
    }

    private BinanceCommissionHttpReader reader(Duration timeout) {
        return new BinanceCommissionHttpReader(
                client,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                BinanceCommissionHttpReader.Environment.ISOLATED_FIXTURE,
                timeout,
                clock);
    }

    @Test
    void hmacReadsOnlyTheThreeFixedGetsAndKeepsNativeCommission() {
        AtomicInteger boundaries = new AtomicInteger();
        var result =
                reader(Duration.ofSeconds(2))
                        .read(
                                "BTCUSDT",
                                new BinanceApiCredentials(KEY, SECRET),
                                BinanceCommissionHttpReader.PermissionScope.READ_ONLY,
                                boundaries::incrementAndGet);
        assertEquals("BTCUSDT", result.symbol());
        assertEquals("HTTP_RESPONSE_DATE", result.providerTimeBasis());
        assertEquals(2, validSignatures.get());
        assertEquals(4, boundaries.get());
        assertEquals(
                List.of(
                        "/api/v3/time",
                        "/sapi/v1/account/apiRestrictions",
                        "/api/v3/account/commission"),
                paths);
    }

    @Test
    void supportsInlineEd25519WithoutReadingAnyKeyPath() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        edPublic = pair.getPublic();
        String pem =
                "-----BEGIN %s-----\n".formatted("PRIVATE KEY")
                        + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                        + "\n-----END %s-----".formatted("PRIVATE KEY");
        var credential = new BinanceApiCredentials(KEY, null, BinanceKeyType.ED25519, pem, null);
        assertEquals(
                "BTCUSDT",
                reader(Duration.ofSeconds(2))
                        .read(
                                "BTCUSDT",
                                credential,
                                BinanceCommissionHttpReader.PermissionScope.READ_ONLY,
                                () -> {})
                        .symbol());
        assertEquals(2, validSignatures.get());
    }

    /** 官方原生协议兼容不等于开放新的NQ市场；探针只在本地fixture验证完整编码及签名。 */
    @Test
    void unicodeNativeSymbolIsEncodedSignedAndMatchedExactly() {
        String symbol = "测试USDT";
        commission = commission.replace("BTCUSDT", symbol);
        var result =
                reader(Duration.ofSeconds(2))
                        .read(
                                symbol,
                                new BinanceApiCredentials(KEY, SECRET),
                                BinanceCommissionHttpReader.PermissionScope.READ_ONLY,
                                () -> {});
        assertEquals(symbol, result.symbol());
        assertEquals(symbol, requestedSymbol);
        assertEquals(2, validSignatures.get());
    }

    @Test
    void unsupportedTestnetFailsBeforeAnyRequestOrCredentialUse() {
        var failure =
                assertThrows(
                        BinanceFeeReadException.class,
                        () ->
                                new BinanceCommissionHttpReader(
                                        client,
                                        URI.create("https://testnet.binance.vision"),
                                        BinanceCommissionHttpReader.Environment.TESTNET,
                                        Duration.ofSeconds(2),
                                        clock));
        assertEquals(BinanceFeeReadException.Reason.ENVIRONMENT_UNSUPPORTED, failure.reason());
        assertTrue(paths.isEmpty());
        assertEquals(0, validSignatures.get());
    }

    @Test
    void missingCredentialsOrAKeyPathProduceNoNetworkRequests() {
        for (var credential :
                new BinanceApiCredentials[] {
                    null,
                    BinanceApiCredentials.unconfigured(),
                    new BinanceApiCredentials(
                            KEY, null, BinanceKeyType.ED25519, null, "must-not-be-opened.pem")
                }) {
            assertEquals(
                    BinanceFeeReadException.Reason.CREDENTIAL_UNAVAILABLE,
                    assertThrows(
                                    BinanceFeeReadException.class,
                                    () ->
                                            reader(Duration.ofSeconds(2))
                                                    .read(
                                                            "BTCUSDT",
                                                            credential,
                                                            BinanceCommissionHttpReader
                                                                    .PermissionScope.READ_ONLY,
                                                            () -> {}))
                            .reason());
        }
        assertTrue(paths.isEmpty());
    }

    @Test
    void broadOrUnknownRemotePermissionsStopBeforeCommission() {
        for (String changed :
                new String[] {
                    permissions.replace(
                            "\"enableWithdrawals\":false", "\"enableWithdrawals\":true"),
                    permissions.replace("\"ipRestrict\":true", "\"ipRestrict\":false"),
                    permissions.replace("\"enableReading\":true", "\"enableReading\":false"),
                    permissions.replace(
                            "\"enableMargin\":false",
                            "\"unknownAccess\":true,\"enableMargin\":false")
                }) {
            String original = permissions;
            permissions = changed;
            paths.clear();
            assertEquals(
                    BinanceFeeReadException.Reason.REMOTE_PERMISSION_DENIED,
                    assertThrows(
                                    BinanceFeeReadException.class,
                                    () ->
                                            reader(Duration.ofSeconds(2))
                                                    .read(
                                                            "BTCUSDT",
                                                            new BinanceApiCredentials(KEY, SECRET),
                                                            BinanceCommissionHttpReader
                                                                    .PermissionScope.READ_ONLY,
                                                            () -> {}))
                            .reason());
            assertEquals(List.of("/api/v3/time", "/sapi/v1/account/apiRestrictions"), paths);
            permissions = original;
        }
    }

    @Test
    void changedCallerBoundaryStopsBeforeTheNextPrivateGet() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        reader(Duration.ofSeconds(2))
                                .read(
                                        "BTCUSDT",
                                        new BinanceApiCredentials(KEY, SECRET),
                                        BinanceCommissionHttpReader.PermissionScope.READ_ONLY,
                                        () -> {
                                            if (checks.incrementAndGet() == 3)
                                                throw new IllegalStateException("BOUNDARY_CHANGED");
                                        }));
        assertEquals(List.of("/api/v3/time", "/sapi/v1/account/apiRestrictions"), paths);
    }

    @Test
    void oversizedResponseIsCanceledWithoutExposingProviderText() {
        commission = commission + " ".repeat(70000);
        var failure =
                assertThrows(
                        BinanceFeeReadException.class,
                        () ->
                                reader(Duration.ofSeconds(2))
                                        .read(
                                                "BTCUSDT",
                                                new BinanceApiCredentials(KEY, SECRET),
                                                BinanceCommissionHttpReader.PermissionScope
                                                        .READ_ONLY,
                                                () -> {}));
        assertEquals(BinanceFeeReadException.Reason.RESPONSE_TOO_LARGE, failure.reason());
        assertNull(failure.getCause());
    }

    @Test
    void stalledBodyTimesOutAndDoesNotRetry() {
        stallBody = true;
        long start = System.nanoTime();
        var failure =
                assertThrows(
                        BinanceFeeReadException.class,
                        () ->
                                reader(Duration.ofMillis(500))
                                        .read(
                                                "BTCUSDT",
                                                new BinanceApiCredentials(KEY, SECRET),
                                                BinanceCommissionHttpReader.PermissionScope
                                                        .READ_ONLY,
                                                () -> {}));
        assertEquals(BinanceFeeReadException.Reason.TIMEOUT, failure.reason());
        assertTrue(
                Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(3)) < 0);
        assertEquals(3, paths.size());
    }

    @Test
    void privateErrorPayloadIsNeverReturnedOrKeptAsAnExceptionCause() {
        commissionStatus = 401;
        commission = "{\"msg\":\"" + KEY + ":" + SECRET + "\"}";
        var failure =
                assertThrows(
                        BinanceFeeReadException.class,
                        () ->
                                reader(Duration.ofSeconds(2))
                                        .read(
                                                "BTCUSDT",
                                                new BinanceApiCredentials(KEY, SECRET),
                                                BinanceCommissionHttpReader.PermissionScope
                                                        .READ_ONLY,
                                                () -> {}));
        assertEquals(BinanceFeeReadException.Reason.REMOTE_PERMISSION_DENIED, failure.reason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(KEY));
        assertFalse(failure.toString().contains(SECRET));
    }

    @Test
    void endpointEnvironmentAndDefaultProxyAreRejectedBeforeNetwork() {
        for (URI target :
                List.of(
                        URI.create("https://api.binance.com.evil.invalid"),
                        URI.create("https://api.binance.com/?x=1"),
                        URI.create("https://user@api.binance.com"),
                        URI.create("https://api.binance.com/custom")))
            assertThrows(
                    BinanceFeeReadException.class,
                    () ->
                            new BinanceCommissionHttpReader(
                                    client,
                                    target,
                                    BinanceCommissionHttpReader.Environment.PRODUCTION,
                                    Duration.ofSeconds(2),
                                    clock));
        try (var unsafe = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertThrows(
                    BinanceFeeReadException.class,
                    () ->
                            new BinanceCommissionHttpReader(
                                    unsafe,
                                    URI.create("https://api.binance.com"),
                                    BinanceCommissionHttpReader.Environment.PRODUCTION,
                                    Duration.ofSeconds(2),
                                    clock));
        }
        assertTrue(paths.isEmpty());
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            if ("/api/v3/account/commission".equals(path)) {
                String raw = exchange.getRequestURI().getRawQuery();
                requestedSymbol =
                        URLDecoder.decode(
                                raw.substring("symbol=".length(), raw.indexOf('&')),
                                StandardCharsets.UTF_8);
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!"/api/v3/time".equals(path) && !valid(exchange)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            String body =
                    switch (path) {
                        case "/api/v3/time" -> "{\"serverTime\":" + clock.millis() + "}";
                        case "/sapi/v1/account/apiRestrictions" -> permissions;
                        case "/api/v3/account/commission" -> commission;
                        default -> "{}";
                    };
            if (stallBody && "/api/v3/account/commission".equals(path)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(
                    "/api/v3/account/commission".equals(path) ? commissionStatus : 200,
                    bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    /** 只保存路径与校验结果，不将完整认证query或材料写到测试输出。 */
    private boolean valid(HttpExchange exchange) {
        try {
            String raw = exchange.getRequestURI().getRawQuery();
            int split = raw.lastIndexOf("&signature=");
            if (!KEY.equals(exchange.getRequestHeaders().getFirst("X-MBX-APIKEY")) || split < 0)
                return false;
            String query = raw.substring(0, split),
                    signature =
                            URLDecoder.decode(raw.substring(split + 11), StandardCharsets.UTF_8);
            boolean result;
            if (edPublic == null) {
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                result =
                        HexFormat.of()
                                .formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)))
                                .equals(signature);
            } else {
                var verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(edPublic);
                verifier.update(query.getBytes(StandardCharsets.UTF_8));
                result = verifier.verify(Base64.getDecoder().decode(signature));
            }
            if (result) validSignatures.incrementAndGet();
            return result;
        } catch (Exception failure) {
            return false;
        }
    }
}
