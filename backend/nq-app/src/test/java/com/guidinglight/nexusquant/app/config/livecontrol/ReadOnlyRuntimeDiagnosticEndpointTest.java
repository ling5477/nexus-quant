package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadOnlyRuntimeDiagnosticEndpointTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-08-21T00:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-21T00:01:00Z");

    @Test
    void exposesReadOperationOnly() throws NoSuchMethodException {
        var readMethod = ReadOnlyRuntimeDiagnosticEndpoint.class.getMethod("read");

        assertNotNull(readMethod.getAnnotation(ReadOperation.class));
        assertNull(readMethod.getAnnotation(WriteOperation.class));
        assertNull(readMethod.getAnnotation(DeleteOperation.class));
    }

    @Test
    void returnsDiagnosticIdentityWithoutInventingProductionCounters() {
        AtomicInteger killReads = new AtomicInteger();
        Clock clock = Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);
        ReadOnlyRuntimeDiagnosticEndpoint endpoint = endpoint(killReads, clock, false, false);

        ReadOnlyRuntimeDiagnosticEndpoint.RuntimeDiagnostic first = endpoint.read();
        ReadOnlyRuntimeDiagnosticEndpoint.RuntimeDiagnostic second = endpoint.read();

        assertEquals("1111111111111111111111111111111111111111", first.sourceCommit());
        assertEquals(first.sourceCommit(), first.releaseId());
        assertEquals(21, first.javaMajor());
        assertEquals("gatey-readonly-qualification", first.qualificationProfile());
        assertEquals("read-only-provider-observation", first.capabilityIdentity());
        assertEquals("127.0.0.1", first.bindAddress());
        assertTrue(first.providerObservationEnabled());
        assertFalse(first.tradingComponentsEnabled());
        assertFalse(first.liveEnabled());
        assertEquals("ENGAGED", first.killSwitch());
        assertEquals(7, first.killSwitchVersion());
        assertFalse(first.mutationRuntimeBound());
        assertEquals("NOT_INSTRUMENTED", first.credentialMetadataReads().status());
        assertNull(first.credentialMetadataReads().value());
        assertEquals("NOT_INSTRUMENTED", first.credentialMaterialReads().status());
        assertEquals("NOT_INSTRUMENTED", first.decryptCount().status());
        assertEquals("NOT_INSTRUMENTED", first.okxGetCount().status());
        assertEquals("NOT_INSTRUMENTED", first.okxPostCount().status());
        assertEquals("NOT_INSTRUMENTED", first.executionIntentDelta().status());
        assertEquals("NOT_INSTRUMENTED", first.executionReceiptDelta().status());
        assertEquals("NOT_INSTRUMENTED", first.orderDelta().status());
        assertEquals("NOT_INSTRUMENTED", first.ledgerDelta().status());
        assertTrue(first.diagnosticOnly());
        assertFalse(first.tradingAuthorization());
        assertTrue(first.noSideEffect());
        assertEquals(first.credentialMetadataReads(), second.credentialMetadataReads());
        assertEquals(first.okxGetCount(), second.okxGetCount());
        assertEquals(2, killReads.get());
    }

    @Test
    void reportsUnsafeRuntimeFactsWithoutGrantingAuthorization() {
        AtomicInteger killReads = new AtomicInteger();
        ReadOnlyRuntimeDiagnosticEndpoint endpoint = endpoint(
                killReads,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC),
                true,
                true
        );

        ReadOnlyRuntimeDiagnosticEndpoint.RuntimeDiagnostic diagnostic = endpoint.read();

        assertTrue(diagnostic.liveEnabled());
        assertTrue(diagnostic.mutationRuntimeBound());
        assertFalse(diagnostic.tradingAuthorization());
        assertTrue(diagnostic.diagnosticOnly());
    }

    private static ReadOnlyRuntimeDiagnosticEndpoint endpoint(
            AtomicInteger killReads,
            Clock clock,
            boolean liveEnabled,
            boolean mutationRuntimeBound
    ) {
        KillSwitchState state = new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING,
                KillSwitchStatus.ENGAGED,
                7,
                "DEPLOYMENT_TEST",
                "TEST",
                UPDATED_AT,
                "test-operator",
                "trace-test"
        );
        KillSwitchStateRepository repository = new KillSwitchStateRepository() {
            @Override
            public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
                killReads.incrementAndGet();
                return Optional.of(state);
            }

            @Override
            public KillSwitchState engage(KillSwitchEngageCommand command) {
                throw new AssertionError("diagnostic endpoint must not mutate kill state");
            }
        };
        MockEnvironment environment = new MockEnvironment()
                .withProperty("nq.runtime.trading-components.enabled", "false")
                .withProperty(
                        "nq.runtime.provider-observation.deployment-profile",
                        "gatey-readonly-qualification"
                )
                .withProperty("nq.env-safety.live-enabled", Boolean.toString(liveEnabled));
        return new ReadOnlyRuntimeDiagnosticEndpoint(
                new ReadOnlyProviderObservationRuntimeIdentity(
                        "1111111111111111111111111111111111111111",
                        "1111111111111111111111111111111111111111",
                        ReadOnlyProviderObservationRuntimeIdentity.CAPABILITY,
                        "127.0.0.1",
                        21
                ),
                new KillSwitchService(repository, clock),
                environment,
                clock,
                () -> mutationRuntimeBound
        );
    }
}
