package com.guidinglight.nexusquant.adapter.okx.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.policy.PublicMarketDataOutboundPolicy;
import com.guidinglight.nexusquant.adapter.okx.error.OkxApiException;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.guidinglight.nexusquant.marketdata.domain.OrderBookSourceException;
import com.guidinglight.nexusquant.marketdata.domain.PublicOrderBookSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.port.PublicOrderBookReadPort;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 公共盘口必须同时启用manual外联与独立盘口开关；客户端不持有凭据且只GET固定现货资源。 */
@Component
@Profile("public-marketdata-manual")
// 两个开关必须分别显式为true；不把外部配置插入SpEL，非法值保持关闭且不破坏其他组件发现。
@ConditionalOnProperty(
        prefix = "nq.public-marketdata",
        name = {"outbound.enabled", "order-book.enabled"},
        havingValue = "true",
        matchIfMissing = false)
public class OkxPublicOrderBookAdapter implements PublicOrderBookReadPort, DisposableBean {
    private final OkxHttpClient client;
    private final Clock clock;
    private final PublicMarketDataOutboundPolicy policy;
    private HttpClient ownedTransport;

    @Autowired
    public OkxPublicOrderBookAdapter(
            @Value("${nq.public-marketdata.outbound.base-url:http://127.0.0.1:0}") String origin,
            @Value("${nq.public-marketdata.outbound.total-request-timeout:PT8S}")
                    Duration timeout) {
        this(publicClient(origin, timeout), Clock.systemUTC());
    }

    OkxPublicOrderBookAdapter(OkxHttpClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
        this.policy = new PublicMarketDataOutboundPolicy(clock, true);
    }

    private OkxPublicOrderBookAdapter(Transport transport, Clock clock) {
        this(transport.client(), clock);
        this.ownedTransport = transport.http();
    }

    private static Transport publicClient(String base, Duration timeout) {
        URI origin;
        try {
            origin = URI.create(base);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("PUBLIC_ORDER_BOOK_CONFIGURATION_INVALID");
        }
        boolean vendor =
                "https".equals(origin.getScheme())
                        && List.of("www.okx.com", "okx.com").contains(origin.getHost());
        boolean fixture =
                "http".equals(origin.getScheme())
                        && List.of("127.0.0.1", "localhost").contains(origin.getHost());
        if ((!vendor && !fixture)
                || origin.getUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0)
            throw new IllegalArgumentException("PUBLIC_ORDER_BOOK_CONFIGURATION_INVALID");
        var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        return new Transport(
                new OkxHttpClient(http, new ObjectMapper(), base.replaceAll("/$", ""), timeout),
                http);
    }

    @Override
    public String exchangeCode() {
        return "OKX";
    }

    @Override
    public PublicOrderBookSnapshot fetch(String symbol) {
        String path = "/api/v5/market/books?instId=" + symbol + "&sz=20";
        if (!policy.evaluate(
                        PublicMarketDataOutboundRequest.publicGet(
                                "OKX", PublicMarketDataEndpointCategory.ORDER_BOOK, path))
                .allowed()) throw new OrderBookSourceException("SOURCE_REQUEST_DENIED");
        JsonNode payload;
        try {
            payload = client.getBounded(path, null, 65536);
        } catch (OkxApiException ex) {
            throw new OrderBookSourceException("SOURCE_UNAVAILABLE");
        }
        try {
            if (!"0".equals(payload.path("code").asText())
                    || !payload.path("data").isArray()
                    || payload.path("data").size() != 1) throw invalid();
            var book = payload.path("data").get(0);
            var bids = levels(book.path("bids"), true);
            var asks = levels(book.path("asks"), false);
            if (new BigDecimal(bids.getFirst().price())
                            .compareTo(new BigDecimal(asks.getFirst().price()))
                    >= 0) throw invalid();
            Instant sourceTime = Instant.ofEpochMilli(Long.parseLong(book.path("ts").asText()));
            Instant received = clock.instant();
            if (sourceTime.isAfter(received.plusSeconds(5))) throw invalid();
            return new PublicOrderBookSnapshot(
                    "OKX",
                    "SPOT",
                    symbol,
                    "AVAILABLE",
                    sourceTime,
                    received,
                    received,
                    "OKX_PUBLIC_REST",
                    20,
                    null,
                    bids,
                    asks);
        } catch (NumberFormatException | DateTimeException ex) {
            throw invalid();
        }
    }

    private static List<PublicOrderBookSnapshot.Level> levels(JsonNode rows, boolean bid) {
        if (!rows.isArray() || rows.isEmpty() || rows.size() > 20) throw invalid();
        var levels = new ArrayList<PublicOrderBookSnapshot.Level>();
        BigDecimal previous = null, cumulative = BigDecimal.ZERO;
        for (var row : rows) {
            if (!row.isArray()
                    || row.size() != 4
                    || !row.get(0).isTextual()
                    || !row.get(1).isTextual()
                    || row.get(0).asText().length() > 80
                    || row.get(1).asText().length() > 80) throw invalid();
            var price = new BigDecimal(row.get(0).asText());
            var quantity = new BigDecimal(row.get(1).asText());
            if (price.signum() <= 0
                    || quantity.signum() <= 0
                    || price.precision() > 40
                    || quantity.precision() > 40
                    || Math.abs(price.scale()) > 18
                    || Math.abs(quantity.scale()) > 18) throw invalid();
            if (previous != null
                    && (bid ? price.compareTo(previous) >= 0 : price.compareTo(previous) <= 0))
                throw invalid();
            String count = row.get(3).asText();
            if (!count.matches("[0-9]{1,9}")) throw invalid();
            cumulative = cumulative.add(quantity);
            previous = price;
            levels.add(
                    new PublicOrderBookSnapshot.Level(
                            price.toPlainString(),
                            quantity.toPlainString(),
                            cumulative.toPlainString(),
                            Integer.parseInt(count)));
        }
        return List.copyOf(levels);
    }

    private static OrderBookSourceException invalid() {
        return new OrderBookSourceException("SOURCE_INVALID");
    }

    @Override
    public void destroy() {
        if (ownedTransport != null) ownedTransport.close();
    }

    private record Transport(OkxHttpClient client, HttpClient http) {}
}
