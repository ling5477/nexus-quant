package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineBar;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.adapter.api.service.HistoricalKlineAdapterException;

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

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/**
 * OkxHistoricalKlineAdapter 负责 OKX SPOT history-candles 的协议适配。
 * <p>
 * Why:
 * OKX 使用系统同形的 `BTC-USDT` symbol，但 interval 命名和响应数组顺序仍然是交易所私有协议；
 * 这些差异必须被隔离在 adapter 内，不能污染平台的 marketdata domain。
 */
@Component
@Profile("!gatew")
public class OkxHistoricalKlineAdapter implements HistoricalKlineAdapter {

    private static final Map<String, String> SYMBOLS = Map.of(
            "BTC-USDT", "BTC-USDT",
            "ETH-USDT", "ETH-USDT",
            "SOL-USDT", "SOL-USDT"
    );
    private static final Map<String, String> INTERVALS = Map.of(
            "1m", "1m",
            "5m", "5m",
            "15m", "15m",
            "1h", "1H",
            "4h", "4H",
            "1d", "1D"
    );
    private static final Map<String, Duration> DURATIONS = Map.of(
            "1m", Duration.ofMinutes(1),
            "5m", Duration.ofMinutes(5),
            "15m", Duration.ofMinutes(15),
            "1h", Duration.ofHours(1),
            "4h", Duration.ofHours(4),
            "1d", Duration.ofDays(1)
    );

    private final OkxHttpClient client;

    public OkxHistoricalKlineAdapter() {
        this(new OkxHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                OkxRuntimeConfig.fromSystemEnv().baseUrl(),
                OkxRuntimeConfig.fromSystemEnv().timeout(),
                new OkxRequestSigner(),
                () -> Instant.now().toString(),
                null,
                false
        ));
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
        String path = "/api/v5/market/history-candles"
                + "?instId=" + encode(mapSymbol(request.symbol()))
                + "&bar=" + encode(mapInterval(request.interval()))
                + "&after=" + request.endTime().toEpochMilli()
                + "&before=" + request.startTime().toEpochMilli()
                + "&limit=" + normalizeLimit(request.limit());
        try {
            JsonNode payload = client.get(path, null);
            if (!"0".equals(payload.path("code").asText())) {
                throw new HistoricalKlineAdapterException("OKX history-candles failed, code=" + payload.path("code").asText());
            }
            JsonNode data = payload.path("data");
            if (!data.isArray()) {
                throw new HistoricalKlineAdapterException("OKX history-candles data is not an array");
            }
            return java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .map(row -> parseBar(request, row))
                    .toList();
        } catch (RuntimeException ex) {
            if (ex instanceof HistoricalKlineAdapterException adapterException) {
                throw adapterException;
            }
            throw new HistoricalKlineAdapterException("OKX historical candles request failed: " + ex.getMessage(), ex);
        }
    }

    private HistoricalKlineBar parseBar(HistoricalKlineRequest request, JsonNode row) {
        if (!row.isArray() || row.size() < 7) {
            throw new HistoricalKlineAdapterException("OKX candle row has unexpected shape");
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
                row.toString()
        );
    }

    private void requireSpot(HistoricalKlineRequest request) {
        if (!"SPOT".equalsIgnoreCase(request.marketType())) {
            throw new HistoricalKlineAdapterException("OKX GateH-2 adapter only supports SPOT");
        }
    }

    private String mapSymbol(String symbol) {
        String mapped = SYMBOLS.get(symbol == null ? "" : symbol.toUpperCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException("unsupported OKX GateH-2 symbol: " + symbol);
        }
        return mapped;
    }

    private String mapInterval(String interval) {
        String mapped = INTERVALS.get(interval == null ? "" : interval.toLowerCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException("unsupported OKX GateH-2 interval: " + interval);
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
