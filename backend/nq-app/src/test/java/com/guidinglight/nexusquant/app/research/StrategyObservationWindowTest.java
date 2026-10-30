package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StrategyObservationWindowTest {
    private static final Instant NOW = Instant.parse("2026-10-30T12:30:00Z");

    @Test void retrospectiveRequiresAlreadyOccurredWholeHourWindow() {
        assertDoesNotThrow(() -> StrategyObservationService.validateWindow(
                request("RETROSPECTIVE", "2026-10-29T12:00:00Z", "2026-10-30T12:00:00Z"), NOW));
        assertThrows(ResponseStatusException.class, () -> StrategyObservationService.validateWindow(
                request("RETROSPECTIVE", "2026-10-29T13:00:00Z", "2026-10-30T13:00:00Z"), NOW));
        assertThrows(ResponseStatusException.class, () -> StrategyObservationService.validateWindow(
                request("RETROSPECTIVE", "2026-10-29T12:00:01Z", "2026-10-30T12:00:01Z"), NOW));
    }

    @Test void forwardRegistrationCannotBackdateTheObservation() {
        assertDoesNotThrow(() -> StrategyObservationService.validateWindow(
                request("FORWARD", "2026-10-30T13:00:00Z", "2026-10-31T13:00:00Z"), NOW));
        assertThrows(ResponseStatusException.class, () -> StrategyObservationService.validateWindow(
                request("FORWARD", "2026-10-30T12:00:00Z", "2026-10-31T12:00:00Z"), NOW));
    }

    @Test void durationHasBothLowerAndUpperBounds() {
        assertDoesNotThrow(() -> StrategyObservationService.validateWindow(
                request("RETROSPECTIVE", "2026-08-01T12:00:00Z", "2026-10-30T12:00:00Z"), NOW));
        for (String start : new String[]{"2026-07-31T12:00:00Z", "2026-10-29T13:00:00Z", "2026-10-30T13:00:00Z"})
            assertThrows(ResponseStatusException.class, () -> StrategyObservationService.validateWindow(
                    request("RETROSPECTIVE", start, "2026-10-30T12:00:00Z"), NOW));
    }

    private static StrategyObservationService.CreateRequest request(String kind, String from, String to) {
        return new StrategyObservationService.CreateRequest("ptr-test", 1, "sv-test", "pub-test",
                UUID.randomUUID(), UUID.randomUUID(), kind, Instant.parse(from), Instant.parse(to), null);
    }
}
