package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.marketdata.application.exception.PriceEventSourceConflictException;
import com.guidinglight.nexusquant.marketdata.application.service.PriceEventSourceCodec;
import com.guidinglight.nexusquant.marketdata.application.service.PriceEventSourceService;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;
import com.guidinglight.nexusquant.marketdata.domain.port.BarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.domain.port.PriceEventSourceRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 完整Flyway的独立PG验证来源记录；行情/时点为明确夹具，不冒充交易所认证、完整逐笔或交易E2E。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "nq.instrument.catalog-sync.enabled=false",
            "nq.env-safety.no-outbound=true",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.account.performance.sampling-enabled=false",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = PriceEventSourcesPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class PriceEventSourcesPostgresIntegrationTest {
    private static JdbcTemplate control;
    private static String database, url, dbUser, dbPassword, priorBarDigest;
    private static long priorBarId;
    private static final String PASSWORD = "synthetic-price-source-test-password";
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired PriceEventSourceService sources;
    @Autowired PriceEventSourceRepository repository;
    @Autowired BarProvenanceReadPort provenance;
    private long actor;
    private String operator, viewer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String base = System.getProperty("nq.postgres.smoke.url", "");
        dbUser = System.getProperty("nq.postgres.smoke.user", "");
        dbPassword = System.getProperty("nq.postgres.smoke.password", "");
        assertTrue(
                base.startsWith("jdbc:postgresql://")
                        && !dbUser.isBlank()
                        && !dbPassword.isBlank());
        URI uri = URI.create(base.substring(5));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getRawUserInfo() == null
                        && uri.getQuery() == null);
        database = "price_sources_" + UUID.randomUUID().toString().replace("-", "");
        control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                base.replaceFirst("/[^/]+$", "/postgres"), dbUser, dbPassword));
        control.execute("CREATE DATABASE \"" + database + "\"");
        url = base.replaceFirst("/[^/]+$", "/" + database);
        Flyway.configure()
                .dataSource(url, dbUser, dbPassword)
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();
        var prior = new JdbcTemplate(new DriverManagerDataSource(url, dbUser, dbPassword));
        priorBarId =
                prior.queryForObject(
                        """
                INSERT INTO marketdata_bars(exchange_code,market_type,symbol,"interval",open_time,close_time,open_price,high_price,low_price,close_price,volume,source)
                VALUES('OKX','SPOT','ETH-USDT','1m','2026-09-01T00:00:00Z','2026-09-01T00:00:59.999Z',2,3,1,2,1,'UPGRADE_SENTINEL') RETURNING marketdata_bar_id
                """,
                        Long.class);
        priorBarDigest =
                prior.queryForObject(
                        "SELECT md5(row_to_json(b)::text) FROM marketdata_bars b WHERE marketdata_bar_id=?",
                        String.class,
                        priorBarId);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPassword);
    }

    @BeforeEach
    void users() {
        String suffix = UUID.randomUUID().toString();
        operator = "source-op-" + suffix;
        viewer = "source-view-" + suffix;
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                operator,
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                viewer, passwords.encode(PASSWORD), List.of("VIEWER"), true)));
        actor = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, operator);
    }

    private StoredBarIdentity bar() {
        String interval = "1m";
        Instant at =
                start.plusSeconds(
                        jdbc.queryForObject("SELECT count(*) FROM marketdata_bars", Long.class)
                                * 60);
        long id =
                jdbc.queryForObject(
                        """
                INSERT INTO marketdata_bars(exchange_code,market_type,symbol,"interval",open_time,close_time,open_price,high_price,low_price,close_price,volume,source,available_at)
                VALUES('OKX','SPOT','BTC-USDT',?,?,?,100,110,90,105,1,'PRICE_SOURCE_FIXTURE',?) RETURNING marketdata_bar_id
                """,
                        Long.class,
                        interval,
                        Timestamp.from(at),
                        Timestamp.from(at.plusMillis(59999)),
                        Timestamp.from(at.plusSeconds(60)));
        return repository.findBar(id, false).orElseThrow();
    }

    private ObjectNode document(StoredBarIdentity target) {
        var b = target.bar();
        ObjectNode node = mapper.createObjectNode();
        node.put("exchangeCode", b.exchangeCode());
        node.put("marketType", b.marketType());
        node.put("symbol", b.symbol());
        node.put("interval", b.interval().wireValue());
        node.put("openTime", b.openTime().toString());
        node.put("closeTime", b.closeTime().toString());
        node.put("openPrice", b.openPrice().toPlainString());
        node.put("highPrice", b.highPrice().toPlainString());
        node.put("lowPrice", b.lowPrice().toPlainString());
        node.put("closePrice", b.closePrice().toPlainString());
        node.put("openPriceAt", b.openTime().plusMillis(123).toString());
        node.put("highPriceAt", b.openTime().plusSeconds(5).toString());
        node.put("lowPriceAt", b.openTime().plusSeconds(17).toString());
        node.put("closePriceAt", b.openTime().plusSeconds(58).plusNanos(456000).toString());
        return node;
    }

    private String hash(StoredBarIdentity bar) {
        return new PriceEventSourceCodec(mapper).barSha256(bar);
    }

    private PriceEventSourceRecord submit(StoredBarIdentity bar, UUID id, ObjectNode document) {
        return sources.submit(
                actor,
                id,
                bar.barId(),
                hash(bar),
                "explicit-source-fixture.json",
                document.toString());
    }

    private Map<String, Object> body(StoredBarIdentity bar, UUID id, ObjectNode doc) {
        return Map.of(
                "sourceId",
                id,
                "barId",
                bar.barId(),
                "expectedBarSha256",
                hash(bar),
                "sourceName",
                "explicit-source-fixture.json",
                "documentJson",
                doc.toString());
    }

    private String login(String username) throws Exception {
        String json =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(json).path("accessToken").asText();
    }

    @Test
    void v8UpgradeValidateAndRepeatedMigratePreservePriorBar() {
        var flyway =
                Flyway.configure()
                        .dataSource(url, dbUser, dbPassword)
                        .locations("classpath:db/migration")
                        .load();
        flyway.validate();
        assertEquals("9", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(
                priorBarDigest,
                jdbc.queryForObject(
                        "SELECT md5(row_to_json(b)::text) FROM marketdata_bars b WHERE marketdata_bar_id=?",
                        String.class,
                        priorBarId));
    }

    @Test
    void suppliedTimesRoundtripAndQueriesLeaveCanonicalBarAndResearchDigestUntouched()
            throws Exception {
        var target = bar();
        String frozen = SpotBarIdentity.capture(List.of(target.bar()), mapper).sha256();
        var before =
                provenance
                        .read("OKX", "SPOT", "BTC-USDT", "1m", List.of(target.bar().openTime()))
                        .get(target.bar().openTime());
        assertEquals("NOT_PROVIDED", before.priceEventTimes().status());
        assertNull(before.priceEventTimes().highPriceAt());
        UUID id = UUID.randomUUID();
        var saved = submit(target, id, document(target));
        var loaded = sources.read(id).orElseThrow();
        assertEquals(saved.openPriceAt(), loaded.openPriceAt());
        assertEquals(saved.closePriceAt(), loaded.closePriceAt());
        assertNotEquals(target.bar().openTime(), loaded.openPriceAt());
        assertEquals(456000, loaded.closePriceAt().getNano());
        var supplied =
                provenance
                        .read("OKX", "SPOT", "BTC-USDT", "1m", List.of(target.bar().openTime()))
                        .get(target.bar().openTime());
        assertEquals("SOURCE_PROVIDED", supplied.priceEventTimes().status());
        assertEquals(id, supplied.priceEventTimes().sourceId());
        assertEquals(
                frozen,
                SpotBarIdentity.capture(
                                List.of(
                                        repository
                                                .findBar(target.barId(), false)
                                                .orElseThrow()
                                                .bar()),
                                mapper)
                        .sha256());
        String result =
                mvc.perform(
                                get("/api/marketdata/bars")
                                        .header("Authorization", "Bearer " + login(viewer))
                                        .param("exchangeCode", "OKX")
                                        .param("symbol", "BTC-USDT")
                                        .param("interval", "1m")
                                        .param("startTime", target.bar().openTime().toString())
                                        .param("endTime", target.bar().closeTime().toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(
                "SOURCE_PROVIDED",
                mapper.readTree(result).get(0).path("priceEventTimes").path("status").asText());
        assertEquals(
                target.barId(),
                Long.parseLong(mapper.readTree(result).get(0).path("barId").asText()));
    }

    @Test
    void exactReplayReuseConflictAndChangedVersionDoNotOverwritePublishedRecord() {
        var target = bar();
        UUID id = UUID.randomUUID();
        var doc = document(target);
        var first = submit(target, id, doc);
        assertEquals(id, submit(target, id, doc).sourceId());
        assertEquals(id, submit(target, UUID.randomUUID(), doc).sourceId());
        ObjectNode changed = document(target);
        changed.put("highPriceAt", target.bar().openTime().plusSeconds(6).toString());
        assertThrows(
                PriceEventSourceConflictException.class,
                () -> submit(target, UUID.randomUUID(), changed));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources WHERE marketdata_bar_id=?",
                        Long.class,
                        target.barId()));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE marketdata_price_event_sources SET source_name='changed' WHERE source_id=?",
                                id));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM marketdata_price_event_sources WHERE source_id=?",
                                id));
        jdbc.update(
                "UPDATE marketdata_bars SET high_price=111 WHERE marketdata_bar_id=?",
                target.barId());
        assertThrows(
                PriceEventSourceConflictException.class,
                () ->
                        sources.submit(
                                actor,
                                UUID.randomUUID(),
                                target.barId(),
                                hash(target),
                                "explicit-source-fixture.json",
                                doc.toString()));
        var current =
                provenance
                        .read("OKX", "SPOT", "BTC-USDT", "1m", List.of(target.bar().openTime()))
                        .get(target.bar().openTime());
        assertNull(current.priceEventTimes().highPriceAt());
        assertEquals(first.documentSha256(), sources.read(id).orElseThrow().documentSha256());
    }

    @Test
    void viewerAnonymousInvalidSourceAndUnknownReadRespectExistingContracts() throws Exception {
        var target = bar();
        UUID id = UUID.randomUUID();
        String payload = mapper.writeValueAsString(body(target, id, document(target))),
                path = "/api/marketdata/price-event-sources";
        mvc.perform(post(path).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + login(viewer))
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isForbidden());
        assertTrue(sources.read(id).isEmpty());
        ObjectNode missing = document(target);
        missing.remove("highPriceAt");
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + login(operator))
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(body(target, id, missing))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + login(operator))
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.createdByUserId").value((int) actor));
        mvc.perform(get(path + "/" + id).header("Authorization", "Bearer " + login(viewer)))
                .andExpect(status().isOk());
        mvc.perform(
                        get(path + "/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + login(viewer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentDistinctSourcesHaveOneDurableWinner() throws Exception {
        var target = bar();
        ObjectNode first = document(target), second = document(target);
        second.put("highPriceAt", target.bar().openTime().plusSeconds(6).toString());
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks =
                    List.of(first, second).stream()
                            .map(
                                    doc ->
                                            pool.submit(
                                                    () -> {
                                                        ready.countDown();
                                                        if (!go.await(5, TimeUnit.SECONDS))
                                                            throw new IllegalStateException(
                                                                    "test barrier timeout");
                                                        try {
                                                            return submit(
                                                                            target,
                                                                            UUID.randomUUID(),
                                                                            doc)
                                                                    .sourceId()
                                                                    .toString();
                                                        } catch (
                                                                PriceEventSourceConflictException
                                                                        conflict) {
                                                            return "CONFLICT";
                                                        }
                                                    }))
                            .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            var results =
                    List.of(
                            tasks.get(0).get(10, TimeUnit.SECONDS),
                            tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM marketdata_price_event_sources WHERE marketdata_bar_id=?",
                            Long.class,
                            target.barId()));
        } finally {
            go.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void nullDocumentValuesAndInvalidDatesFailDatabaseConstraints() {
        var target = bar();
        var saved = submit(target, UUID.randomUUID(), document(target));
        ObjectNode nullDocument = document(target);
        nullDocument.putNull("openPriceAt");
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                """
                INSERT INTO marketdata_price_event_sources(source_id,marketdata_bar_id,bar_identity_sha256,source_name,document_sha256,document_json,bar_snapshot_json,
                    open_price_at,high_price_at,low_price_at,close_price_at,created_by_user_id,created_at)
                VALUES(?,?,?,'invalid',?,?::jsonb,?::jsonb,?,?,?,?,?,?)
                """,
                                UUID.randomUUID(),
                                target.barId(),
                                "f".repeat(64),
                                saved.documentSha256(),
                                nullDocument.toString(),
                                saved.barSnapshotJson(),
                                Timestamp.from(saved.openPriceAt()),
                                Timestamp.from(saved.highPriceAt()),
                                Timestamp.from(saved.lowPriceAt()),
                                Timestamp.from(saved.closePriceAt()),
                                actor,
                                Timestamp.from(saved.createdAt())));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources WHERE marketdata_bar_id=?",
                        Long.class,
                        target.barId()));
        assertFalse(sources.read(UUID.randomUUID()).isPresent());
    }

    @Test
    void unknownSaveVerificationIsReadonlyAndComparesCompleteCapturedPayload() {
        var target = bar();
        UUID id = UUID.randomUUID();
        var doc = document(target);
        assertEquals(
                "NOT_FOUND",
                sources.verify(
                                actor,
                                id,
                                target.barId(),
                                hash(target),
                                "explicit-source-fixture.json",
                                doc.toString())
                        .status());
        var saved = submit(target, id, doc);
        assertEquals(
                "MATCH_FOUND",
                sources.verify(
                                actor,
                                id,
                                target.barId(),
                                hash(target),
                                saved.sourceName(),
                                doc.toString())
                        .status());
        ObjectNode changed = document(target);
        changed.put("highPriceAt", target.bar().openTime().plusSeconds(6).toString());
        assertEquals(
                "CONFLICT",
                sources.verify(
                                actor,
                                id,
                                target.barId(),
                                hash(target),
                                saved.sourceName(),
                                changed.toString())
                        .status());
        assertEquals(
                "CONFLICT",
                sources.verify(
                                actor + 1,
                                id,
                                target.barId(),
                                hash(target),
                                saved.sourceName(),
                                doc.toString())
                        .status());
        assertEquals(
                "MATCH_FOUND",
                sources.verify(
                                actor,
                                UUID.randomUUID(),
                                target.barId(),
                                hash(target),
                                saved.sourceName(),
                                doc.toString())
                        .status());
        jdbc.update(
                "UPDATE marketdata_bars SET high_price=111 WHERE marketdata_bar_id=?",
                target.barId());
        assertEquals(
                "MATCH_FOUND",
                sources.verify(
                                actor,
                                id,
                                target.barId(),
                                hash(target),
                                saved.sourceName(),
                                doc.toString())
                        .status());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources WHERE marketdata_bar_id=?",
                        Long.class,
                        target.barId()));
    }

    /** 通过正式JWT/Servlet适配核验三种结果及写权限，所有核验都不改变持久化记录。 */
    @Test
    void verificationHttpReturnsMatchConflictAndNotFoundWithoutWriting() throws Exception {
        var target = bar();
        UUID id = UUID.randomUUID();
        var doc = document(target);
        String path = "/api/marketdata/price-event-sources/verification", token = login(operator);
        String payload = mapper.writeValueAsString(body(target, id, doc));
        long before =
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources", Long.class);
        mvc.perform(post(path).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + login(viewer))
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("NOT_FOUND"));
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources", Long.class));
        submit(target, id, doc);
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("MATCH_FOUND"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.source.sourceId").value(id.toString()));
        doc.put("highPriceAt", target.bar().openTime().plusSeconds(6).toString());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(body(target, id, doc))))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CONFLICT"));
        assertEquals(
                before + 1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketdata_price_event_sources", Long.class));
    }

    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) {
            if (control != null && database != null) {
                assertTrue(database.matches("price_sources_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
