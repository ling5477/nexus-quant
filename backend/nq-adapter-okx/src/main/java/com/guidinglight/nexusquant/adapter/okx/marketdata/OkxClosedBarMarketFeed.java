package com.guidinglight.nexusquant.adapter.okx.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 固定 OKX 公开端点的有界轮询；只有受控连续 SIM profile 显式开启时装配。 */
@Component
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.public-marketdata.outbound", name = "enabled", havingValue = "true")
public final class OkxClosedBarMarketFeed implements ClosedBarMarketFeed {
    private static final int MAX_RESPONSE_BYTES = 128_000;
    private static final int MAX_CATCH_UP_BARS = 168;
    private static final URI OKX_ORIGIN = URI.create("https://www.okx.com");
    private static final String TIME_PATH = "/api/v5/public/time";
    private static final String RULE_PATH = "/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT";
    private static final String TICKER_PATH = "/api/v5/market/ticker?instId=BTC-USDT";
    private final HttpClient http;
    private final URI origin;
    private final ObjectMapper mapper;

    public OkxClosedBarMarketFeed() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                OKX_ORIGIN, new ObjectMapper());
    }

    OkxClosedBarMarketFeed(HttpClient http, URI origin, ObjectMapper mapper) {
        this.http = Objects.requireNonNull(http);
        this.origin = Objects.requireNonNull(origin);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Observation observe(Instant firstOpen, int maximumBars) {
        if (firstOpen == null || firstOpen.toEpochMilli() % Duration.ofHours(1).toMillis() != 0
                || maximumBars < 1 || maximumBars > 24) {
            throw new IllegalArgumentException("CONTINUOUS_SIM_WINDOW_INVALID");
        }
        JsonNode time = get(TIME_PATH);
        if (!"0".equals(time.path("code").asText()) || !time.path("data").isArray()
                || time.path("data").size() != 1) {
            throw new IllegalStateException("PUBLIC_MARKET_TIME_UNAVAILABLE");
        }
        Instant serverTime;
        try {
            serverTime = Instant.ofEpochMilli(Long.parseLong(time.path("data").get(0).path("ts").asText()));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("PUBLIC_MARKET_TIME_UNAVAILABLE", ex);
        }
        Instant endExclusive = serverTime.truncatedTo(ChronoUnit.HOURS);
        if (firstOpen.isAfter(endExclusive)) {
            throw new IllegalStateException("PUBLIC_MARKET_TIME_BEFORE_CURSOR");
        }
        if (Duration.between(firstOpen, endExclusive).toHours() > MAX_CATCH_UP_BARS) {
            throw new IllegalStateException("BACKFILL_LIMIT_EXCEEDED");
        }
        Instant pageEnd = firstOpen.plus(Duration.ofHours(maximumBars));
        if (pageEnd.isAfter(endExclusive)) pageEnd = endExclusive;
        JsonNode instruments = get(RULE_PATH);
        OkxVenueRuleFact rule;
        try {
            rule = OkxVenueRuleFactsReader.parseSnapshot(instruments, Set.of("BTC-USDT"), serverTime)
                    .facts().getFirst();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("VENUE_RULE_UNAVAILABLE", ex);
        }
        if (!"LIVE".equals(rule.state()) || rule.tickSize().signum() <= 0
                || rule.lotSize().signum() <= 0 || rule.minimumSize().signum() <= 0) {
            throw new IllegalStateException("VENUE_RULE_UNAVAILABLE");
        }
        JsonNode ticker = get(TICKER_PATH);
        if (!"0".equals(ticker.path("code").asText()) || !ticker.path("data").isArray()
                || ticker.path("data").size() != 1) {
            throw new IllegalStateException("PUBLIC_MARKET_QUOTE_UNAVAILABLE");
        }
        ClosedBarMarketFeed.Quote quote;
        try {
            JsonNode row = ticker.path("data").get(0);
            if (!"BTC-USDT".equals(row.path("instId").asText())) {
                throw new IllegalArgumentException("wrong instrument");
            }
            Instant quoteTime = Instant.ofEpochMilli(Long.parseLong(row.path("ts").asText()));
            BigDecimal price = new BigDecimal(row.path("last").asText());
            if (price.signum() <= 0 || quoteTime.isAfter(serverTime.plusSeconds(5))
                    || quoteTime.isBefore(serverTime.minus(Duration.ofMinutes(2)))) {
                throw new IllegalArgumentException("stale quote");
            }
            quote = new ClosedBarMarketFeed.Quote(quoteTime, price);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("PUBLIC_MARKET_QUOTE_UNAVAILABLE", ex);
        }
        if (firstOpen.equals(endExclusive)) {
            return new Observation(serverTime, serverTime, List.of(),
                    new Rule(serverTime, rule.state(), rule.tickSize(), rule.lotSize(), rule.minimumSize()), quote);
        }
        String path = "/api/v5/market/history-candles?instId=BTC-USDT&bar=1H&after="
                + pageEnd.toEpochMilli() + "&before=" + (firstOpen.toEpochMilli() - 1)
                + "&limit=" + maximumBars;
        JsonNode candles = get(path);
        if (!"0".equals(candles.path("code").asText()) || !candles.path("data").isArray()
                || candles.path("data").size() > maximumBars) {
            throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_INVALID");
        }
        List<HistoricalBar> bars = new ArrayList<>();
        for (JsonNode row : candles.path("data")) {
            if (!row.isArray() || row.size() < 9) {
                throw new IllegalStateException("PUBLIC_MARKET_BAR_INVALID");
            }
            Instant open = Instant.ofEpochMilli(row.get(0).asLong());
            if (!"1".equals(row.get(8).asText()) || open.isBefore(firstOpen)
                    || !open.isBefore(pageEnd)
                    || open.toEpochMilli() % Duration.ofHours(1).toMillis() != 0) {
                continue;
            }
            bars.add(new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
                    open, open.plus(Duration.ofHours(1)).minusMillis(1),
                    decimal(row, 1), decimal(row, 2), decimal(row, 3), decimal(row, 4),
                    decimal(row, 5), row.get(7).isNull() ? null : decimal(row, 7),
                    null, "OK", row.toString(), serverTime));
        }
        bars.sort(Comparator.comparing(HistoricalBar::openTime));
        return new Observation(serverTime, serverTime, List.copyOf(bars),
                new Rule(serverTime, rule.state(), rule.tickSize(), rule.lotSize(), rule.minimumSize()), quote);
    }

    private BigDecimal decimal(JsonNode row, int index) {
        try {
            BigDecimal value = new BigDecimal(row.get(index).asText());
            if (value.signum() < 0 || (index != 5 && index != 7 && value.signum() == 0)) {
                throw new IllegalStateException("PUBLIC_MARKET_BAR_INVALID");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("PUBLIC_MARKET_BAR_INVALID", ex);
        }
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(origin.resolve(path))
                .timeout(Duration.ofSeconds(8)).GET().build();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) {
                        throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_TOO_LARGE");
                    }
                    if (response.statusCode() == 200) {
                        return mapper.readTree(bytes);
                    }
                    if (response.statusCode() < 500 || attempt == 2) {
                        throw new IllegalStateException("PUBLIC_MARKET_HTTP_" + response.statusCode());
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", ex);
            } catch (IOException ex) {
                if (attempt == 2) throw new IllegalStateException("PUBLIC_MARKET_UNAVAILABLE", ex);
            }
            try { Thread.sleep(500L * (attempt + 1)); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", ex);
            }
        }
        throw new IllegalStateException("PUBLIC_MARKET_UNAVAILABLE");
    }
}
