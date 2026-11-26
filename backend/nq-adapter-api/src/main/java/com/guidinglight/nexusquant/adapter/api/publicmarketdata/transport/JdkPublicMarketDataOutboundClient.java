package com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.config.PublicMarketDataOutboundSettings;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundDecision;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundErrorCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundResult;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataQualitySummary;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.policy.PublicMarketDataOutboundPolicy;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.port.PublicMarketDataOutboundClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DateTimeException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundErrorCategory.*;

/**
 * JdkPublicMarketDataOutboundClient 是受控 public REST outbound 的最小 JDK HTTP 实现。
 *
 * <p>Why: O-1 需要证明 manual profile / feature flag 打开后才可能构造真实 HTTP client，同时所有
 * endpoint 必须先过 policy，timeout/retry/backoff 必须有上限，结果必须脱敏。测试只能使用 localhost
 * fake server；本类不读取 credential、不构造 signed request、不支持 private WebSocket。</p>
 *
 * <p>线程安全：JDK HttpClient 可复用；本类无可变业务状态。失败模式：返回错误分类，不抛出包含
 * raw URL query、headers 或 response body 的异常。</p>
 */
public final class JdkPublicMarketDataOutboundClient implements PublicMarketDataOutboundClient {

    private static final Duration SNAPSHOT_MAX_TIMEOUT = Duration.ofSeconds(8);
    private static final String SPOT_ID = "[A-Z0-9]{1,24}-[A-Z0-9]{1,24}";
    private static final Pattern TICKER_PATH = Pattern.compile("/api/v5/market/ticker\\?instId=" + SPOT_ID);
    private static final Pattern CANDLES_PATH = Pattern.compile(
            "/api/v5/market/candles\\?instId=" + SPOT_ID + "&bar=(1m|5m|15m|1H|4H|1D)&limit=100");

    @FunctionalInterface
    interface BackoffSleeper {
        void sleep(Duration backoff) throws InterruptedException;
    }

    private final URI baseUri;
    private final PublicMarketDataOutboundPolicy policy;
    private final PublicMarketDataOutboundSettings settings;
    private final HttpClient httpClient;
    private final Clock clock;
    private final BackoffSleeper sleeper;
    private final BooleanSupplier outboundAllowed;
    private Instant snapshotRetryAt = Instant.EPOCH;

    /**
     * 生产构造器；只应由 `public-marketdata-manual` profile + enabled=true 装配。
     *
     * @param baseUri  公开行情 base URI；由手动 profile 注入
     * @param policy   allowlist / denylist 策略
     * @param settings timeout / retry 设置
     */
    public JdkPublicMarketDataOutboundClient(
            URI baseUri,
            PublicMarketDataOutboundPolicy policy,
            PublicMarketDataOutboundSettings settings
    ) {
        this(baseUri, policy, settings, () -> true);
    }

    public JdkPublicMarketDataOutboundClient(
            URI baseUri, PublicMarketDataOutboundPolicy policy,
            PublicMarketDataOutboundSettings settings, BooleanSupplier outboundAllowed
    ) {
        this(baseUri, policy, settings,
                HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build(),
                Clock.systemUTC(), backoff -> Thread.sleep(backoff.toMillis()), outboundAllowed);
    }

    JdkPublicMarketDataOutboundClient(
            URI baseUri,
            PublicMarketDataOutboundPolicy policy,
            PublicMarketDataOutboundSettings settings,
            HttpClient httpClient,
            Clock clock,
            BackoffSleeper sleeper
    ) {
        this(baseUri, policy, settings, httpClient, clock, sleeper, () -> true);
    }

    private JdkPublicMarketDataOutboundClient(
            URI baseUri, PublicMarketDataOutboundPolicy policy, PublicMarketDataOutboundSettings settings,
            HttpClient httpClient, Clock clock, BackoffSleeper sleeper, BooleanSupplier outboundAllowed
    ) {
        this.baseUri = validateBaseUri(baseUri);
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
        this.outboundAllowed = Objects.requireNonNull(outboundAllowed);
    }

