package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleChecksumCalculator;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status;

/** 人工账户观察中的当前公开规则；固定无凭证 GET，不读写 catalog，也不表示历史回放窗口的规则。 */
public final class OkxCurrentPublicRuleReader implements AutoCloseable {
    private static final URI INSTRUMENT_URI = URI.create(
            "https://openapi.okx.com/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT");
    private static final String INSTRUMENT = "BTC-USDT";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RULE_TTL = Duration.ofHours(24);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final List<String> DECIMAL_FIELDS = List.of(
            "tickSz", "lotSz", "minSz", "maxLmtSz", "maxMktSz", "maxLmtAmt", "maxMktAmt");

    private final HttpClient client;
    private final Clock clock;
    private final Semaphore concurrency = new Semaphore(1);
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final VenueRuleChecksumCalculator checksumCalculator = new VenueRuleChecksumCalculator();

    /** 构造只创建连接客户端；网络只由显式 observe 调用触发，不自动重试或跟随重定向。 */
    public OkxCurrentPublicRuleReader(Clock clock) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), clock);
    }

    OkxCurrentPublicRuleReader(HttpClient client, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 单次请求取得完整当前规则；并发、超时、部分响应和规则不完整均返回 UNKNOWN。 */
    public AccountFactsSnapshot.Fact<String> observe() {
        Instant started = clock.instant();
        if (!concurrency.tryAcquire()) {
            return unknown(started, "PUBLIC_RULE_REQUEST_IN_PROGRESS");
        }
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(INSTRUMENT_URI).GET()
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").build();
            pending = client.sendAsync(request, ignored -> new LimitedBodySubscriber());
            HttpResponse<byte[]> response = pending.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return unknown(started, "PUBLIC_RULE_HTTP_FAILED");
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) {
                return unknown(started, "PUBLIC_RULE_RESPONSE_INVALID");
            }
            JsonNode payload = mapper.readTree(bytes);
            validateCompleteResponse(payload);
            Instant observedAt = clock.instant();
            var snapshot = OkxVenueRuleFactsReader.parseSnapshot(payload, Set.of(INSTRUMENT), observedAt);
            var item = canonicalItem(snapshot.facts().getFirst(), observedAt);
            String checksum = checksumCalculator.calculate(item);
            if (!checksum.matches("[0-9a-f]{64}")) {
                return unknown(observedAt, "PUBLIC_RULE_CHECKSUM_INVALID");
            }
            Instant now = clock.instant();
            Instant expiresAt = observedAt.plus(RULE_TTL);
            if (observedAt.isBefore(started) || observedAt.isAfter(now) || now.isAfter(expiresAt)) {
                return new AccountFactsSnapshot.Fact<>(Status.STALE, null, observedAt, expiresAt,
                        OkxVenueRuleContract.SOURCE, "PUBLIC_RULE_OBSERVATION_STALE");
            }
            return new AccountFactsSnapshot.Fact<>(Status.OBSERVED, "OKX:" + INSTRUMENT + ":" + checksum,
                    observedAt, expiresAt, OkxVenueRuleContract.SOURCE, "CURRENTLY_OBSERVED_PUBLIC_RULE");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unknown(started, "PUBLIC_RULE_READ_INTERRUPTED");
        } catch (TimeoutException ex) {
            return unknown(started, "PUBLIC_RULE_READ_TIMEOUT");
        } catch (ExecutionException ex) {
            return unknown(started, "PUBLIC_RULE_READ_FAILED");
        } catch (IOException | RuntimeException ex) {
            // 只输出稳定原因，响应正文和底层异常不进入账户事实或日志。
            return unknown(started, "PUBLIC_RULE_RESPONSE_INVALID");
        } finally {
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
            concurrency.release();
        }
    }

    private static void validateCompleteResponse(JsonNode payload) {
        if (payload == null || !payload.isObject() || !payload.path("code").isTextual()
                || !"0".equals(payload.path("code").textValue())) {
            throw new IllegalArgumentException("public instrument response is not successful");
        }
        JsonNode data = payload.path("data");
        if (!data.isArray() || data.size() != 1) {
            throw new IllegalArgumentException("public instrument response must contain one exact rule");
        }
        JsonNode rule = data.get(0);
        requireText(rule, "instId", INSTRUMENT);
        requireText(rule, "instType", "SPOT");
        requireText(rule, "baseCcy", "BTC");
        requireText(rule, "quoteCcy", "USDT");
        requireText(rule, "state", "live");
        for (String field : DECIMAL_FIELDS) {
            JsonNode value = rule.path(field);
            if (!value.isTextual() || value.textValue().length() > 64 || value.textValue().isBlank()) {
                throw new IllegalArgumentException("public instrument decimal is missing");
            }
            BigDecimal decimal = new BigDecimal(value.textValue());
            // 小数指数也受限，避免 canonical decimal 展开造成无界分配。
            if (decimal.signum() <= 0 || decimal.precision() > 38
                    || decimal.scale() < -18 || decimal.scale() > 18) {
                throw new IllegalArgumentException("public instrument decimal is outside the bounded contract");
            }
        }
    }

    private static void requireText(JsonNode rule, String field, String expected) {
        JsonNode value = rule.path(field);
        if (!value.isTextual() || !expected.equals(value.textValue())) {
            throw new IllegalArgumentException("public instrument identity or state is outside the exact scope");
        }
    }

    private static InstrumentCatalogItem canonicalItem(OkxVenueRuleFact fact, Instant observedAt) {
        // 仅复用既有 checksum 输入模型，不形成 catalog 持久化或第二套规则事实源。
        return new InstrumentCatalogItem(null, "OKX", fact.instType(), fact.instId(), fact.instId(),
                fact.baseCurrency(), fact.quoteCurrency(), fact.state(), fact.tickSize(), fact.lotSize(),
                fact.minimumSize(), fact.maximumLimitSize(), fact.maximumMarketSize(),
                fact.maximumMarketSizeUnit(), fact.maximumLimitAmountUsd(), fact.maximumMarketAmountUsd(),
                OkxVenueRuleContract.SOURCE, OkxVenueRuleContract.SOURCE_SCHEMA_VERSION, observedAt, null,
                fact.nextRuleEffectiveAt(), null, null, null);
    }

    private static AccountFactsSnapshot.Fact<String> unknown(Instant at, String reason) {
        return new AccountFactsSnapshot.Fact<>(Status.UNKNOWN, null, at, null, OkxVenueRuleContract.SOURCE, reason);
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    /** 收取过程中限制字节并取消超限订阅，不先无界缓冲再做长度检查。 */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final byte[] buffer = new byte[MAX_RESPONSE_BYTES];
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

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
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer value : buffers) {
                int size = value.remaining();
                if (received > MAX_RESPONSE_BYTES - size) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("public rule response exceeds byte limit"));
                    return;
                }
                value.get(buffer, received, size);
                received += size;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(Arrays.copyOf(buffer, received));
        }
    }
}
