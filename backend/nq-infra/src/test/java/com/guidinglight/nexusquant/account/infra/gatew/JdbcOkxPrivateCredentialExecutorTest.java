package com.guidinglight.nexusquant.account.infra.gatew;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcOkxPrivateCredentialExecutorTest {

    @Test
    void returnsUnavailableWithoutDecryptForZeroCandidates() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(0, validPayload());

        OkxPrivateReadException ex = assertThrows(OkxPrivateReadException.class,
                () -> executor(jdbc, successfulTransport()).withActiveCredential(
                        7L, 9L, "OKX_API_V5", session -> observation()));

        assertEquals(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE, ex.category());
        assertEquals(0, jdbc.decryptCalls.get());
    }

    @Test
    void rejectsOwnerExchangeAccountStatusAndCredentialLifecycleExclusionsWithoutDecrypt() {
        for (String excludedCase : List.of(
                "OWNER_MISMATCH", "NON_OKX_ACCOUNT", "INACTIVE_ACCOUNT",
                "DISABLED", "REVOKED", "EXPIRED", "ROTATED", "INACTIVE_CREDENTIAL"
        )) {
            StubJdbcTemplate jdbc = new StubJdbcTemplate(0, validPayload());

            OkxPrivateReadException ex = assertThrows(
                    OkxPrivateReadException.class,
                    () -> executor(jdbc, successfulTransport()).withActiveCredential(
                            7L, 9L, "OKX_API_V5", session -> observation()),
                    excludedCase
            );

            assertEquals(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE, ex.category(), excludedCase);
            assertEquals(0, jdbc.decryptCalls.get(), excludedCase);
        }
    }

    @Test
    void returnsConflictWithoutDecryptForMultipleCandidates() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(2, validPayload());

        OkxPrivateReadException ex = assertThrows(OkxPrivateReadException.class,
                () -> executor(jdbc, successfulTransport()).withActiveCredential(
                        7L, 9L, "OKX_API_V5", session -> observation()));

        assertEquals(OkxPrivateReadError.CREDENTIAL_CONFLICT, ex.category());
        assertEquals(0, jdbc.decryptCalls.get());
    }

    @Test
    void enforcesOwnerOkxActiveAccountAndExactCredentialLifecycleInBothQueries() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(1, validPayload());
        JdbcOkxPrivateCredentialExecutor executor = executor(jdbc, successfulTransport());

        executor.withActiveCredential(7L, 9L, "OKX_API_V5", session -> {
            session.execute(OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.DEMO);
            return observation();
        });

        assertEquals(2, jdbc.sql.size());
        for (String sql : jdbc.sql) {
            String normalized = sql.replaceAll("\\s+", " ").toUpperCase();
            assertTrue(normalized.contains("JOIN EXCHANGE_ACCOUNTS A"));
            assertTrue(normalized.contains("A.OWNER_USER_ID = ?"));
            assertTrue(normalized.contains("A.EXCHANGE_CODE = 'OKX'"));
            assertTrue(normalized.contains("A.STATUS = 'ACTIVE'"));
            assertTrue(normalized.contains("C.CREDENTIAL_TYPE = ?"));
            assertTrue(normalized.contains("C.IS_ACTIVE = TRUE"));
            assertTrue(normalized.contains("C.CREDENTIAL_STATUS = 'ACTIVE'"));
            assertTrue(normalized.contains("C.REVOKED_AT IS NULL"));
            assertTrue(normalized.contains("C.ROTATED_AT IS NULL"));
            assertFalse(normalized.contains("LIMIT 1"));
        }
    }

    @Test
    void decryptsOnceExecutesSynchronousSessionAndClearsCredentialBuffers() throws Exception {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(1, validPayload());
        AtomicInteger transportCalls = new AtomicInteger();
        AtomicReference<char[]> secretReference = new AtomicReference<>();
        OkxPrivateReadTransport transport = (request, credential, environment) -> {
            transportCalls.incrementAndGet();
            secretReference.set(secretBuffer(credential));
            assertFalse(allCleared(secretReference.get()));
            return new OkxPrivateReadResult(request.operation(), Set.of("READ_ONLY"), 0, true);
        };

        OkxPrivateReadObservation result = executor(jdbc, transport).withActiveCredential(
                7L,
                9L,
                "OKX_API_V5",
                session -> {
                    session.execute(OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.DEMO);
                    return observation();
                }
        );

        assertEquals(OkxPrivateProbeStatus.PASSED_READ_ONLY, result.probeStatus());
        assertEquals(1, jdbc.decryptCalls.get());
        assertEquals(1, transportCalls.get());
        assertTrue(allCleared(secretReference.get()));
    }

    @Test
    void rejectsTypeFallbackAndSanitizesMalformedCredentialPayload() {
        StubJdbcTemplate validJdbc = new StubJdbcTemplate(1, validPayload());
        OkxPrivateReadException typeError = assertThrows(OkxPrivateReadException.class,
                () -> executor(validJdbc, successfulTransport()).withActiveCredential(
                        7L, 9L, "OTHER", session -> observation()));
        assertEquals(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE, typeError.category());
        assertEquals(0, validJdbc.decryptCalls.get());

        String marker = "plaintext-secret-marker-should-not-escape";
        StubJdbcTemplate malformedJdbc = new StubJdbcTemplate(1, "{not-json:" + marker);
        OkxPrivateReadException malformed = assertThrows(OkxPrivateReadException.class,
                () -> executor(malformedJdbc, successfulTransport()).withActiveCredential(
                        7L, 9L, "OKX_API_V5", session -> observation()));
        assertEquals(OkxPrivateReadError.AUTHENTICATION_FAILURE, malformed.category());
        assertNull(malformed.getCause());
        assertFalse(malformed.getMessage().contains(marker));
        assertFalse(malformed.toString().contains(marker));
    }

    @Test
    void sanitizesJdbcOrDecryptFailureCause() {
        String marker = "jdbc-decrypt-secret-marker-should-not-escape";
        JdbcTemplate failingJdbc = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String query, Class<T> requiredType, Object... args) {
                throw new IllegalStateException(marker);
            }
        };

        OkxPrivateReadException failure = assertThrows(
                OkxPrivateReadException.class,
                () -> executor(failingJdbc, successfulTransport()).withActiveCredential(
                        7L, 9L, "OKX_API_V5", session -> observation())
        );

        assertEquals(OkxPrivateReadError.AUTHENTICATION_FAILURE, failure.category());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(marker));
        assertFalse(failure.toString().contains(marker));
    }

    @Test
    void sessionCannotEscapeCallbackOrRunOnAnotherThread() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(1, validPayload());
        AtomicReference<OkxPrivateCredentialExecutor.CredentialSession> captured = new AtomicReference<>();
        JdbcOkxPrivateCredentialExecutor executor = executor(jdbc, successfulTransport());

        executor.withActiveCredential(7L, 9L, "OKX_API_V5", session -> {
            captured.set(session);
            CompletionException asynchronous = assertThrows(CompletionException.class,
                    () -> java.util.concurrent.CompletableFuture.supplyAsync(() -> session.execute(
                            OkxPrivateReadRequest.accountConfiguration(),
                            OkxPrivateEnvironment.DEMO
                    )).join());
            assertTrue(asynchronous.getCause() instanceof OkxPrivateReadException);
            assertEquals(
                    OkxPrivateReadError.AUTHENTICATION_FAILURE,
                    ((OkxPrivateReadException) asynchronous.getCause()).category()
            );
            return observation();
        });

        OkxPrivateReadException expired = assertThrows(OkxPrivateReadException.class,
                () -> captured.get().execute(
                        OkxPrivateReadRequest.accountConfiguration(),
                        OkxPrivateEnvironment.DEMO
                ));
        assertEquals(OkxPrivateReadError.AUTHENTICATION_FAILURE, expired.category());

        Method callback = OkxPrivateCredentialExecutor.CredentialCallback.class.getDeclaredMethods()[0];
        // GateW-3 将 callback 结果泛型化以返回 immutable normalized snapshot；类型擦除为 Object，
        // session 的线程绑定和 callback 结束即失效边界保持不变。
        assertEquals(Object.class, callback.getReturnType());
        assertEquals(OkxPrivateCredentialExecutor.CredentialSession.class, callback.getParameterTypes()[0]);
    }

    private static JdbcOkxPrivateCredentialExecutor executor(
            JdbcTemplate jdbcTemplate,
            OkxPrivateReadTransport transport
    ) {
        return new JdbcOkxPrivateCredentialExecutor(
                jdbcTemplate,
                new ObjectMapper(),
                "test-master-key",
                transport
        );
    }

    private static OkxPrivateReadTransport successfulTransport() {
        return (request, credential, environment) -> new OkxPrivateReadResult(
                request.operation(),
                request.operation() == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ
                        ? Set.of("READ_ONLY") : Set.of(),
                0,
                true
        );
    }

    private static OkxPrivateReadObservation observation() {
        return new OkxPrivateReadObservation(
                OkxPrivateProbeStatus.PASSED_READ_ONLY,
                Instant.parse("2026-07-13T00:00:00Z"),
                null,
                Set.of("READ_ONLY"),
                0,
                "COMPLETE",
                List.of(),
                List.of(),
                true, true, true, false, true, false
        );
    }

    private static String validPayload() {
        return "{\"apiKey\":\"fake-key\",\"secretKey\":\"fake-secret\",\"passphrase\":\"fake-pass\"}";
    }

    private static char[] secretBuffer(Object credential) {
        try {
            Field field = credential.getClass().getDeclaredField("secretKey");
            field.setAccessible(true);
            return (char[]) field.get(credential);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static boolean allCleared(char[] value) {
        return value != null && Arrays.stream(new String(value).chars().toArray())
                .allMatch(character -> character == 0);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final int candidates;
        private final String payload;
        private final AtomicInteger decryptCalls = new AtomicInteger();
        private final List<String> sql = new java.util.ArrayList<>();

        private StubJdbcTemplate(int candidates, String payload) {
            this.candidates = candidates;
            this.payload = payload;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String query, Class<T> requiredType, Object... args) {
            sql.add(query);
            if (Integer.class.equals(requiredType)) {
                return (T) Integer.valueOf(candidates);
            }
            decryptCalls.incrementAndGet();
            return (T) payload;
        }
    }
}
