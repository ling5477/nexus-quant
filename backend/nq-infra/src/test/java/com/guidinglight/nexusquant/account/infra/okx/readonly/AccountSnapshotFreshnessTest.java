package com.guidinglight.nexusquant.account.infra.okx.readonly;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountSnapshotFreshnessTest {
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    @Test
    void oldSourceEventDoesNotInventRefreshDeadline() {
        var snapshot = snapshot(NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(30)));
        assertEquals(AccountSnapshotFreshness.State.CURRENT_PROJECTION_VENUE_FRESHNESS_UNPROVEN,
                AccountSnapshotFreshness.evaluate(snapshot, NOW, null));
        assertEquals(AccountSnapshotFreshness.State.EXPLICIT_REFRESH_OVERDUE,
                AccountSnapshotFreshness.evaluate(snapshot, NOW, Duration.ofDays(1)));
    }

    @Test
    void futureAndMissingProvenanceFailClosed() {
        assertEquals(AccountSnapshotFreshness.State.INVALID_FUTURE_TIME,
                AccountSnapshotFreshness.evaluate(snapshot(NOW.plusSeconds(1), NOW), NOW, null));
        assertEquals(AccountSnapshotFreshness.State.INVALID_FUTURE_TIME,
                AccountSnapshotFreshness.evaluate(snapshot(NOW, NOW.plusSeconds(1)), NOW, null));
        assertEquals(AccountSnapshotFreshness.State.UNKNOWN_PROVENANCE,
                AccountSnapshotFreshness.evaluate(null, NOW, null));
    }

    private static JdbcAccountSnapshotReader.Snapshot snapshot(Instant source, Instant recorded) {
        return new JdbcAccountSnapshotReader.Snapshot(1, "BTC", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, source, recorded, "LIVE", "POSITION_PROJECTION", "NQ_MANAGED_ACCOUNT");
    }
}
