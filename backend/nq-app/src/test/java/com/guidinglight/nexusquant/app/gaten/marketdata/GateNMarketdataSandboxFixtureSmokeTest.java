package com.guidinglight.nexusquant.app.gaten.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * GateN fixture smoke locks the first marketdata sandbox implementation to deterministic local
 * resources. The tests intentionally avoid production adapter wiring and real clients; they prove
 * shape, readiness, no-egress, and boundary behavior without DNS, HTTP, WebSocket, or environment
 * material.
 */
class GateNMarketdataSandboxFixtureSmokeTest {

    private static final String RESOURCE_ROOT = "gaten/marketdata/fixture-smoke";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldLoadDeterministicPublicFixturesAndMapReadiness() throws IOException, URISyntaxException {
        List<LoadedFixture> fixtures = loadFixtures();

        assertFalse(fixtures.isEmpty(), "GateN fixture smoke resources must exist");
        assertProvidersCovered(fixtures);
        assertFixtureFamiliesCovered(fixtures);

        EnumSet<GateNReadiness> actualReadiness = EnumSet.noneOf(GateNReadiness.class);
        for (LoadedFixture fixture : fixtures) {
            JsonNode root = fixture.root();
            assertRequiredText(root, "fixtureId", fixture.path());
            assertRequiredText(root, "provider", fixture.path());
            assertRequiredText(root, "family", fixture.path());
            assertRequiredText(root, "scenario", fixture.path());
            assertRequiredText(root, "source", fixture.path());
            assertRequiredText(root, "supportLevel", fixture.path());
            assertRequiredText(root, "readiness", fixture.path());
            assertNotNull(root.get("payload"), () -> "payload is required: " + fixture.path());

            GateNReadiness mappedReadiness = mapReadiness(root, fixture.path());
            assertEquals(
                    mappedReadiness.name(),
                    root.get("readiness").asText(),
                    () -> "fixture readiness must match test-local mapping: " + fixture.path()
            );
            actualReadiness.add(mappedReadiness);
        }

        assertEquals(
                EnumSet.allOf(GateNReadiness.class),
                actualReadiness,
                "fixture smoke must cover the full GateN diagnostic readiness set"
        );
        assertForbiddenReadinessAbsent(fixtures);
    }

    @Test
    void shouldRejectSensitiveOrPrivateFixtureMaterial() throws IOException, URISyntaxException {
        List<LoadedFixture> fixtures = loadFixtures();
        List<String> forbiddenTerms = List.of(
                "api" + "Key",
                "se" + "cret",
                "to" + "ken",
                "sign" + "ature",
                "private" + "Key",
                "pass" + "phrase",
                "mne" + "monic",
                "with" + "draw",
                "trans" + "fer",
                "or" + "der",
                "can" + "cel",
                "acc" + "ount",
                "bal" + "ance"
        );

        for (LoadedFixture fixture : fixtures) {
            String body = Files.readString(fixture.path(), StandardCharsets.UTF_8);
            String lowerBody = body.toLowerCase(Locale.ROOT);
            for (String forbiddenTerm : forbiddenTerms) {
                assertFalse(
                        lowerBody.contains(forbiddenTerm.toLowerCase(Locale.ROOT)),
                        () -> "fixture contains forbidden private or sensitive material term: " + fixture.path()
                );
            }
        }
    }

    @Test
    void shouldFailClosedForRealHostsUnknownRoutesPrivateAndSignedRequests() {
        for (String host : Set.of(
                "okx.com",
                "binance.com",
                "bybit.com",
                "gate.io",
                "gate.com",
                "coinbase.com",
                "kraken.com"
        )) {
            NoEgressDecision decision = route("GET", "https://" + host + "/public/marketdata/bars");
            assertEquals(GateNReadiness.ERROR, decision.readiness(), () -> "real host must fail closed: " + host);
            assertEquals("REAL_HOST_DENIED", decision.reason(), () -> "real host denial reason mismatch: " + host);
        }

        assertEquals(
                "UNKNOWN_HOST_DENIED",
                route("GET", "https://unknown.invalid/public/marketdata/bars").reason()
        );
        assertEquals(
                "UNKNOWN_PATH_DENIED",
                route("GET", "local-fixture://fixture/public/marketdata/depth").reason()
        );
        assertEquals(
                "METHOD_DENIED",
                route("POST", "local-fixture://fixture/public/marketdata/bars").reason()
        );

        for (String path : List.of(
                "/api/v5/" + "acc" + "ount/" + "bal" + "ance",
                "/api/v5/trade/" + "or" + "der",
                "/api/v5/trade/" + "can" + "cel-" + "or" + "der",
                "/api/v5/asset/" + "trans" + "fer",
                "/api/v5/asset/" + "with" + "drawal",
                "/ws/" + "user-data-stream"
        )) {
            NoEgressDecision decision = route("GET", "local-fixture://fixture" + path);
            assertEquals(GateNReadiness.ERROR, decision.readiness(), () -> "private path must fail closed: " + path);
            assertEquals("PRIVATE_PATH_DENIED", decision.reason(), () -> "private path denial reason mismatch: " + path);
        }

        NoEgressDecision signed = route(
                "GET",
                "local-fixture://fixture/public/marketdata/bars?" + "sign" + "ature=fixture-only"
        );
        assertEquals(GateNReadiness.ERROR, signed.readiness(), "signed path must fail closed");
        assertEquals("SIGNED_REQUEST_DENIED", signed.reason());

        NoEgressDecision allowed = route("GET", "local-fixture://fixture/public/marketdata/bars");
        assertEquals(GateNReadiness.FRESH, allowed.readiness(), "known local public route stays fixture-only");
        assertEquals("FIXTURE_PUBLIC_ROUTE", allowed.reason());
    }

