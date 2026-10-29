package com.guidinglight.nexusquant.adapter.okx.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineBar;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.error.HistoricalKlineAdapterException;
import com.guidinglight.nexusquant.adapter.api.service.port.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.error.OkxApiException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * OkxHistoricalKlineAdapter 负责 OKX SPOT history-candles 的协议适配。
 * <p>
 * Why:
 * OKX 使用系统同形的 `BTC-USDT` symbol，但 interval 命名和响应数组顺序仍然是交易所私有协议；
 * 这些差异必须被隔离在 adapter 内，不能污染平台的 marketdata domain。
 * 真实 HTTP client 只在已有的手动 public outbound capability 显式启用时构造；
 * 默认运行与私有只读诊断不应因组件扫描获得公开行情外联能力。
 */
@Component
@Profile("public-marketdata-manual")
@ConditionalOnProperty(
        prefix = "nq.public-marketdata.outbound",
        name = "enabled",
        havingValue = "true")
public class OkxHistoricalKlineAdapter implements HistoricalKlineAdapter {

    private static final Map<String, String> SYMBOLS =
            Map.of(
                    "BTC-USDT", "BTC-USDT",
                    "ETH-USDT", "ETH-USDT",
                    "SOL-USDT", "SOL-USDT");
    private static final Map<String, String> INTERVALS =
            Map.of(
                    "1m", "1m",
                    "5m", "5m",
                    "15m", "15m",
                    "1h", "1H",
                    "4h", "4H",
                    "1d", "1D");
    private static final Map<String, Duration> DURATIONS =
            Map.of(
                    "1m", Duration.ofMinutes(1),
                    "5m", Duration.ofMinutes(5),
                    "15m", Duration.ofMinutes(15),
                    "1h", Duration.ofHours(1),
                    "4h", Duration.ofHours(4),
                    "1d", Duration.ofDays(1));

    private final OkxHttpClient client;
    private long nextRequestNanos;

    @Autowired
    public OkxHistoricalKlineAdapter(
            @Value(
                            "${nq.public-marketdata.outbound.base-url:${NQ_PUBLIC_MARKETDATA_BASE_URL:http://127.0.0.1:0}}")
                    String baseUrl,
            @Value("${nq.public-marketdata.outbound.total-request-timeout:PT8S}")
                    Duration timeout) {
        this(new OkxHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper(), baseUrl, timeout));
    }

    OkxHistoricalKlineAdapter(OkxHttpClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String exchangeCode() {
        return "OKX";
    }

    @Override
    public List<HistoricalKlineBar> fetchHistoricalKlines(HistoricalKlineRequest request) {
        requireSpot(request);
        String path =
                "/api/v5/market/history-candles"
                        + "?instId="
                        + encode(mapSymbol(request.symbol()))
                        + "&bar="
                        + encode(mapInterval(request.interval()))
                        + "&after="
                        + request.endTime().toEpochMilli()
                        // 供应方游标为排他，NQ范围包含起始bar；否则最早一根会被跳过。
                        + "&before="
                        + Math.subtractExact(request.startTime().toEpochMilli(), 1)
                        + "&limit="
                        + normalizeLimit(request.limit());
        try {
            JsonNode payload = readPage(path);
            if (!"0".equals(payload.path("code").asText())) {
                throw new HistoricalKlineAdapterException(
                        "OKX history-candles failed, code=" + payload.path("code").asText());
            }
            JsonNode data = payload.path("data");
            if (!data.isArray()) {
                throw new HistoricalKlineAdapterException(
                        "OKX history-candles data is not an array");
            }
            return StreamSupport.stream(data.spliterator(), false)
                    .map(row -> parseBar(request, row))
                    .toList();
        } catch (RuntimeException ex) {
            if (ex instanceof HistoricalKlineAdapterException adapterException) {
                throw adapterException;
            }
            throw new HistoricalKlineAdapterException(
                    "OKX historical candles request failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode readPage(String path) {
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle();
            try {
                JsonNode payload = client.getBounded(path, null, 262144);
                if ("50011".equals(payload.path("code").asText())) {
                    if (attempt == 2) throw new HistoricalKlineAdapterException("OKX_HISTORY_RATE_LIMITED");
                    pause(250L * (attempt + 1));
                    continue;
                }
                return payload;
            } catch (OkxApiException failure) {
                if (Thread.currentThread().isInterrupted())
                    throw new HistoricalKlineAdapterException("OKX_HISTORY_INTERRUPTED", failure);
                boolean retry = failure.httpStatus() == 429 || failure.httpStatus() >= 500
                        || (failure.httpStatus() == 0 && failure.getCause() instanceof java.io.IOException
                                && !"PUBLIC_RESPONSE_LIMIT_EXCEEDED".equals(failure.getCause().getMessage()));
                if (!retry || attempt == 2) throw failure;
                pause(250L * (attempt + 1));
            }
        }
        throw new HistoricalKlineAdapterException("OKX_HISTORY_RETRY_EXHAUSTED");
    }

    /** 当前进程的公开历史读取低于供应方每IP每两秒20次的上限，不并发突发抢占额度。 */
    private synchronized void throttle() {
        long remaining = nextRequestNanos - System.nanoTime();
        if (remaining > 0) pause(Math.max(1, (remaining + 999999L) / 1000000L));
        nextRequestNanos = System.nanoTime() + Duration.ofMillis(125).toNanos();
    }

    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new HistoricalKlineAdapterException("OKX_HISTORY_INTERRUPTED", failure);
        }
    }

    private HistoricalKlineBar parseBar(HistoricalKlineRequest request, JsonNode row) {
        if (!row.isArray() || row.size() < 9 || !"1".equals(row.get(8).asText())) {
            throw new HistoricalKlineAdapterException("OKX candle row is unclosed or has unexpected shape");
        }
        Instant openTime = Instant.ofEpochMilli(row.get(0).asLong());
        return new HistoricalKlineBar(
                "OKX",
                "SPOT",
                request.symbol(),
                request.interval(),
                openTime,
                openTime.plus(DURATIONS.get(request.interval())).minusMillis(1),
                new BigDecimal(row.get(1).asText()),
                new BigDecimal(row.get(2).asText()),
                new BigDecimal(row.get(3).asText()),
                new BigDecimal(row.get(4).asText()),
                new BigDecimal(row.get(5).asText()),
                row.size() > 7 ? new BigDecimal(row.get(7).asText()) : null,
                null,
                row.toString());
    }

    private void requireSpot(HistoricalKlineRequest request) {
        if (!"SPOT".equalsIgnoreCase(request.marketType())) {
            throw new HistoricalKlineAdapterException(
                    "OKX historical kline adapter only supports SPOT");
        }
    }

    private String mapSymbol(String symbol) {
        String mapped = SYMBOLS.get(symbol == null ? "" : symbol.toUpperCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException(
                    "unsupported OKX historical kline symbol: " + symbol);
        }
        return mapped;
    }

    private String mapInterval(String interval) {
        String mapped = INTERVALS.get(interval == null ? "" : interval.toLowerCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException(
                    "unsupported OKX historical kline interval: " + interval);
        }
        return mapped;
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 300));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
