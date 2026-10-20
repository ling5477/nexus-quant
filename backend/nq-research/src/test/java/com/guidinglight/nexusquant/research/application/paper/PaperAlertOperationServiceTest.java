package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.command.PaperAlertOperationCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperAlertOperationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperAlertOperation;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperAlertOperationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 证明原结果与当前告警分离；事务原子性仍必须由真实PG/HTTP场景证明。 */
class PaperAlertOperationServiceTest {
    private final UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final PaperAlertOperationRepository operations =
            mock(PaperAlertOperationRepository.class);
    private final PaperRunMonitorService monitor = mock(PaperRunMonitorService.class);
    private final PaperTradingRunService runs = mock(PaperTradingRunService.class);
    private PaperAlertOperationService service;

    @BeforeEach
    void setup() {
        service = new PaperAlertOperationService(operations, monitor, runs);
        when(operations.find(id)).thenReturn(Optional.empty());
    }

    private PaperAlertOperationCommand create(String title) {
        return new PaperAlertOperationCommand(
                PaperAlertOperationCommand.Type.CREATE,
                null,
                "SYSTEM_NOTICE",
                "LOW",
                title,
                null,
                "MANUAL",
                null);
    }

    private PaperRunAlert alert(PaperRunAlertStatus status) {
        Instant time = Instant.parse("2026-10-15T01:02:03.123456Z");
        return new PaperRunAlert(
                "alt-one",
                "ptr-one",
                "SYSTEM_NOTICE",
                PaperRunAlertSeverity.LOW,
                status,
                "original",
                null,
                "MANUAL",
                "{}",
                null,
                null,
                null,
                time,
                time);
    }

    @Test
    void replayReturnsOriginalSnapshotWithoutReadingCurrentStateOrRepeatingMutation() {
        var command = create("original");
        var original =
                new PaperAlertOperation(
                        id,
                        7,
                        "owner",
                        "ptr-one",
                        "CREATE",
                        command.fingerprint(7, "ptr-one"),
                        alert(PaperRunAlertStatus.OPEN),
                        Instant.now());
        when(operations.find(id)).thenReturn(Optional.of(original));
        assertEquals(original, service.submit(id, 7, "owner", "ptr-one", command, "trace"));
        verifyNoInteractions(runs, monitor);
        verify(operations, never()).complete(any(), any());
    }

    @Test
    void anotherOwnerOrDifferentFullInputCannotReuseOriginalUuid() {
        var command = create("original");
        when(operations.find(id))
                .thenReturn(
                        Optional.of(
                                new PaperAlertOperation(
                                        id,
                                        7,
                                        "owner",
                                        "ptr-one",
                                        "CREATE",
                                        command.fingerprint(7, "ptr-one"),
                                        alert(PaperRunAlertStatus.OPEN),
                                        Instant.now())));
        assertThrows(
                IllegalStateException.class,
                () -> service.submit(id, 8, "other", "ptr-one", command, "trace"));
        assertThrows(
                IllegalStateException.class,
                () -> service.submit(id, 7, "owner", "ptr-one", create("changed"), "trace"));
        assertThrows(
                IllegalStateException.class,
                () -> service.submit(id, 7, "owner", "ptr-other", command, "trace"));
        verifyNoInteractions(runs, monitor);
    }

    @Test
    void createsOneAlertAndRecordsPersistedPrecisionAndAuthenticatedActor() {
        var persisted = alert(PaperRunAlertStatus.OPEN);
        when(monitor.createAlert(any())).thenReturn(persisted);
        when(monitor.getAlert("ptr-one", "alt-one")).thenReturn(persisted);
        var result = service.submit(id, 7, "owner", "ptr-one", create("original"), "trace");
        assertEquals(persisted, result.result());
        assertEquals("owner", result.actor());
        assertEquals(0, result.completedAt().getNano() % 1000);
        verify(runs).getById("ptr-one");
        ArgumentCaptor<PaperAlertOperation> saved =
                ArgumentCaptor.forClass(PaperAlertOperation.class);
        verify(operations).complete(saved.capture(), eq("trace"));
        assertEquals(result, saved.getValue());
    }

    @Test
    void acknowledgementUsesOriginalAlertIdAndServerActor() {
        var command =
                new PaperAlertOperationCommand(
                        PaperAlertOperationCommand.Type.ACK,
                        "alt-one",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var changed = alert(PaperRunAlertStatus.ACKED);
        when(monitor.ackAlert("ptr-one", "alt-one", "owner")).thenReturn(changed);
        when(monitor.getAlert("ptr-one", "alt-one")).thenReturn(changed);
        assertEquals(
                PaperRunAlertStatus.ACKED,
                service.submit(id, 7, "owner", "ptr-one", command, "trace").result().status());
    }

    @Test
    void failedCompletionPropagatesInsteadOfClaimingSuccess() {
        var persisted = alert(PaperRunAlertStatus.OPEN);
        when(monitor.createAlert(any())).thenReturn(persisted);
        when(monitor.getAlert("ptr-one", "alt-one")).thenReturn(persisted);
        doThrow(new IllegalStateException("write failed")).when(operations).complete(any(), any());
        assertThrows(
                IllegalStateException.class,
                () -> service.submit(id, 7, "owner", "ptr-one", create("original"), "trace"));
    }

    @Test
    void resultReadRequiresOriginalOwnerAndExactParent() {
        when(operations.find(id))
                .thenReturn(
                        Optional.of(
                                new PaperAlertOperation(
                                        id,
                                        7,
                                        "owner",
                                        "ptr-one",
                                        "CREATE",
                                        "a".repeat(64),
                                        alert(PaperRunAlertStatus.OPEN),
                                        Instant.now())));
        assertFalse(service.find(id, 8, "ptr-one").isPresent());
        assertFalse(service.find(id, 7, "ptr-other").isPresent());
        assertEquals(
                PaperRunAlertStatus.OPEN,
                service.find(id, 7, "ptr-one").orElseThrow().result().status());
        verifyNoInteractions(runs, monitor);
    }
}
