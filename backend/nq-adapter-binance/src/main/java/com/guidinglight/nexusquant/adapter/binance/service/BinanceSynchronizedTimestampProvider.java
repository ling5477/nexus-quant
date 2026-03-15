package com.guidinglight.nexusquant.adapter.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BinanceSynchronizedTimestampProvider 在本地时钟基础上叠加人工偏移与 serverTime 校准。
 * <p>
 * Why:
 * UC-D10 实样本表明，Binance dome 会因为宿主机与服务端的轻微时钟漂移返回 `-1021`，
 * 但这类问题发生在签名层，不应该把 place/cancel 语义改造成“先失败再盲重试”。
 * 这里在发送签名请求前，通过公开的 `/api/v3/time` 同步一次服务端时间并缓存偏移，
 * 让业务请求第一次进入 Binance 时就带着更接近事实的签名时间戳。
 */
public final class BinanceSynchronizedTimestampProvider implements BinanceTimestampProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceSynchronizedTimestampProvider.class);
    private static final String SERVER_TIME_ENDPOINT = "/api/v3/time";
    private static final long DEFAULT_RESYNC_INTERVAL_MS = 30_000L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final Clock clock;
    private final long manualOffsetMs;
    private final long resyncIntervalMs;
    private final AtomicLong serverOffsetMs;
    private final AtomicLong lastSyncEpochMs;

    public BinanceSynchronizedTimestampProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            Clock clock,
            long manualOffsetMs
    ) {
        this(httpClient, objectMapper, baseUrl, timeout, clock, manualOffsetMs, DEFAULT_RESYNC_INTERVAL_MS);
    }

    BinanceSynchronizedTimestampProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            Clock clock,
            long manualOffsetMs,
            long resyncIntervalMs
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.manualOffsetMs = manualOffsetMs;
        this.resyncIntervalMs = Math.max(0L, resyncIntervalMs);
        this.serverOffsetMs = new AtomicLong(0L);
        this.lastSyncEpochMs = new AtomicLong(Long.MIN_VALUE);
    }

    /**
     * Why:
     * Binance 要求签名时间与 serverTime 足够接近。
     * 这里先做一次按需校时，再返回“本地时间 + 人工偏移 + 服务端偏移”的最终签名时间戳。
     */
    @Override
    public long nowMillis() {
        long localNow = clock.millis();
        refreshOffsetIfStale(localNow);
        return localNow + manualOffsetMs + serverOffsetMs.get();
    }

    long effectiveOffsetMillis() {
        long localNow = clock.millis();
        refreshOffsetIfStale(localNow);
        return manualOffsetMs + serverOffsetMs.get();
    }

    private void refreshOffsetIfStale(long localNow) {
        long lastSync = lastSyncEpochMs.get();
        if (lastSync != Long.MIN_VALUE && localNow - lastSync < resyncIntervalMs) {
            return;
        }
        synchronized (this) {
            lastSync = lastSyncEpochMs.get();
            if (lastSync != Long.MIN_VALUE && localNow - lastSync < resyncIntervalMs) {
                return;
            }
            try {
                syncOffset(localNow);
            } catch (Exception ex) {
                lastSyncEpochMs.set(localNow);
                log.warn(
                        "binance_signed_timestamp_sync_failed base_url={} reason={} manual_offset_ms={}",
                        baseUrl,
                        ex.getMessage(),
                        manualOffsetMs
                );
            }
        }
    }

    private void syncOffset(long localNow) throws IOException, InterruptedException {
        long requestStartMs = clock.millis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + SERVER_TIME_ENDPOINT))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long responseEndMs = clock.millis();
        if (response.statusCode() >= 400) {
            throw new IOException("status=" + response.statusCode());
        }
        JsonNode payload = objectMapper.readTree(response.body());
        if (!payload.hasNonNull("serverTime")) {
            throw new IOException("serverTime missing");
        }
        long serverTime = payload.path("serverTime").asLong();
        long midpointMs = requestStartMs + Math.max(0L, (responseEndMs - requestStartMs) / 2L);
        long resolvedOffsetMs = serverTime - midpointMs;
        serverOffsetMs.set(resolvedOffsetMs);
        lastSyncEpochMs.set(localNow);
        log.info(
                "binance_signed_timestamp_synced base_url={} manual_offset_ms={} server_offset_ms={} effective_offset_ms={} server_time={}",
                baseUrl,
                manualOffsetMs,
                resolvedOffsetMs,
                manualOffsetMs + resolvedOffsetMs,
                serverTime
        );
    }
}
