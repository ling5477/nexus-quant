package com.guidinglight.nexusquant.adapter.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineBar;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.adapter.api.service.HistoricalKlineAdapterException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * BinanceHistoricalKlineAdapter 负责 Binance SPOT historical klines 的协议适配。
 * <p>
 * Why:
 * GateH-2 只允许 Binance adapter 处理 Binance symbol/interval 映射和 payload 转换，不能把 `BTCUSDT`
 * 这类交易所私有符号泄漏到 controller、core service 或数据库幂等键。
 */
@Component
public class BinanceHistoricalKlineAdapter implements HistoricalKlineAdapter {

    private static final Map<String, String> SYMBOLS = Map.of(
            "BTC-USDT", "BTCUSDT",
            "ETH-USDT", "ETHUSDT",
            "SOL-USDT", "SOLUSDT"
    );
    private static final Map<String, String> INTERVALS = Map.of(
            "1m", "1m",
            "5m", "5m",
            "15m", "15m",
            "1h", "1h",
            "4h", "4h",
            "1d", "1d"
    );

    private final BinanceHttpClient client;

    public BinanceHistoricalKlineAdapter() {
        this(new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                BinanceRuntimeConfig.fromSystemEnv().baseUrl(),
                BinanceRuntimeConfig.fromSystemEnv().timeout(),
                new BinanceRequestSigner(),
                System::currentTimeMillis,
                null
        ));
    }

    BinanceHistoricalKlineAdapter(BinanceHttpClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String exchangeCode() {
        return "BINANCE";
    }

    @Override
    public List<HistoricalKlineBar> fetchHistoricalKlines(HistoricalKlineRequest request) {
        requireSpot(request);
        String exchangeSymbol = mapSymbol(request.symbol());
        String exchangeInterval = mapInterval(request.interval());
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", exchangeSymbol);
        params.put("interval", exchangeInterval);
        params.put("startTime", request.startTime().toEpochMilli());
        params.put("endTime", request.endTime().toEpochMilli());
        params.put("limit", normalizeLimit(request.limit()));
        try {
            JsonNode payload = client.get("/api/v3/klines", params, false, null);
            if (!payload.isArray()) {
                throw new HistoricalKlineAdapterException("Binance klines response is not an array");
            }
            return parseBars(request, payload);
        } catch (RuntimeException ex) {
            if (ex instanceof HistoricalKlineAdapterException adapterException) {
                throw adapterException;
            }
            throw new HistoricalKlineAdapterException("Binance historical klines request failed: " + ex.getMessage(), ex);
        }
    }

    private List<HistoricalKlineBar> parseBars(HistoricalKlineRequest request, JsonNode payload) {
        return java.util.stream.StreamSupport.stream(payload.spliterator(), false)
                .map(row -> parseBar(request, row))
                .toList();
    }

    private HistoricalKlineBar parseBar(HistoricalKlineRequest request, JsonNode row) {
        if (!row.isArray() || row.size() < 9) {
            throw new HistoricalKlineAdapterException("Binance kline row has unexpected shape");
        }
        return new HistoricalKlineBar(
                "BINANCE",
                "SPOT",
                request.symbol(),
                request.interval(),
                Instant.ofEpochMilli(row.get(0).asLong()),
                Instant.ofEpochMilli(row.get(6).asLong()),
                new BigDecimal(row.get(1).asText()),
                new BigDecimal(row.get(2).asText()),
                new BigDecimal(row.get(3).asText()),
                new BigDecimal(row.get(4).asText()),
                new BigDecimal(row.get(5).asText()),
                new BigDecimal(row.get(7).asText()),
                row.get(8).asLong(),
                row.toString()
        );
    }

    private void requireSpot(HistoricalKlineRequest request) {
        if (!"SPOT".equalsIgnoreCase(request.marketType())) {
            throw new HistoricalKlineAdapterException("Binance GateH-2 adapter only supports SPOT");
        }
    }

    private String mapSymbol(String symbol) {
        String mapped = SYMBOLS.get(symbol == null ? "" : symbol.toUpperCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException("unsupported Binance GateH-2 symbol: " + symbol);
        }
        return mapped;
    }

    private String mapInterval(String interval) {
        String mapped = INTERVALS.get(interval == null ? "" : interval.toLowerCase(Locale.ROOT));
        if (mapped == null) {
            throw new HistoricalKlineAdapterException("unsupported Binance GateH-2 interval: " + interval);
        }
        return mapped;
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 500 : limit, 1_000));
    }
}
