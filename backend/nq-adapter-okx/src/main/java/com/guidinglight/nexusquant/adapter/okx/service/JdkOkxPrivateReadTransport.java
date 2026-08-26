package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.EndpointPolicyDecision;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * OKX global host 专用 credential-scoped typed transport。
 *
 * <p>复用同一 signer、JDK client、NEVER redirect、无自动 retry、bounded response 与单并发；
 * 既有 read-only API 保持不变，GateY-6E capability 不带 Spring/runtime wiring。</p>
 */
public final class JdkOkxPrivateReadTransport implements OkxPrivateRealTransport {

    public static final URI GLOBAL_HOST = URI.create("https://openapi.okx.com");
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MAX_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern CURRENCY = Pattern.compile("[A-Z0-9]{2,12}");

    private final OkxSpotEndpointGuard endpointGuard;
    private final OkxPrivateRequestSigner signer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OkxPrivateHttpExchange exchange;
    private final OkxJdkRealClient realClient;
    private final Duration requestTimeout;
    private final Semaphore concurrency = new Semaphore(1, true);

    public JdkOkxPrivateReadTransport(ObjectMapper objectMapper, Clock clock) {
        this(objectMapper, clock, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public JdkOkxPrivateReadTransport(
            ObjectMapper objectMapper,
            Clock clock,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        this(
                objectMapper,
                clock,
                requestTimeout,
                jdkExchange(validateDuration(connectTimeout, MAX_CONNECT_TIMEOUT, "connectTimeout"))
        );
    }

    JdkOkxPrivateReadTransport(
            ObjectMapper objectMapper,
            Clock clock,
            Duration requestTimeout,
            OkxPrivateHttpExchange exchange
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.signer = new OkxPrivateRequestSigner(clock);
        this.endpointGuard = new OkxSpotEndpointGuard();
        this.requestTimeout = validateDuration(requestTimeout, MAX_REQUEST_TIMEOUT, "requestTimeout");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.realClient = new OkxJdkRealClient(
                this.objectMapper, this.clock, this.signer, this.exchange, this.requestTimeout);
    }

    @Override
    public OkxPilotPrerequisiteSnapshot observePrerequisites(
            OkxPilotPrerequisiteRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        return withPermit(() -> realClient.observePrerequisites(request, credential, environment));
    }

    @Override
    public OkxSpotProviderTransport.PlaceResponse placeLimit(
            OkxSpotProviderTransport.PlaceCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireProviderContract(OkxSpotProviderOperation.PLACE_LIMIT);
        return withPermit(() -> realClient.placeLimit(command, credential, environment));
    }

    @Override
    public OkxSpotProviderTransport.OrderResponse queryOrder(
            OkxSpotProviderTransport.OrderCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireProviderContract(OkxSpotProviderOperation.QUERY_ORDER);
        return withPermit(() -> realClient.queryOrder(
                command, credential, environment, OkxSpotProviderOperation.QUERY_ORDER));
    }

    @Override
    public OkxSpotProviderTransport.CancelResponse cancelOrder(
            OkxSpotProviderTransport.CancelCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireProviderContract(OkxSpotProviderOperation.CANCEL_ORDER);
        return withPermit(() -> realClient.cancelOrder(command, credential, environment));
    }

    @Override
    public OkxSpotProviderTransport.OrderResponse readOrder(
            OkxSpotProviderTransport.OrderCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireProviderContract(OkxSpotProviderOperation.READ_ORDER);
        return withPermit(() -> realClient.queryOrder(
                command, credential, environment, OkxSpotProviderOperation.READ_ORDER));
    }

    @Override
    public OkxSpotProviderTransport.FillResponse readFills(
            OkxSpotProviderTransport.FillCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        requireProviderContract(OkxSpotProviderOperation.READ_FILLS);
        return withPermit(() -> realClient.readFills(command, credential, environment));
    }

    @Override
    public OkxSpotProviderTransport.ClockResponse readClock(OkxSpotProviderTransport.ClockCommand command) {
        requireProviderContract(OkxSpotProviderOperation.READ_CLOCK);
        return withPermit(() -> realClient.readClock(command));
    }

    @Override
    public OkxPrivateReadResult execute(
            OkxPrivateReadRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        Objects.requireNonNull(environment, "environment must not be null");
        EndpointPolicyDecision decision = endpointGuard.evaluatePrivateRead(request);
        if (!decision.allowed()) {
            throw new OkxPrivateReadException(OkxPrivateReadError.PERMISSION_BLOCKED);
        }
        if (!concurrency.tryAcquire()) {
            throw new OkxPrivateReadException(OkxPrivateReadError.RATE_LIMITED);
        }
        try (OkxPrivateRequestSigner.SignedHeaders signed = signer.sign(request, credential)) {
            if (environment.simulatedTradingHeader()) {
                signed.values().put("x-simulated-trading", "1");
            }
            URI uri = GLOBAL_HOST.resolve(request.pathWithQuery());
            if (!GLOBAL_HOST.getScheme().equals(uri.getScheme())
                    || !GLOBAL_HOST.getHost().equals(uri.getHost())
                    || uri.getPort() != -1) {
                throw new OkxPrivateReadException(OkxPrivateReadError.ENVIRONMENT_MISMATCH);
            }
            OkxPrivateHttpExchange.Response response = exchange.get(uri, signed.values(), requestTimeout);
            byte[] responseBody = response.body();
            try {
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.REDIRECT_REJECTED);
                }
                if (response.statusCode() == 429) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.HTTP_RATE_LIMITED);
                }
                if (response.statusCode() == 401) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.HTTP_UNAUTHORIZED);
                }
                if (response.statusCode() == 403) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.HTTP_FORBIDDEN);
                }
                if (response.statusCode() >= 500 && response.statusCode() <= 599) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.HTTP_SERVER_ERROR);
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.HTTP_UNEXPECTED_STATUS);
                }
                if (responseBody == null) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.MALFORMED_RESPONSE);
                }
                if (responseBody.length > MAX_RESPONSE_BYTES) {
                    throw new OkxPrivateReadException(OkxPrivateReadError.RESPONSE_TOO_LARGE);
                }
                return parse(request, responseBody);
            } finally {
                if (responseBody != null) {
                    java.util.Arrays.fill(responseBody, (byte) 0);
                }
            }
        } catch (HttpTimeoutException ex) {
            throw new OkxPrivateReadException(OkxPrivateReadError.NETWORK_TIMEOUT, ex);
        } catch (OkxPrivateReadException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OkxPrivateReadException(OkxPrivateReadError.NETWORK_IO_ERROR, ex);
        } catch (IOException ex) {
            if (hasCause(ex, ResponseTooLargeIOException.class)) {
                throw new OkxPrivateReadException(OkxPrivateReadError.RESPONSE_TOO_LARGE, ex);
            }
            throw new OkxPrivateReadException(OkxPrivateReadError.NETWORK_IO_ERROR, ex);
        } finally {
            concurrency.release();
        }
    }

    private OkxPrivateReadResult parse(OkxPrivateReadRequest request, byte[] payload) {
        try {
            OkxPrivateReadOperation operation = request.operation();
            JsonNode root = objectMapper.readTree(payload);
            String providerCode = text(root, "code");
            if (!"0".equals(providerCode)) {
                throw new OkxPrivateReadException(providerError(providerCode));
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return result(operation, Set.of(), 0, false, List.of(), List.of());
            }
            if (request.reconciliationOperation()) {
                return parseReconciliation(request, data);
            }
            if (data.isEmpty()) {
                return result(operation, Set.of(), 0, false, List.of(), List.of());
            }
            // 两个 account schema 均预期单条；多条一律 fail-closed，避免忽略后续权限或资产数据。
            if (data.size() != 1) {
                return result(operation, Set.of(), 0, false, List.of(), List.of());
            }
            if (operation == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ) {
                return parseConfiguration(data.get(0), request);
            }
            return parseBalance(data.get(0), operation);
        } catch (OkxPrivateReadException ex) {
            throw ex;
        } catch (Exception ex) {
            // Jackson cause 可能携带 raw provider body 片段，只向上传递固定内部分类。
            throw new OkxPrivateReadException(OkxPrivateReadError.RESPONSE_PARSE_FAILED);
        }
    }

    private OkxPrivateReadResult parseConfiguration(JsonNode row, OkxPrivateReadRequest request) {
        OkxPrivateReadOperation operation = request.operation();
        String permissionText = text(row, "perm");
        if (permissionText == null || permissionText.isBlank()) {
            return new OkxPrivateReadResult(operation, Set.of(), 0, false);
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (String token : permissionText.split(",")) {
            String normalized = token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (!normalized.isBlank()) {
                permissions.add(normalized);
            }
        }
        // OKX 以空字符串表示当前 API key 未绑定 IP。这里只保留布尔事实，绝不把 allowlist 内容带出 transport。
        String ipAllowlist = text(row, "ip");
        boolean ipAllowlistConfigured = ipAllowlist != null && !ipAllowlist.isBlank();
        OkxIpAllowlistStatus ipStatus = matchExpectedIp(request.expectedIp(), ipAllowlist);
        return new OkxPrivateReadResult(
                operation,
                permissions,
                0,
                !permissions.isEmpty(),
                List.of(),
                List.of(),
                ipAllowlistConfigured,
                ipStatus,
                clock.instant()
        );
    }

    private static OkxIpAllowlistStatus matchExpectedIp(String expectedIp, String providerAllowlist) {
        if (expectedIp == null) {
            return OkxIpAllowlistStatus.NOT_CHECKED;
        }
        if (providerAllowlist == null) {
            return OkxIpAllowlistStatus.UNKNOWN;
        }
        if (providerAllowlist.isBlank()) {
            return OkxIpAllowlistStatus.MISSING;
        }
        boolean matched = false;
        for (String token : providerAllowlist.split(",", -1)) {
            if (token.isBlank()) {
                return OkxIpAllowlistStatus.UNKNOWN;
            }
            final String normalized;
            try {
                normalized = OkxIpAddressNormalizer.normalizeLiteral(token);
            } catch (IllegalArgumentException ex) {
                return OkxIpAllowlistStatus.UNKNOWN;
            }
            matched |= expectedIp.equals(normalized);
        }
        return matched ? OkxIpAllowlistStatus.MATCHED : OkxIpAllowlistStatus.MISMATCHED;
    }

    private OkxPrivateReadResult parseBalance(JsonNode row, OkxPrivateReadOperation operation) {
        JsonNode details = row.path("details");
        if (!details.isArray()) {
            return result(operation, Set.of(), 0, false, List.of(), List.of());
        }
        boolean complete = !details.isEmpty();
        int count = 0;
        for (JsonNode detail : details) {
            count++;
            complete &= validCurrency(text(detail, "ccy"))
                    && validDecimal(text(detail, "cashBal"))
                    && validDecimal(text(detail, "availBal"))
                    && validDecimal(text(detail, "frozenBal"))
                    && validTimestamp(text(detail, "uTime"));
        }
        return result(operation, Set.of(), count, complete, List.of(), List.of());
    }

    private OkxPrivateReadResult parseReconciliation(OkxPrivateReadRequest request, JsonNode data) {
        if (data.size() > request.limit()) {
            return result(request.operation(), Set.of(), 0, false, List.of(), List.of());
        }
        boolean complete = data.size() < request.limit();
        if (request.operation() == OkxPrivateReadOperation.OKX_SPOT_RECENT_FILLS_READ) {
            List<OkxPrivateFillSnapshot> fills = new ArrayList<>();
            for (JsonNode row : data) {
                OkxPrivateFillSnapshot parsed = parseFill(row, request);
                if (parsed == null) return result(request.operation(), Set.of(), 0, false, List.of(), List.of());
                fills.add(parsed);
            }
            return result(request.operation(), Set.of(), 0, complete, List.of(), fills);
        }
        List<OkxPrivateOrderSnapshot> orders = new ArrayList<>();
        for (JsonNode row : data) {
            OkxPrivateOrderSnapshot parsed = parseOrder(row, request);
            if (parsed == null) return result(request.operation(), Set.of(), 0, false, List.of(), List.of());
            orders.add(parsed);
        }
        return result(request.operation(), Set.of(), 0, complete, orders, List.of());
    }

    private OkxPrivateOrderSnapshot parseOrder(JsonNode row, OkxPrivateReadRequest request) {
        String orderId = text(row, "ordId");
        String clientOrderId = blankToNull(text(row, "clOrdId"));
        String instrumentId = text(row, "instId");
        String instrumentType = text(row, "instType");
        String side = lower(text(row, "side"));
        String orderType = lower(text(row, "ordType"));
        String state = lower(text(row, "state"));
        String rawPrice = text(row, "px");
        BigDecimal price = optionalDecimal(rawPrice);
        BigDecimal quantity = decimal(text(row, "sz"));
        BigDecimal filled = decimal(text(row, "accFillSz"));
        if (blankToNull(orderId) == null
                || !"SPOT".equals(instrumentType)
                || !request.instrumentId().equals(instrumentId)
                || !Set.of("buy", "sell").contains(side)
                || orderType == null || state == null
                || (rawPrice != null && !rawPrice.isBlank() && price == null)
                || (price != null && price.signum() < 0)
                || quantity == null || quantity.signum() < 0
                || filled == null || filled.signum() < 0) {
            return null;
        }
        return new OkxPrivateOrderSnapshot(
                orderId, clientOrderId, instrumentId, side, orderType, price, quantity, filled, state,
                clock.instant(), request.operation()
        );
    }

    private OkxPrivateFillSnapshot parseFill(JsonNode row, OkxPrivateReadRequest request) {
        String orderId = text(row, "ordId");
        String clientOrderId = blankToNull(text(row, "clOrdId"));
        String tradeId = text(row, "tradeId");
        String instrumentId = text(row, "instId");
        String instrumentType = text(row, "instType");
        BigDecimal price = decimal(text(row, "fillPx"));
        BigDecimal quantity = decimal(text(row, "fillSz"));
        Instant fillTime = epochMillis(text(row, "fillTime"));
        if (blankToNull(orderId) == null || blankToNull(tradeId) == null
                || !"SPOT".equals(instrumentType)
                || !request.instrumentId().equals(instrumentId)
                || price == null || price.signum() < 0
                || quantity == null || quantity.signum() < 0
                || fillTime == null) {
            return null;
        }
        return new OkxPrivateFillSnapshot(
                orderId, clientOrderId, tradeId, instrumentId, price, quantity, fillTime, clock.instant()
        );
    }

    private OkxPrivateReadResult result(
            OkxPrivateReadOperation operation,
            Set<String> permissions,
            int assetCount,
            boolean complete,
            List<OkxPrivateOrderSnapshot> orders,
            List<OkxPrivateFillSnapshot> fills
    ) {
        return new OkxPrivateReadResult(operation, permissions, assetCount, complete, orders, fills, clock.instant());
    }

    private static OkxPrivateReadError providerError(String code) {
        return switch (code == null ? "" : code) {
            case "50011" -> OkxPrivateReadError.HTTP_RATE_LIMITED;
            case "50105", "50111" -> OkxPrivateReadError.OKX_AUTHENTICATION_FAILED;
            case "50113" -> OkxPrivateReadError.OKX_SIGNATURE_INVALID;
            case "50102" -> OkxPrivateReadError.OKX_TIMESTAMP_INVALID;
            case "50110", "50035" -> OkxPrivateReadError.OKX_IP_NOT_ALLOWED;
            case "50120" -> OkxPrivateReadError.OKX_PERMISSION_DENIED;
            default -> OkxPrivateReadError.OKX_BUSINESS_REJECTED;
        };
    }

    private static OkxPrivateHttpExchange jdkExchange(Duration connectTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new OkxPrivateHttpExchange() {
            @Override
            public Response get(URI uri, Map<String, String> headers, Duration timeout)
                    throws IOException, InterruptedException {
                return get(uri, headers, timeout, MAX_RESPONSE_BYTES);
            }

            @Override
            public Response get(
                    URI uri,
                    Map<String, String> headers,
                    Duration timeout,
                    int maximumResponseBytes
            ) throws IOException, InterruptedException {
                return send(client, HttpRequest.newBuilder(uri).timeout(timeout).GET(), headers,
                        maximumResponseBytes);
            }

            @Override
            public Response post(
                    URI uri,
                    Map<String, String> headers,
                    byte[] body,
                    Duration timeout,
                    int maximumResponseBytes
            ) throws IOException, InterruptedException {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
                return send(client, builder, headers, maximumResponseBytes);
            }
        };
    }

    private static OkxPrivateHttpExchange.Response send(
            HttpClient client,
            HttpRequest.Builder builder,
            Map<String, String> headers,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        if (maximumResponseBytes <= 0 || maximumResponseBytes > MAX_RESPONSE_BYTES) {
            throw new OkxPrivateHttpExchange.ResponseLimitExceededIOException();
        }
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = client.send(
                builder.build(), ignored -> new LimitedBodySubscriber(maximumResponseBytes));
        return new OkxPrivateHttpExchange.Response(response.statusCode(), response.body());
    }

    private <T> T withPermit(java.util.function.Supplier<T> operation) {
        if (!concurrency.tryAcquire()) {
            throw new OkxPrivateReadException(OkxPrivateReadError.RATE_LIMITED);
        }
        try {
            return operation.get();
        } finally {
            concurrency.release();
        }
    }

    private void requireProviderContract(OkxSpotProviderOperation operation) {
        OkxSpotProviderContractDecision decision = endpointGuard.evaluateProviderContract(operation);
        if (!decision.contractAllowed() || decision.runtimeAuthorized() || decision.tradingAuthorized()) {
            throw new IllegalStateException("OKX provider operation is outside the typed contract");
        }
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

    private static Duration validateDuration(Duration value, Duration max, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative() || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(field + " exceeds the security boundary");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean validCurrency(String value) {
        return value != null && CURRENCY.matcher(value).matches();
    }

    private static boolean validDecimal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean validTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal optionalDecimal(String value) {
        return value == null || value.isBlank() ? null : decimal(value);
    }

    private static Instant epochMillis(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 接收过程中即执行 byte cap；超限后取消 subscription，避免先无界缓冲再检查。
     */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final byte[] buffer;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        LimitedBodySubscriber(int limit) {
            this.limit = limit;
            this.buffer = new byte[limit];
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (ByteBuffer buffer : buffers) {
                    int size = buffer.remaining();
                    if (received > limit - size) {
                        subscription.cancel();
                        java.util.Arrays.fill(this.buffer, (byte) 0);
                        body.completeExceptionally(new ResponseTooLargeIOException());
                        return;
                    }
                    int offset = received;
                    received += size;
                    buffer.get(this.buffer, offset, size);
                }
                subscription.request(1);
            } catch (RuntimeException ex) {
                subscription.cancel();
                java.util.Arrays.fill(buffer, (byte) 0);
                body.completeExceptionally(ex);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            java.util.Arrays.fill(buffer, (byte) 0);
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            byte[] result = java.util.Arrays.copyOf(buffer, received);
            java.util.Arrays.fill(buffer, (byte) 0);
            body.complete(result);
        }
    }

    static final class ResponseTooLargeIOException extends IOException {
        private ResponseTooLargeIOException() {
            super("response exceeded configured byte limit");
        }
    }
}
