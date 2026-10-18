package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 校验稳定输入、原主体和重放边界；原子性、互斥与重启另由真实PG证明。 */
class ContinuousSimOperationServiceTest {
    private final ContinuousSimOperationRepository operations =
            mock(ContinuousSimOperationRepository.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ContinuousSimRunService> provider = mock(ObjectProvider.class);

    private final ContinuousSimRunService driver = mock(ContinuousSimRunService.class);
    private final ContinuousSimOperationService service =
            new ContinuousSimOperationService(operations, provider);
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-15T01:00:00Z");

    @Test
    void numericEquivalentBudgetUsesSameIdentityButBusinessInputAndOwnerDoNot() {
        var original = create("100");
        assertEquals(original.fingerprint(7), create("100.00000000").fingerprint(7));
        assertNotEquals(original.fingerprint(7), create("100.00000001").fingerprint(7));
        assertNotEquals(original.fingerprint(7), original.fingerprint(8));
        assertNotEquals(
                original.fingerprint(7),
                new ContinuousSimOperationRequest(
                                id,
                                ContinuousSimOperationRequest.Type.CREATE,
                                "publish-other",
                                new BigDecimal("100"),
                                null)
                        .fingerprint(7));
        var pause = state(ContinuousSimOperationRequest.Type.PAUSE);
        assertNotEquals(
                pause.fingerprint(7),
                state(ContinuousSimOperationRequest.Type.RESUME).fingerprint(7));
    }

    @Test
    void invalidOrMixedInputIsRejectedBeforeAnyRepositoryAccess() {
        for (var request :
                List.of(
                        create("0"),
                        create("-1"),
                        create("100000.00000001"),
                        create("0.000000001"),
                        new ContinuousSimOperationRequest(id, null, null, null, null),
                        new ContinuousSimOperationRequest(
                                id,
                                ContinuousSimOperationRequest.Type.CREATE,
                                "publish\uD800",
                                new BigDecimal("1"),
                                null),
                        new ContinuousSimOperationRequest(
                                id,
                                ContinuousSimOperationRequest.Type.CREATE,
                                "publish-a",
                                new BigDecimal("1"),
                                "ptr-other"),
                        new ContinuousSimOperationRequest(
                                id,
                                ContinuousSimOperationRequest.Type.PAUSE,
                                "publish-a",
                                null,
                                "ptr-a"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.submit(request, 7, "actor", "trace"));
        }
        verifyNoInteractions(operations, provider, driver);
    }

    @Test
    void replayReturnsOriginalSnapshotBeforeLookingForDisabledDriver() {
        var request = create("100");
        var original = receipt(request, "RUNNING");
        when(operations.find(id)).thenReturn(Optional.of(original));
        assertEquals(original, service.submit(create("100.0"), 7, "actor", "later-trace"));
        verify(operations).lock(id);
        verifyNoInteractions(provider, driver);
        verify(operations, never())
                .complete(any(), anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void changedInputOrOwnerCannotReuseCompletedIdentity() {
        when(operations.find(id)).thenReturn(Optional.of(receipt(create("100"), "RUNNING")));
        assertEquals(
                "CONTINUOUS_SIM_OPERATION_ID_CONFLICT",
                assertThrows(
                                IllegalStateException.class,
                                () -> service.submit(create("101"), 7, "actor", "trace"))
                        .getMessage());
        assertThrows(
                IllegalStateException.class,
                () -> service.submit(create("100"), 8, "other", "trace"));
        verifyNoInteractions(provider, driver);
    }

    @Test
    void onlyOriginalOwnerReadsReceipt() {
        var original = receipt(create("100"), "RUNNING");
        when(operations.find(id)).thenReturn(Optional.of(original));
        assertEquals(Optional.of(original), service.find(id, 7));
        assertTrue(service.find(id, 8).isEmpty());
        verifyNoInteractions(provider, driver);
    }

    @Test
    void createRecordsAuthenticatedActorAndOriginalResult() {
        var request = create("100");
        var complete = receipt(request, "RUNNING");
        when(operations.find(id)).thenReturn(Optional.empty(), Optional.of(complete));
        when(provider.getIfAvailable()).thenReturn(driver);
        when(driver.start("publish-a", request.budget(), "actor")).thenReturn(complete.result());
        assertEquals(complete, service.submit(request, 7, "actor", "trace"));
        verify(operations)
                .complete(
                        eq(request),
                        eq(7L),
                        eq("actor"),
                        eq(request.fingerprint(7)),
                        eq("trace"),
                        eq(complete.result()),
                        any(Instant.class));
    }

    @Test
    void pauseAndResumeKeepSeparateCommandKinds() {
        for (var type :
                List.of(
                        ContinuousSimOperationRequest.Type.PAUSE,
                        ContinuousSimOperationRequest.Type.RESUME)) {
            var request = state(type);
            var complete =
                    receipt(
                            request,
                            type == ContinuousSimOperationRequest.Type.PAUSE
                                    ? "STOPPED"
                                    : "RUNNING");
            when(operations.find(id)).thenReturn(Optional.empty(), Optional.of(complete));
            when(provider.getIfAvailable()).thenReturn(driver);
            when(driver.stop("ptr-a")).thenReturn(complete.result());
            when(driver.resume("ptr-a")).thenReturn(complete.result());
            assertEquals(complete, service.submit(request, 7, "actor", "trace"));
        }
        verify(driver).stop("ptr-a");
        verify(driver).resume("ptr-a");
    }

    @Test
    void unavailableOrFailedDriverDoesNotWriteSuccessfulReceipt() {
        var request = create("100");
        when(operations.find(id)).thenReturn(Optional.empty());
        assertEquals(
                "CONTINUOUS_SIM_DISABLED",
                assertThrows(
                                IllegalStateException.class,
                                () -> service.submit(request, 7, "actor", "trace"))
                        .getMessage());
        when(provider.getIfAvailable()).thenReturn(driver);
        when(driver.start("publish-a", request.budget(), "actor"))
                .thenThrow(new IllegalStateException("CONTINUOUS_SIM_SEED_INVALID"));
        assertThrows(
                IllegalStateException.class, () -> service.submit(request, 7, "actor", "trace"));
        verify(operations, never())
                .complete(any(), anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    private ContinuousSimOperationRequest create(String budget) {
        return new ContinuousSimOperationRequest(
                id,
                ContinuousSimOperationRequest.Type.CREATE,
                "publish-a",
                new BigDecimal(budget),
                null);
    }

    private ContinuousSimOperationRequest state(ContinuousSimOperationRequest.Type type) {
        return new ContinuousSimOperationRequest(id, type, null, null, "ptr-a");
    }

    private ContinuousSimOperation receipt(ContinuousSimOperationRequest request, String status) {
        var result =
                new ContinuousSimRunService.Status(
                        "ptr-a",
                        status,
                        now,
                        null,
                        null,
                        now,
                        now.plusSeconds(3600),
                        now,
                        null,
                        null,
                        null,
                        null,
                        0);
        return new ContinuousSimOperation(
                id,
                7,
                "actor",
                request.type(),
                request.publishId(),
                request.budget(),
                "ptr-a",
                request.fingerprint(7),
                "trace",
                result,
                now);
    }
}
