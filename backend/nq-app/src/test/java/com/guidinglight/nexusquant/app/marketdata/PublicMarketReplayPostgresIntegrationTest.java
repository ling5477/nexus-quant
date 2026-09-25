package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcHistoricalMarketDataPort;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataDatasetRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 只在显式指定 disposable PostgreSQL URL 时运行；网络调用仅由定向 smoke 执行。 */
class PublicMarketReplayPostgresIntegrationTest {
    @Test
    void livePublicCaptureMigratesAndReplaysFromImmutablePostgres() throws Exception {
        String url = System.getProperty("nq.public-replay.postgres.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "disposable localhost PostgreSQL URL required");
        }
        Flyway flyway = Flyway.configure().dataSource(url, "postgres", "")
                .locations("classpath:db/migration").cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        assertEquals("54", flyway.info().current().getVersion().getVersion());
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "postgres", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var capture = new PublicMarketReplayCaptureService(http, URI.create("https://www.okx.com"),
                mapper, jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                Clock.systemUTC());
        Instant start = Instant.parse("2026-09-20T00:00:00Z");
        Instant end = Instant.parse("2026-09-23T00:00:00Z");
        var result = capture.capture(start, end, "ISOLATED_SMOKE");
        assertEquals(72, result.barCount());
        assertTrue(result.observedAt().isAfter(end));
        assertEquals("CURRENTLY_OBSERVED_PUBLIC_RULES", result.rulePolicy());
        assertTrue(result.ruleRawSha256().matches("[0-9a-f]{64}"));
        var datasetService = new MarketdataDatasetService(
                new JdbcMarketdataDatasetRepository(jdbc, mapper,
                        mock(AdmissionMutationCoordinator.class)), mapper);
        String snapshot = datasetService.buildDatasetSnapshot(result.datasetId());
        assertEquals("public-capture", mapper.readTree(snapshot).path("provider").asText());
        assertEquals(result.consumedSha256(),
                mapper.readTree(snapshot).path("capture").path("consumedSha256").asText());
        var spec = new HistoricalDatasetSpec("public-capture", result.datasetId().toString(),
                "OKX", "BTC-USDT", BarInterval.ONE_HOUR, result.datasetId().toString());
        var query = new HistoricalMarketDataQuery(spec, "OKX", "SPOT", "BTC-USDT",
                BarInterval.ONE_HOUR, start, end.minusMillis(1), 0, 500);
        var port = new JdbcHistoricalMarketDataPort(jdbc);
        var replayA = port.loadBars(query);
        var replayB = port.loadBars(query);
        assertEquals(result.consumedSha256(), SpotBarIdentity.capture(replayA, mapper).sha256());
        assertEquals(SpotBarIdentity.capture(replayA, mapper).sha256(),
                SpotBarIdentity.capture(replayB, mapper).sha256());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE public_market_captures SET raw_response='changed' WHERE dataset_id=?",
                result.datasetId()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE marketdata_datasets SET bar_count=1 WHERE dataset_id=?", result.datasetId()));
        UUID corruptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,
                    symbol,"interval",start_time,end_time,status,quality_status,bar_count,gap_count,
                    source,created_by,created_at,updated_at,request_json)
                SELECT ?,?,exchange_code,market_type,symbol,"interval",start_time,end_time,
                    status,quality_status,bar_count,gap_count,source,created_by,created_at,
                    updated_at,request_json FROM marketdata_datasets WHERE dataset_id=?
                """, corruptId, "corrupt-fixture-" + corruptId, result.datasetId());
        jdbc.update("""
                INSERT INTO public_market_captures(dataset_id,observed_at,requested_start,requested_end,
                    request_path,raw_response,raw_sha256,normalized_sha256,consumed_sha256,
                    replay_visibility_version,replay_visibility_source,rule_request_path,
                    rule_raw_response,rule_raw_sha256,rule_observed_at,rule_sha256,rule_json,
                    bars_json,bar_count,first_open_time,last_open_time)
                SELECT ?,observed_at,requested_start,requested_end,request_path,raw_response,
                    ?,normalized_sha256,consumed_sha256,replay_visibility_version,
                    replay_visibility_source,rule_request_path,rule_raw_response,rule_raw_sha256,
                    rule_observed_at,rule_sha256,rule_json,bars_json,bar_count,first_open_time,
                    last_open_time FROM public_market_captures WHERE dataset_id=?
                """, corruptId, "0".repeat(64), result.datasetId());
        var corruptSpec = new HistoricalDatasetSpec("public-capture", corruptId.toString(),
                "OKX", "BTC-USDT", BarInterval.ONE_HOUR, corruptId.toString());
        var corruptQuery = new HistoricalMarketDataQuery(corruptSpec, "OKX", "SPOT", "BTC-USDT",
                BarInterval.ONE_HOUR, start, end.minusMillis(1), 0, 500);
        assertEquals("PUBLIC_CAPTURE_IDENTITY_MISMATCH", assertThrows(IllegalStateException.class,
                () -> port.loadBars(corruptQuery)).getMessage());
        for (String forgedField : new String[] { "normalizedSha256", "ruleSha256", "observedAt" }) {
            UUID forgedId = insertForgedSourceLink(jdbc, result.datasetId(), forgedField);
            var forgedSpec = new HistoricalDatasetSpec("public-capture", forgedId.toString(),
                    "OKX", "BTC-USDT", BarInterval.ONE_HOUR, forgedId.toString());
            var forgedQuery = new HistoricalMarketDataQuery(forgedSpec, "OKX", "SPOT", "BTC-USDT",
                    BarInterval.ONE_HOUR, start, end.minusMillis(1), 0, 500);
            assertEquals(switch (forgedField) {
                        case "normalizedSha256" -> "PUBLIC_CAPTURE_SOURCE_LINK_MISMATCH";
                        case "ruleSha256" -> "PUBLIC_RULE_SOURCE_LINK_MISMATCH";
                        default -> "PUBLIC_CAPTURE_DATASET_IDENTITY_MISMATCH";
                    },
                    assertThrows(IllegalStateException.class, () -> port.loadBars(forgedQuery)).getMessage());
        }
        System.out.println("PUBLIC_MARKET_SMOKE_OBSERVED dataset=" + result.datasetId()
                + " observedAt=" + result.observedAt() + " raw=" + result.rawSha256()
                + " normalized=" + result.normalizedSha256() + " consumed=" + result.consumedSha256()
                + " ruleObservedAt=" + result.ruleObservedAt() + " ruleRaw="
                + result.ruleRawSha256() + " rule=" + result.ruleSha256());
    }

    private UUID insertForgedSourceLink(JdbcTemplate jdbc, UUID sourceId, String field) {
        UUID forgedId = UUID.randomUUID();
        String forgedDigest = "0".repeat(64);
        jdbc.update("""
                INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,
                    symbol,"interval",start_time,end_time,status,quality_status,bar_count,gap_count,
                    source,created_by,created_at,updated_at,request_json)
                SELECT ?,?,exchange_code,market_type,symbol,"interval",start_time,end_time,
                    status,quality_status,bar_count,gap_count,source,created_by,created_at,
                    updated_at,jsonb_set(request_json,ARRAY[?],to_jsonb(?::text))
                FROM marketdata_datasets WHERE dataset_id=?
                """, forgedId, "forged-source-" + forgedId, field, forgedDigest, sourceId);
        jdbc.update("""
                INSERT INTO public_market_captures(dataset_id,observed_at,requested_start,requested_end,
                    request_path,raw_response,raw_sha256,normalized_sha256,consumed_sha256,
                    replay_visibility_version,replay_visibility_source,rule_request_path,
                    rule_raw_response,rule_raw_sha256,rule_observed_at,rule_sha256,rule_json,
                    bars_json,bar_count,first_open_time,last_open_time)
                SELECT ?,observed_at,requested_start,requested_end,request_path,raw_response,
                    raw_sha256,CASE WHEN ?='normalizedSha256' THEN ? ELSE normalized_sha256 END,
                    consumed_sha256,replay_visibility_version,replay_visibility_source,
                    rule_request_path,rule_raw_response,rule_raw_sha256,rule_observed_at,
                    CASE WHEN ?='ruleSha256' THEN ? ELSE rule_sha256 END,rule_json,
                    bars_json,bar_count,first_open_time,last_open_time
                FROM public_market_captures WHERE dataset_id=?
                """, forgedId, field, forgedDigest, field, forgedDigest, sourceId);
        return forgedId;
    }
}
