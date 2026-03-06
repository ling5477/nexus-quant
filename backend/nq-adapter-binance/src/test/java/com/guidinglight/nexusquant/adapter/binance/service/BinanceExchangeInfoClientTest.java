package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * BinanceExchangeInfoClientTest 验证 exchangeInfo 的 filters 解析。
 */
class BinanceExchangeInfoClientTest {

    @Test
    void shouldParseSpotExchangeInfoFilters() throws Exception {
        String body = """
                {
                  \"timezone\":\"UTC\",
                  \"serverTime\":1700000000000,
                  \"symbols\":[
                    {
                      \"symbol\":\"BTCUSDT\",
                      \"status\":\"TRADING\",
                      \"baseAsset\":\"BTC\",
                      \"quoteAsset\":\"USDT\",
                      \"filters\":[
                        {\"filterType\":\"PRICE_FILTER\",\"minPrice\":\"0.01000000\",\"maxPrice\":\"1000000.00000000\",\"tickSize\":\"0.01000000\"},
                        {\"filterType\":\"LOT_SIZE\",\"minQty\":\"0.00001000\",\"maxQty\":\"9000.00000000\",\"stepSize\":\"0.00001000\"},
                        {\"filterType\":\"MARKET_LOT_SIZE\",\"minQty\":\"0.00000000\",\"maxQty\":\"171.46807523\",\"stepSize\":\"0.00000000\"},
                        {\"filterType\":\"MIN_NOTIONAL\",\"minNotional\":\"5.00000000\",\"applyToMarket\":true}
                      ]
                    }
                  ]
                }
                """;
        try (TestServer server = new TestServer(body)) {
            BinanceHttpClient httpClient = new BinanceHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new BinanceRequestSigner(),
                    () -> 1_700_000_000_000L,
                    new BinanceApiCredentials("", "")
            );
            BinanceExchangeInfoClient client = new BinanceExchangeInfoClient(httpClient);

            Map<String, BinanceSymbolFilters> snapshot = client.fetchSpotExchangeInfo("trc-binance-exchange-info");
            BinanceSymbolFilters filters = snapshot.get("BTCUSDT");

            assertNotNull(filters);
            assertEquals("BTC-USDT", filters.internalSymbol());
            assertEquals("TRADING", filters.status());
            assertEquals(0, filters.tickSize().compareTo(new java.math.BigDecimal("0.01000000")));
            assertEquals(0, filters.stepSize().compareTo(new java.math.BigDecimal("0.00001000")));
            assertEquals(0, filters.minQty().compareTo(new java.math.BigDecimal("0.00001000")));
            assertEquals(0, filters.minNotional().compareTo(new java.math.BigDecimal("5.00000000")));
            assertEquals(true, filters.minNotionalAppliesToMarket());
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(String responseBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                byte[] response = responseBody.getBytes();
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response);
                }
            });
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
