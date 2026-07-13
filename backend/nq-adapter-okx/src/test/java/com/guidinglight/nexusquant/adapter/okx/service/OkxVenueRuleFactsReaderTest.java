package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OkxVenueRuleFactsReaderTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);

    @Test
    void shouldParseCompleteLiveSpotResponseUsingOfficialCurrencies() throws Exception {
        OkxVenueRuleFactsSnapshot snapshot = reader(response(item(
                "live", "XBT", "USDT", "0.1", "0.0001", "0.001",
                "100", "100000", "1000000", "1000000", "[]"
        ))).fetch(Set.of("BTC-USDT"), "trace-public-facts");

        OkxVenueRuleFact fact = snapshot.facts().getFirst();
        assertEquals(OBSERVED_AT, snapshot.observedAt());
        assertEquals("BTC-USDT", fact.instId());
        assertEquals("SPOT", fact.instType());
        assertEquals("LIVE", fact.state());
        assertEquals("XBT", fact.baseCurrency());
        assertEquals("USDT", fact.quoteCurrency());
        assertEquals("0.1", fact.tickSize().toPlainString());
        assertEquals("100000", fact.maximumMarketSize().toPlainString());
        assertEquals("USDT", fact.maximumMarketSizeUnit());
    }

    @Test
    void shouldPreserveNonLiveStatusAndBlankNullableMaxFields() throws Exception {
        OkxVenueRuleFact fact = reader(response(item(
                "preopen", "BTC", "USDT", "0.1", "0.0001", "0.001",
                "", "", "", "", "[]"
        ))).fetch(Set.of("BTC-USDT"), "trace-preopen").facts().getFirst();

        assertEquals("PREOPEN", fact.state());
        assertNull(fact.maximumLimitSize());
        assertNull(fact.maximumMarketSize());
        assertNull(fact.maximumMarketSizeUnit());
        assertNull(fact.maximumLimitAmountUsd());
        assertNull(fact.maximumMarketAmountUsd());
    }

    @Test
    void jsonFieldOrderShouldNotChangeParsedFacts() throws Exception {
        String ordered = item(
                "live", "BTC", "USDT", "0.1", "0.0001", "0.001",
                "100", "100000", "1000000", "1000000", "[]"
        );
        String reordered = """
                {
                  "maxMktAmt":"1000000","quoteCcy":"USDT","instId":"BTC-USDT",
                  "minSz":"0.001","state":"live","maxLmtAmt":"1000000",
                  "baseCcy":"BTC","maxMktSz":"100000","lotSz":"0.0001",
                  "instType":"SPOT","maxLmtSz":"100","tickSz":"0.1","upcChg":[]
                }
                """;

        OkxVenueRuleFact first = reader(response(ordered)).fetch(Set.of("BTC-USDT"), "trace-order-a")
                .facts().getFirst();
        OkxVenueRuleFact second = reader(response(reordered)).fetch(Set.of("BTC-USDT"), "trace-order-b")
                .facts().getFirst();

        assertEquals(first, second);
    }

    @Test
    void shouldFailClosedForInvalidZeroOrNegativeDecimals() throws Exception {
        for (String invalid : new String[]{"invalid", "0", "-0.1"}) {
            OkxVenueRuleFactsReader reader = reader(response(item(
                    "live", "BTC", "USDT", invalid, "0.0001", "0.001",
                    "100", "100000", "1000000", "1000000", "[]"
            )));

            assertThrows(
                    IllegalStateException.class,
                    () -> reader.fetch(Set.of("BTC-USDT"), "trace-invalid-decimal")
            );
        }
    }

    @Test
    void shouldKeepPlannedChangesPostponedUntilCanonicalRepresentationIsPersistable() throws Exception {
        String changes = """
                [
                  {"param":"tickSz","newValue":"0.01","effTime":"1783936860000"}
                ]
                """;

        OkxVenueRuleFact fact = reader(response(item(
                "live", "BTC", "USDT", "0.1", "0.0001", "0.001",
                "100", "100000", "1000000", "1000000", changes
        ))).fetch(Set.of("BTC-USDT"), "trace-upcoming").facts().getFirst();

        assertNull(fact.nextRuleEffectiveAt());
    }

    @Test
    void shouldRejectMoreThanThreeOrMissingAllowlistedSymbolsBeforeReturningSnapshot() throws Exception {
        OkxVenueRuleFactsReader reader = reader(response(item(
                "live", "BTC", "USDT", "0.1", "0.0001", "0.001",
                "100", "100000", "1000000", "1000000", "[]"
        )));

        assertThrows(IllegalArgumentException.class, () -> reader.fetch(
                Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT", "DOGE-USDT"),
                "trace-too-many"
        ));
        assertThrows(IllegalStateException.class, () -> reader.fetch(
                Set.of("BTC-USDT", "ETH-USDT"),
                "trace-missing"
        ));
    }

    @Test
    void shouldUseOnlyFixedPublicEndpointWithoutAuthentication() throws Exception {
        StubOkxHttpClient client = new StubOkxHttpClient(response(item(
                "live", "BTC", "USDT", "0.1", "0.0001", "0.001",
                "100", "100000", "1000000", "1000000", "[]"
        )));

        new OkxVenueRuleFactsReader(client, CLOCK).fetch(Set.of("BTC-USDT"), "trace-boundary");

        assertEquals(OkxVenueRuleFactsReader.INSTRUMENTS_ENDPOINT, client.lastPath());
        assertTrue(client.lastPath().startsWith("/api/v5/public/"));
    }

    private static OkxVenueRuleFactsReader reader(JsonNode response) {
        return new OkxVenueRuleFactsReader(new StubOkxHttpClient(response), CLOCK);
    }

    private static JsonNode response(String itemJson) throws Exception {
        return new ObjectMapper().readTree("{\"code\":\"0\",\"data\":[" + itemJson + "]}");
    }

    private static String item(
            String state,
            String baseCurrency,
            String quoteCurrency,
            String tickSize,
            String lotSize,
            String minimumSize,
            String maximumLimitSize,
            String maximumMarketSize,
            String maximumLimitAmount,
            String maximumMarketAmount,
            String upcomingChanges
    ) {
        return """
                {
                  "instId":"BTC-USDT","instType":"SPOT","state":"%s",
                  "baseCcy":"%s","quoteCcy":"%s","tickSz":"%s","lotSz":"%s","minSz":"%s",
                  "maxLmtSz":"%s","maxMktSz":"%s","maxLmtAmt":"%s","maxMktAmt":"%s",
                  "upcChg":%s
                }
                """.formatted(
                state,
                baseCurrency,
                quoteCurrency,
                tickSize,
                lotSize,
                minimumSize,
                maximumLimitSize,
                maximumMarketSize,
                maximumLimitAmount,
                maximumMarketAmount,
                upcomingChanges
        );
    }

    private static final class StubOkxHttpClient extends OkxHttpClient {

        private final JsonNode response;
        private String lastPath;

        private StubOkxHttpClient(JsonNode response) {
            super(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "http://127.0.0.1:0",
                    Duration.ofSeconds(1)
            );
            this.response = response;
        }

        @Override
        public JsonNode get(String requestPathWithQuery, String traceId) {
            lastPath = requestPathWithQuery;
            return response;
        }

        private String lastPath() {
            return lastPath;
        }
    }
}