    @Override
    public SnapshotResponse fetchSnapshot(PublicMarketDataOutboundRequest request) {
        String path = request.endpointPath();
        boolean allowedPath = switch (request.endpointCategory()) {
            case TICKER -> TICKER_PATH.matcher(path).matches();
            case OHLCV -> CANDLES_PATH.matcher(path).matches();
            default -> false;
        };
        if (!"OKX".equals(request.exchange()) || !allowedPath || !snapshotOriginAllowed()
                || !policy.evaluate(request).allowed() || !resolvedTargetDecision(request).allowed()) {
            return snapshotFailure(DENIED);
        }

        Duration timeout = settings.totalRequestTimeout().compareTo(SNAPSHOT_MAX_TIMEOUT) > 0
                ? SNAPSHOT_MAX_TIMEOUT : settings.totalRequestTimeout();
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int attempt = 1; ; attempt++) {
            if (!policy.evaluate(request).allowed()) return snapshotFailure(DENIED);
            SnapshotResponse result = readSnapshot(path, deadline);
            // 限流按上游冷却时间处理；超时已经耗尽本次请求预算。
            if ((result.error() != TEMPORARY_FAILURE && result.error() != TRANSPORT_ERROR)
                    || attempt >= settings.maxAttempts()) return result;
            Duration delay = settings.backoffForRetry(attempt);
            if (delay.compareTo(Duration.ofNanos(Math.max(0, deadline - System.nanoTime()))) >= 0) {
                return result;
            }
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return snapshotFailure(TRANSPORT_ERROR);
            }
        }
    }

    @Override
    public synchronized Instant snapshotRetryAt() {
        return snapshotRetryAt;
    }

    @Override
    public synchronized Instant deferSnapshotUntil(Instant retryAt) {
        if (retryAt.isAfter(snapshotRetryAt)) snapshotRetryAt = retryAt;
        return snapshotRetryAt;
    }

    private SnapshotResponse readSnapshot(String path, long deadline) {
        BoundedPublicResponseReader.SnapshotRead read;
        synchronized (this) {
            // 限流登记与启动请求使用同一把锁。等待响应和退避均在锁外。
            if (!outboundAllowed.getAsBoolean()) return snapshotFailure(DISABLED);
            if (clock.instant().isBefore(snapshotRetryAt)) {
                return new SnapshotResponse(RATE_LIMITED, null, null, snapshotRetryAt);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return snapshotFailure(TIMEOUT);
            var request = HttpRequest.newBuilder(resolve(path)).timeout(Duration.ofNanos(remaining)).GET().build();
            read = BoundedPublicResponseReader.startSnapshot(httpClient, request, headers -> {
                if (headers.statusCode() == 429) {
                    deferSnapshotUntil(retryAfter(headers.headers().firstValue("Retry-After").orElse(""),
                            clock.instant()));
                }
            });
        }
        try {
            var response = read.awaitBody(Duration.ofNanos(deadline - System.nanoTime()));
            int status = response.statusCode();
            Instant receivedAt = clock.instant();
            if (status == 429) return new SnapshotResponse(RATE_LIMITED, null, null, snapshotRetryAt());
            if (status >= 500) return snapshotFailure(TEMPORARY_FAILURE);
            return status == 200
                    ? new SnapshotResponse(NONE, response.body(), receivedAt, null)
                    : snapshotFailure(INVALID_RESPONSE);
        } catch (HttpTimeoutException ex) {
            return snapshotFailure(TIMEOUT);
        } catch (IOException ex) {
            return snapshotFailure(TRANSPORT_ERROR);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return snapshotFailure(TRANSPORT_ERROR);
        } catch (IllegalStateException ex) {
            return snapshotFailure(INVALID_RESPONSE);
        }
    }

    private static Instant retryAfter(String value, Instant receivedAt) {
        try {
            Instant retryAt = value.matches("[0-9]{1,10}")
                    ? receivedAt.plusSeconds(Long.parseLong(value))
                    : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            if (!retryAt.isBefore(receivedAt)) return retryAt;
        } catch (DateTimeException ignored) {
            // 请求端至少等待一个刷新周期，避免无有效 Retry-After 时立即重试。
        }
        return receivedAt.plusSeconds(30);
    }

    private boolean snapshotOriginAllowed() {
        String host = baseUri.getHost();
        String path = baseUri.getPath();
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || !(path.isEmpty() || "/".equals(path))) return false;
        return ("https".equals(baseUri.getScheme()) && effectivePort(baseUri) == 443
                        && Set.of("www.okx.com", "okx.com").contains(host))
                || ("http".equals(baseUri.getScheme()) && Set.of("127.0.0.1", "localhost").contains(host)
                        && baseUri.getPort() > 0);
    }

    private static SnapshotResponse snapshotFailure(PublicMarketDataOutboundErrorCategory error) {
        return new SnapshotResponse(error, null, null, null);
    }

    @Override
    public PublicMarketDataOutboundResult fetch(PublicMarketDataOutboundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!outboundAllowed.getAsBoolean()) return disabledFallback(request);
        PublicMarketDataOutboundDecision decision = policy.evaluate(request);
        if (!decision.allowed()) {
            return PublicMarketDataOutboundResult.denied(request, decision);
        }
        PublicMarketDataOutboundDecision resolvedDecision = resolvedTargetDecision(request);
        if (!resolvedDecision.allowed()) {
            return PublicMarketDataOutboundResult.denied(request, resolvedDecision);
        }

        Instant startedAt = Instant.now(clock);
        PublicMarketDataOutboundResult latest = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            if (!outboundAllowed.getAsBoolean()) return disabledFallback(request);
            PublicMarketDataOutboundDecision retryDecision = policy.evaluate(request);
            if (!retryDecision.allowed()) {
                return PublicMarketDataOutboundResult.denied(request, retryDecision);
            }
            PublicMarketDataOutboundDecision retryResolvedDecision = resolvedTargetDecision(request);
            if (!retryResolvedDecision.allowed()) {
                return PublicMarketDataOutboundResult.denied(request, retryResolvedDecision);
            }
            latest = executeOnce(request, startedAt, attempt);
            if (!shouldRetry(latest.errorCategory()) || attempt >= settings.maxAttempts()) {
                return latest;
            }
            sleepBeforeRetry(attempt);
        }
        return latest == null ? disabledFallback(request) : latest;
    }

    private PublicMarketDataOutboundResult executeOnce(
            PublicMarketDataOutboundRequest request, Instant startedAt, int attempt) {
        HttpRequest httpRequest = HttpRequest.newBuilder(resolve(request.endpointPath()))
                .timeout(settings.totalRequestTimeout())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return resultFromResponse(request, response.statusCode(), response.body(), startedAt, attempt);
        } catch (HttpTimeoutException ex) {
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.TIMEOUT,
                    0,
                    startedAt,
                    attempt,
                    "public marketdata request timeout");
        } catch (IOException ex) {
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.TRANSPORT_ERROR,
                    0,
                    startedAt,
                    attempt,
                    "public marketdata transport error: " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.TIMEOUT,
                    0,
                    startedAt,
                    attempt,
                    "public marketdata request interrupted");
        }
    }

    private PublicMarketDataOutboundResult resultFromResponse(
            PublicMarketDataOutboundRequest request,
            int statusCode,
            String body,
            Instant startedAt,
            int attempt
    ) {
        if (statusCode == 429) {
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.RATE_LIMITED,
                    statusCode,
                    startedAt,
                    attempt,
                    "public marketdata rate limited");
        }
        if (statusCode >= 500) {
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.TEMPORARY_FAILURE,
                    statusCode,
                    startedAt,
                    attempt,
                    "public marketdata temporary server failure");
        }
        if (statusCode < 200 || statusCode >= 300 || body == null || body.isBlank()
                || !looksLikeJsonPayload(body)) {
            return failure(
                    request,
                    PublicMarketDataOutboundErrorCategory.INVALID_RESPONSE,
                    statusCode,
                    startedAt,
                    attempt,
                    "public marketdata invalid or malformed response");
        }
        return new PublicMarketDataOutboundResult(
                request.exchange(),
                request.endpointCategory(),
                PublicMarketDataOutboundErrorCategory.NONE,
                statusCode,
                elapsedSince(startedAt),
                attempt,
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                false,
                0,
                false,
                Instant.now(clock),
                "public marketdata response accepted without raw payload");
    }

    /**
     * 执行最小 malformed response 判定。
     *
     * <p>Why: O-1 只需要区分明显坏响应并映射到 INVALID_RESPONSE；这里不引入新的 JSON 解析依赖，
     * 只验证 payload framing，避免把纯文本、HTML 或空白响应误标成健康行情。</p>
     */
    private boolean looksLikeJsonPayload(String body) {
        String trimmed = body.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private PublicMarketDataOutboundResult failure(
            PublicMarketDataOutboundRequest request,
            PublicMarketDataOutboundErrorCategory category,
            int statusCode,
            Instant startedAt,
            int attempt,
            String message
    ) {
        return new PublicMarketDataOutboundResult(
                request.exchange(),
                request.endpointCategory(),
                category,
                statusCode,
                elapsedSince(startedAt),
                attempt,
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                0,
                false,
                0,
                false,
                Instant.now(clock),
                message);
    }

    private PublicMarketDataOutboundResult disabledFallback(PublicMarketDataOutboundRequest request) {
        return PublicMarketDataOutboundResult.disabled(
                request,
                PublicMarketDataQualitySummary.DataOrigin.LOCAL_DB,
                Instant.now(clock));
    }

    private URI resolve(String endpointPath) {
        URI resolved = baseUri.resolve(endpointPath);
        if (!sameNetworkEndpoint(baseUri, resolved)) {
            throw new IllegalArgumentException("resolved URI must stay within configured public marketdata base");
        }
        return resolved;
    }

    private Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now(clock));
    }

    private boolean shouldRetry(PublicMarketDataOutboundErrorCategory category) {
        return category == PublicMarketDataOutboundErrorCategory.RATE_LIMITED
                || category == PublicMarketDataOutboundErrorCategory.TIMEOUT
                || category == PublicMarketDataOutboundErrorCategory.TEMPORARY_FAILURE
                || category == PublicMarketDataOutboundErrorCategory.TRANSPORT_ERROR;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            sleeper.sleep(settings.backoffForRetry(attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static URI validateBaseUri(URI uri) {
        Objects.requireNonNull(uri, "baseUri must not be null");
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("baseUri must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("baseUri must include host and must not include user info");
        }
        return uri;
    }

    private PublicMarketDataOutboundDecision resolvedTargetDecision(PublicMarketDataOutboundRequest request) {
        try {
            resolve(request.endpointPath());
            return PublicMarketDataOutboundDecision.allow(request.endpointCategory(), Instant.now(clock));
        } catch (IllegalArgumentException ex) {
            return PublicMarketDataOutboundDecision.deny(
                    request.endpointCategory(),
                    "endpoint path cannot alter configured public marketdata base endpoint",
                    Instant.now(clock));
        }
    }

    private static boolean sameNetworkEndpoint(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected) == effectivePort(actual);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
