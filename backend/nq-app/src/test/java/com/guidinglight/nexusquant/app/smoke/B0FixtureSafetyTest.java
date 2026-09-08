package com.guidinglight.nexusquant.app.smoke;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 永久负例：任何不安全 fixture 参数必须在 JDBC 或进程启动之前拒绝。 */
class B0FixtureSafetyTest {
    private static final String DB = "nq_f002_b0_0123456789abcdef0123456789abcdef";
    private static final String URL = "jdbc:postgresql://127.0.0.1:15432/" + DB;
    private static final String VENUE = "http://127.0.0.1:18080";

    @Test void permitsOnlyExplicitDisposableInitialPrecondition() {
        assertDoesNotThrow(() -> B0Fixture.validate(URL, DB, "b0-test", VENUE, Map.of(), false));
    }
    @Test void rejectsMissingOwnershipCapabilityAndIdentity() {
        assertThrows(IllegalStateException.class, () -> B0Fixture.create(null));
        assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, null, "b0-test", VENUE, Map.of(), false));
    }
    @Test void rejectsSharedDatabaseIdentityAndRemoteJdbcBeforeConnection() {
        for (String invalid : new String[]{"jdbc:postgresql://127.0.0.1:15432/postgres",
                "jdbc:postgresql://db.example:5432/" + DB, URL + "?currentSchema=public",
                "jdbc:postgresql://localhost:15432/" + DB, URL + "#fragment", "jdbc:h2:mem:b0"}) {
            assertThrows(IllegalStateException.class, () -> B0Fixture.validate(invalid, DB, "b0-test", VENUE, Map.of(), false));
        }
        assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, "production", "b0-test", VENUE, Map.of(), false));
    }
    @Test void rejectsMissingTestProfileAndRuntimeBootstrap() {
        for (String profile : new String[]{"local", "prod", "", "b0-test,prod"}) {
            assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, DB, profile, VENUE, Map.of(), false));
        }
        assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, DB, "b0-test", VENUE, Map.of(), true));
    }
    @Test void rejectsCredentialEndpointAndJvmOptionInjection() {
        for (String key : new String[]{"NQ_OKX_API_KEY", "NQ_BINANCE_API_SECRET", "SPRING_APPLICATION_JSON",
                "SPRING_DATASOURCE_URL", "NQ_OKX_BASE_URL", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> B0Fixture.validate(URL, DB, "b0-test", VENUE, Map.of(key, "synthetic-negative-fixture"), false));
            assertEquals("BLOCKED / UNSAFE_TEST_FIXTURE_TARGET", error.getMessage());
        }
    }
    @Test void rejectsNonLoopbackVenueAndUrlSmuggling() {
        for (String endpoint : new String[]{"https://www.okx.com", "http://localhost:18080", "http://127.0.0.2:18080",
                VENUE + "/other", VENUE + "?endpoint=other", "http://user@127.0.0.1:18080", VENUE + "#fragment"}) {
            assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, DB, "b0-test", endpoint, Map.of(), false));
        }
    }
    @Test void permitsOnlyOwnedJdbcSocketAndSyntheticHttpOrigin() {
        assertTrue(B0NqProcessMain.allowedDestination(java.net.URI.create("socket://127.0.0.1:15432"), VENUE, URL));
        assertTrue(B0NqProcessMain.allowedDestination(java.net.URI.create(VENUE + "/api/v5/trade/order"), VENUE, URL));
        for (String target : new String[]{"socket://127.0.0.1:5432", "socket://www.okx.com:443",
                "https://127.0.0.1:18080", "http://127.0.0.1:15432", "http://www.okx.com:18080"}) {
            assertFalse(B0NqProcessMain.allowedDestination(java.net.URI.create(target), VENUE, URL));
        }
    }
    @Test void rejectsMismatchedChildFixtureIdentity() {
        assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, DB, "b0-test", VENUE,
                Map.of("NQ_B0_DB", "jdbc:postgresql://127.0.0.1:15432/production"), false));
        assertThrows(IllegalStateException.class, () -> B0Fixture.validate(URL, DB, "b0-test", VENUE,
                Map.of("NQ_B0_PROFILE", "prod"), false));
    }
}