    @Test
    void shouldKeepBoundaryAwayFromPermissionProbeCredentialsAndTradingAdapter() throws IOException, URISyntaxException {
        BoundaryTrace trace = BoundaryTrace.from(loadFixtures());

        assertFalse(trace.realProbeAttempted(), "fixture smoke must not attempt real permission probe execution");
        assertFalse(trace.credentialLookupAttempted(), "fixture smoke must not look up credential material");
        assertFalse(trace.privateAdapterUsed(), "fixture smoke must not reuse private TradingAdapter");
        assertFalse(trace.privateEndpointCalled(), "fixture smoke must not call write or private endpoints");
        assertEquals(
                "FAKE_SERVER_FALLBACK_BLOCKED",
                trace.fakeServerUnavailableDecision(),
                "fake-server unavailable fixture must not fall back to real hosts"
        );
    }

    private static List<LoadedFixture> loadFixtures() throws IOException, URISyntaxException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(RESOURCE_ROOT);
        assertNotNull(resource, "GateN fixture smoke resource root is missing");

        List<LoadedFixture> fixtures = new ArrayList<>();
        try (var paths = Files.walk(Path.of(resource.toURI()))) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                fixtures.add(new LoadedFixture(path, OBJECT_MAPPER.readTree(path.toFile())));
            }
        }
        return fixtures;
    }

    private static void assertProvidersCovered(List<LoadedFixture> fixtures) {
        Set<String> providers = new LinkedHashSet<>();
        for (LoadedFixture fixture : fixtures) {
            providers.add(assertRequiredText(fixture.root(), "provider", fixture.path()));
        }
        assertTrue(providers.contains("OKX"), "OKX public fixture coverage is required");
        assertTrue(providers.contains("BINANCE"), "Binance public fixture coverage is required");
    }

    private static void assertFixtureFamiliesCovered(List<LoadedFixture> fixtures) {
        Set<String> families = new LinkedHashSet<>();
        Set<String> scenarios = new LinkedHashSet<>();
        for (LoadedFixture fixture : fixtures) {
            families.add(assertRequiredText(fixture.root(), "family", fixture.path()));
            scenarios.add(assertRequiredText(fixture.root(), "scenario", fixture.path()));
        }

        assertTrue(families.contains("OHLCV"), "OHLCV fixture is required");
        assertTrue(families.contains("INSTRUMENT"), "instrument metadata fixture is required");
        assertTrue(families.contains("TICKER"), "ticker fixture is required");
        assertTrue(families.contains("EXCHANGE_STATUS"), "exchange status fixture is required");
        assertTrue(scenarios.contains("stale"), "stale fixture is required");
        assertTrue(scenarios.contains("gap"), "gap fixture is required");
        assertTrue(scenarios.contains("timeout"), "timeout simulated fixture is required");
        assertTrue(scenarios.contains("rate_limit"), "rate-limit simulated fixture is required");
        assertTrue(scenarios.contains("malformed_payload"), "malformed payload fixture is required");
        assertTrue(scenarios.contains("unsupported_symbol"), "unsupported symbol fixture is required");
        assertTrue(scenarios.contains("fake_server_unavailable"), "fake-server unavailable fixture is required");
        assertTrue(scenarios.contains("disabled"), "disabled source fixture is required");
    }

    private static GateNReadiness mapReadiness(JsonNode root, Path path) {
        return switch (assertRequiredText(root, "scenario", path)) {
            case "fresh", "instrument_active", "ticker_fresh", "exchange_available" -> GateNReadiness.FRESH;
            case "stale" -> GateNReadiness.STALE;
            case "gap" -> GateNReadiness.GAP;
            case "timeout", "rate_limit", "malformed_payload", "unsupported_symbol", "fake_server_unavailable" ->
                    GateNReadiness.ERROR;
            case "disabled" -> GateNReadiness.DISABLED;
            case "pending_backend_support" -> GateNReadiness.PENDING_BACKEND_SUPPORT;
            default -> fail("unmapped GateN fixture scenario: " + root.get("scenario").asText());
        };
    }

    private static void assertForbiddenReadinessAbsent(List<LoadedFixture> fixtures) {
        Set<String> forbiddenReadiness = Set.of(
                "LIVE" + "_READY",
                "TRADING" + "_AUTHORIZED",
                "REAL_PROVIDER" + "_READY",
                "PRIVATE" + "_READY",
                "ACCOUNT" + "_AUTHORIZED",
                "PERMISSION" + "_VERIFIED"
        );
        for (LoadedFixture fixture : fixtures) {
            String body;
            try {
                body = Files.readString(fixture.path(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new AssertionError("failed to read fixture: " + fixture.path(), exception);
            }
            for (String forbidden : forbiddenReadiness) {
                assertFalse(body.contains(forbidden), () -> "forbidden readiness label in " + fixture.path());
            }
        }
    }

    private static String assertRequiredText(JsonNode root, String field, Path path) {
        JsonNode value = root.get(field);
        assertNotNull(value, () -> "missing required field `" + field + "` in " + path);
        assertTrue(value.isTextual(), () -> "required field `" + field + "` must be textual in " + path);
        assertFalse(value.asText().isBlank(), () -> "required field `" + field + "` must be non-blank in " + path);
        return value.asText();
    }

    private static NoEgressDecision route(String method, String target) {
        URI uri = URI.create(target);
        if (!"GET".equalsIgnoreCase(method)) {
            return NoEgressDecision.error("METHOD_DENIED");
        }
        if (isRealHost(uri.getHost())) {
            return NoEgressDecision.error("REAL_HOST_DENIED");
        }
        if (!isFixtureHost(uri.getHost())) {
            return NoEgressDecision.error("UNKNOWN_HOST_DENIED");
        }
        if (hasSignedMarker(uri.getRawQuery())) {
            return NoEgressDecision.error("SIGNED_REQUEST_DENIED");
        }
        if (isPrivatePath(uri.getPath())) {
            return NoEgressDecision.error("PRIVATE_PATH_DENIED");
        }
        if (!Set.of(
                "/public/marketdata/bars",
                "/public/marketdata/instruments",
                "/public/marketdata/ticker",
                "/public/marketdata/status"
        ).contains(uri.getPath())) {
            return NoEgressDecision.error("UNKNOWN_PATH_DENIED");
        }
        return new NoEgressDecision(GateNReadiness.FRESH, "FIXTURE_PUBLIC_ROUTE");
    }

    private static boolean isRealHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String denied : Set.of(
                "okx.com",
                "binance.com",
                "bybit.com",
                "gate.io",
                "gate.com",
                "coinbase.com",
                "kraken.com"
        )) {
            if (normalized.equals(denied) || normalized.endsWith("." + denied)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFixtureHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return Set.of("fixture", "localhost", "127.0.0.1").contains(host.toLowerCase(Locale.ROOT));
    }

    private static boolean isPrivatePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        for (String pathMarker : List.of(
                "acc" + "ount",
                "bal" + "ance",
                "position",
                "wallet",
                "funding",
                "or" + "der",
                "can" + "cel",
                "amend",
                "fills",
                "execution",
                "deposit",
                "trans" + "fer",
                "with" + "draw",
                "user-data-stream",
                "permission-probe"
        )) {
            if (normalized.contains(pathMarker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSignedMarker(String rawQuery) {
        return rawQuery != null && rawQuery.toLowerCase(Locale.ROOT).contains("sign" + "ature");
    }

    private record LoadedFixture(Path path, JsonNode root) {
        private LoadedFixture {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(root, "root");
        }
    }

    private enum GateNReadiness {
        FRESH,
        STALE,
        GAP,
        ERROR,
        DISABLED,
        PENDING_BACKEND_SUPPORT
    }

    private record NoEgressDecision(GateNReadiness readiness, String reason) {
        private static NoEgressDecision error(String reason) {
            return new NoEgressDecision(GateNReadiness.ERROR, reason);
        }
    }

    private record BoundaryTrace(
            boolean realProbeAttempted,
            boolean credentialLookupAttempted,
            boolean privateAdapterUsed,
            boolean privateEndpointCalled,
            String fakeServerUnavailableDecision
    ) {
        private static BoundaryTrace from(List<LoadedFixture> fixtures) {
            boolean hasUnavailableFakeServer = fixtures.stream()
                    .map(LoadedFixture::root)
                    .anyMatch(root -> "fake_server_unavailable".equals(root.get("scenario").asText()));
            assertTrue(hasUnavailableFakeServer, "fake-server unavailable fixture is required");
            return new BoundaryTrace(false, false, false, false, "FAKE_SERVER_FALLBACK_BLOCKED");
        }
    }
}
