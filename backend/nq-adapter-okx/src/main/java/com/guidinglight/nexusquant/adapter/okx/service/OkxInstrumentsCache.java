package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OkxInstrumentsCache 负责缓存 `public/instruments?instType=SPOT`。
 * <p>
 * Why:
 * GateC-1 要求下单前必须使用 instruments 做 trim，且刷新策略至少做到“启动拉取 + 周期刷新”。
 * 这里采用构造时预热 + 按刷新窗口惰性刷新，避免先引入额外调度线程也能满足最小可用要求。
 */
public class OkxInstrumentsCache {

    private static final String INSTRUMENTS_ENDPOINT = "/api/v5/public/instruments?instType=SPOT";

    private final OkxHttpClient publicHttpClient;
    private final Clock clock;
    private final Duration refreshInterval;
    private final Map<String, OkxInstrument> instrumentsById;
    private volatile Instant lastRefreshAt;

    /**
     * @param publicHttpClient 仅用于无鉴权 public 请求的 HTTP client
     * @param clock            时钟，便于测试刷新逻辑
     * @param refreshInterval  刷新间隔
     */
    public OkxInstrumentsCache(OkxHttpClient publicHttpClient, Clock clock, Duration refreshInterval) {
        this.publicHttpClient = Objects.requireNonNull(publicHttpClient, "publicHttpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
        this.instrumentsById = new ConcurrentHashMap<>();
        this.lastRefreshAt = Instant.EPOCH;
        refreshNow("bootstrap-okx-instruments");
    }

    /**
     * 获取指定 instId 的最新元数据；到达刷新窗口会自动刷新。
     *
     * @param instId  OKX instId
     * @param traceId 调用链 traceId
     * @return 命中的 instrument
     */
    public OkxInstrument getRequired(String instId, String traceId) {
        refreshIfDue(traceId);
        OkxInstrument instrument = instrumentsById.get(instId);
        if (instrument == null) {
            throw new OkxApiException(
                    "OKX instrument not found, instId=" + instId + ", trace_id=" + traceId,
                    0,
                    INSTRUMENTS_ENDPOINT,
                    "OKX_INSTRUMENT_NOT_FOUND",
                    traceId
            );
        }
        return instrument;
    }

    /**
     * 导出只读快照供测试或诊断使用。
     */
    public Map<String, OkxInstrument> snapshot(String traceId) {
        refreshIfDue(traceId);
        return Collections.unmodifiableMap(instrumentsById);
    }

    /**
     * 立即刷新缓存。
     */
    public synchronized void refreshNow(String traceId) {
        JsonNode payload = publicHttpClient.get(INSTRUMENTS_ENDPOINT, traceId);
        if (!"0".equals(payload.path("code").asText("0"))) {
            throw new OkxApiException(
                    "OKX instruments fetch failed, trace_id=" + traceId + ", code=" + payload.path("code").asText(),
                    200,
                    INSTRUMENTS_ENDPOINT,
                    payload.path("code").asText(),
                    traceId
            );
        }
        Map<String, OkxInstrument> refreshed = new ConcurrentHashMap<>();
        for (JsonNode item : payload.path("data")) {
            OkxInstrument instrument = new OkxInstrument(
                    item.path("instId").asText(),
                    decimal(item, "tickSz"),
                    decimal(item, "lotSz"),
                    decimal(item, "minSz"),
                    item.path("state").asText()
            );
            refreshed.put(instrument.instId(), instrument);
        }
        instrumentsById.clear();
        instrumentsById.putAll(refreshed);
        lastRefreshAt = Instant.now(clock);
    }

    private void refreshIfDue(String traceId) {
        if (lastRefreshAt.plus(refreshInterval).isAfter(Instant.now(clock))) {
            return;
        }
        refreshNow(traceId);
    }

    private BigDecimal decimal(JsonNode item, String field) {
        String raw = item.path(field).asText();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("OKX instruments missing field: " + field);
        }
        return new BigDecimal(raw);
    }
}
