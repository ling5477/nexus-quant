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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OkxInstrumentsCache 负责缓存 `public/instruments?instType=SPOT`。
 * <p>
 * Why:
 * GateC-1 要求下单前必须使用 instruments 做 trim，但测试和 Spring context 启动必须保持 no-outbound。
 * 因此 cache 构造期只建立本地状态，首次真正读取 symbol metadata 时再刷新，避免 Bean bootstrap 访问 OKX public endpoint。
 */
public class OkxInstrumentsCache {

    private static final Logger log = LoggerFactory.getLogger(OkxInstrumentsCache.class);
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
            // Why:
            // OKX 真实盘 instruments 列表会包含 preopen 等非可交易条目，这些条目可能缺少 tickSz/lotSz/minSz。
            // 启动阶段若因为单个脏条目 fail-fast，会阻断整个恢复与验收链路；因此这里降级为“跳过不可交易条目并记录审计日志”。
            String instId = item.path("instId").asText();
            String state = item.path("state").asText();
            if (isBlank(instId) || isBlank(state) || !"live".equalsIgnoreCase(state)) {
                continue;
            }
            BigDecimal tick = decimalOrNull(item, "tickSz");
            BigDecimal lot = decimalOrNull(item, "lotSz");
            BigDecimal min = decimalOrNull(item, "minSz");
            if (tick == null || lot == null || min == null) {
                log.warn(
                        "okx_instrument_skipped_missing_precision trace_id={} inst_id={} state={} tickSz={} lotSz={} minSz={}",
                        traceId,
                        instId,
                        state,
                        item.path("tickSz").asText(),
                        item.path("lotSz").asText(),
                        item.path("minSz").asText()
                );
                continue;
            }
            OkxInstrument instrument = new OkxInstrument(instId, tick, lot, min, state);
            refreshed.put(instId, instrument);
        }
        if (refreshed.isEmpty()) {
            throw new IllegalStateException("OKX instruments cache refresh produced zero live entries");
        }
        instrumentsById.clear();
        instrumentsById.putAll(refreshed);
        lastRefreshAt = Instant.now(clock);
    }

    private void refreshIfDue(String traceId) {
        if (!instrumentsById.isEmpty() && lastRefreshAt.plus(refreshInterval).isAfter(Instant.now(clock))) {
            return;
        }
        refreshNow(traceId);
    }

    private BigDecimal decimalOrNull(JsonNode item, String field) {
        String raw = item.path(field).asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
