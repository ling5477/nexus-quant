package com.guidinglight.nexusquant.adapter.binance.fee;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;
import com.guidinglight.nexusquant.adapter.binance.signing.BinanceRequestSigner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 固定现货只读GET：时钟、API权限、账户commission；不暴露通用请求或交易方法。
 * 只能在基础设施精确凭据回调内使用，不授予账户/联网资格，不从环境读取材料，也不持有调用方凭据。
 */
public final class BinanceCommissionHttpReader {
    private final HttpClient http;
    private final URI endpoint;
    private final Clock clock;
    private final Duration perRequest;
    private final BinanceRequestSigner signer = new BinanceRequestSigner();
    private final BinanceCommissionCodec codec = new BinanceCommissionCodec();
    private final ObjectMapper mapper =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(8)
                                                    .maxStringLength(4096)
                                                    .maxNumberLength(48)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 明确直连且禁止重定向，避免依赖宿主默认代理将私有认证请求转交另一网络主体。 */
    public static HttpClient directHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new DirectProxy())
                .build();
    }

    /** 生产端点与环境必须匹配；隔离fixture端点只能显式选择并限制为127.0.0.1，绝不自动回退公网。 */
    public BinanceCommissionHttpReader(
            HttpClient http,
            URI endpoint,
            Environment environment,
            Duration perRequest,
            Clock clock) {
        this.http = Objects.requireNonNull(http);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.clock = Objects.requireNonNull(clock);
        this.perRequest = Objects.requireNonNull(perRequest);
        // Spot测试网仅提供/api，缺少必要的/sapi权限证据；联网前明确拒绝，禁止跳过检查或回退主网。
        if (environment == Environment.TESTNET)
            throw error(BinanceFeeReadException.Reason.ENVIRONMENT_UNSUPPORTED);
        if (environment == null
                || endpoint.getUserInfo() != null
                || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null
                || !Set.of("", "/").contains(endpoint.getPath())
                || http.followRedirects() != HttpClient.Redirect.NEVER
                || !(http.proxy().orElse(null) instanceof DirectProxy)
                || http.connectTimeout().isEmpty()
                || http.connectTimeout().get().compareTo(Duration.ofSeconds(3)) > 0
                || perRequest.isZero()
                || perRequest.isNegative()
                || perRequest.compareTo(Duration.ofSeconds(4)) > 0)
            throw error(BinanceFeeReadException.Reason.PRIVATE_SCOPE_DENIED);
        boolean accepted =
                switch (environment) {
                    case PRODUCTION ->
                            "https".equals(endpoint.getScheme())
                                    && "api.binance.com".equals(endpoint.getHost())
                                    && (endpoint.getPort() == -1 || endpoint.getPort() == 443);
                    case TESTNET ->
                            "https".equals(endpoint.getScheme())
                                    && "testnet.binance.vision".equals(endpoint.getHost())
                                    && (endpoint.getPort() == -1 || endpoint.getPort() == 443);
                    case ISOLATED_FIXTURE ->
                            "http".equals(endpoint.getScheme())
                                    && "127.0.0.1".equals(endpoint.getHost())
                                    && endpoint.getPort() > 0;
                };
        if (!accepted) throw error(BinanceFeeReadException.Reason.PRIVATE_SCOPE_DENIED);
    }

    /**
     * 使用同一显式凭据和一次完整权限检查读取费用；全部网络请求有总deadline且不重试。
     * 每个固定GET前后回调核验调用方原安全状态；接口不返回权限或IP的猜测证明。
     */
    public BinanceCommissionSnapshot read(
            String symbol,
            BinanceApiCredentials credentials,
            PermissionScope permission,
            Runnable boundary) {
        if (!BinanceCommissionSnapshot.validNativeSymbol(symbol)
                || permission == null
                || boundary == null)
            throw error(BinanceFeeReadException.Reason.PRIVATE_SCOPE_DENIED);
        requireCredentials(credentials);
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        boundary.run();
        JsonNode time = json(get("/api/v3/time", "", null, deadline).body());
        if (!time.path("serverTime").isIntegralNumber()
                || !time.path("serverTime").canConvertToLong()
                || time.path("serverTime").longValue() <= 0
                || clock.millis() <= 0
                || Math.abs(time.path("serverTime").longValue() - clock.millis()) > 30000)
            throw error(BinanceFeeReadException.Reason.INVALID_PROVIDER_TIME);
        long serverTime = time.path("serverTime").longValue();
        long monotonicTime = System.nanoTime();
        boundary.run();
        JsonNode restrictions =
                json(
                        signed(
                                        "/sapi/v1/account/apiRestrictions",
                                        "",
                                        credentials,
                                        timestamp(serverTime, monotonicTime),
                                        deadline)
                                .body());
        permissions(restrictions, permission);
        boundary.run();
        // 签名覆盖发送的UTF-8百分号编码query，避免非ASCII原生symbol在URI构造或签名中发生歧义。
        var response =
                signed(
                        "/api/v3/account/commission",
                        "symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                        credentials,
                        timestamp(serverTime, monotonicTime),
                        deadline);
        boundary.run();
        return codec.decode(response.body(), symbol, clock.instant(), response.date());
    }

    private static long timestamp(long serverTime, long monotonicTime) {
        return serverTime + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - monotonicTime);
    }

    private Reply signed(
            String path,
            String arguments,
            BinanceApiCredentials credentials,
            long timestamp,
            long deadline) {
        String query =
                (arguments.isEmpty() ? "" : arguments + "&")
                        + "timestamp="
                        + timestamp
                        + "&recvWindow=5000";
        try {
            return get(
                    path,
                    query
                            + "&signature="
                            + URLEncoder.encode(
                                    signer.sign(query, credentials), StandardCharsets.UTF_8),
                    credentials.apiKey(),
                    deadline);
        } catch (BinanceFeeReadException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw error(BinanceFeeReadException.Reason.CREDENTIAL_UNAVAILABLE);
        }
    }

    private Reply get(String path, String query, String key, long deadline) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw error(BinanceFeeReadException.Reason.TIMEOUT);
            Duration timeout = Duration.ofNanos(Math.min(remaining, perRequest.toNanos()));
            String origin = endpoint.toString().replaceAll("/$", "");
            var builder =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            origin + path + (query.isEmpty() ? "" : "?" + query)))
                            .timeout(timeout)
                            .GET();
            if (key != null) builder.header("X-MBX-APIKEY", key);
            pending = http.sendAsync(builder.build(), response -> new LimitedBody());
            var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200)
                throw error(
                        response.statusCode() == 401 || response.statusCode() == 403
                                ? BinanceFeeReadException.Reason.REMOTE_PERMISSION_DENIED
                                : BinanceFeeReadException.Reason.SOURCE_UNAVAILABLE);
            String body =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(response.body()))
                            .toString();
            return new Reply(body, response.headers().firstValue("Date").orElse(null));
        } catch (BinanceFeeReadException failure) {
            throw failure;
        } catch (TimeoutException failure) {
            throw error(BinanceFeeReadException.Reason.TIMEOUT);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw error(BinanceFeeReadException.Reason.INTERRUPTED);
        } catch (CharacterCodingException failure) {
            throw error(BinanceFeeReadException.Reason.INVALID_JSON);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            for (int depth = 0;
                    depth < 3
                            && (cause instanceof CompletionException
                                    || cause instanceof ExecutionException);
                    depth++) cause = cause.getCause();
            if (cause instanceof BinanceFeeReadException bounded) throw bounded;
            if (cause instanceof HttpTimeoutException)
                throw error(BinanceFeeReadException.Reason.TIMEOUT);
            throw error(BinanceFeeReadException.Reason.SOURCE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw error(BinanceFeeReadException.Reason.SOURCE_UNAVAILABLE);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception failure) {
            throw error(BinanceFeeReadException.Reason.INVALID_JSON);
        }
    }

    /** 远端响应必须明确只读/指定TRADE范围，禁止withdraw/transfer/derivatives等额外权限；未知true权限也拒绝。 */
    private static void permissions(JsonNode node, PermissionScope expected) {
        var required =
                Map.of(
                        "ipRestrict",
                        true,
                        "enableReading",
                        true,
                        "enableWithdrawals",
                        false,
                        "enableInternalTransfer",
                        false,
                        "enableSpotAndMarginTrading",
                        expected == PermissionScope.TRADE,
                        "enableMargin",
                        false,
                        "enableFutures",
                        false,
                        "enableVanillaOptions",
                        false);
        if (node == null
                || !node.isObject()
                || required.entrySet().stream()
                        .anyMatch(
                                entry ->
                                        !node.path(entry.getKey()).isBoolean()
                                                || node.path(entry.getKey()).booleanValue()
                                                        != entry.getValue()))
            throw error(BinanceFeeReadException.Reason.REMOTE_PERMISSION_DENIED);
        node.properties()
                .forEach(
                        entry -> {
                            if (!required.containsKey(entry.getKey())
                                    && entry.getValue().isBoolean()
                                    && entry.getValue().booleanValue())
                                throw error(
                                        BinanceFeeReadException.Reason.REMOTE_PERMISSION_DENIED);
                        });
    }

    /** Ed25519仅接受该凭据的内存PEM；绝不沿privateKeyPath读取宿主文件，也不从全局配置补缺失材料。 */
    private static void requireCredentials(BinanceApiCredentials value) {
        if (value == null
                || !value.isConfigured()
                || value.apiKey().length() > 256
                || !value.apiKey().matches("[A-Za-z0-9_-]+")
                || value.privateKeyPath() != null && !value.privateKeyPath().isBlank()
                || value.keyType() == BinanceKeyType.ED25519
                        && (value.privateKey() == null
                                || value.privateKey().isBlank()
                                || value.privateKey().length() > 16384)
                || value.keyType() == BinanceKeyType.HMAC_SHA256
                        && value.secretKey().length() > 1024)
            throw error(BinanceFeeReadException.Reason.CREDENTIAL_UNAVAILABLE);
    }

    private static BinanceFeeReadException error(BinanceFeeReadException.Reason reason) {
        return new BinanceFeeReadException(reason);
    }

    private record Reply(String body, String date) {}

    public enum Environment {
        PRODUCTION,
        TESTNET,
        ISOLATED_FIXTURE
    }

    public enum PermissionScope {
        READ_ONLY,
        TRADE
    }

    private static final class DirectProxy extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            /* 请求失败由有界HTTP路径处理，不记录认证URI。 */
        }
    }

    /** 响应体最多64KiB；超限立即取消订阅，不等待无限响应结束，不把原文附入异常。 */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > BinanceCommissionCodec.MAX_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(
                            error(BinanceFeeReadException.Reason.RESPONSE_TOO_LARGE));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            result.completeExceptionally(error(BinanceFeeReadException.Reason.SOURCE_UNAVAILABLE));
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
