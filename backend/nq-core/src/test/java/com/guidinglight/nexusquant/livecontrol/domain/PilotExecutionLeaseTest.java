package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PilotExecutionLeaseTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    void createdLeaseBindsCanonicalBindingAndHardNotional() {
        ExactPilotBinding binding = binding();
        PilotExecutionLease lease = PilotExecutionLease.created(
                UUID.randomUUID(), binding, new BigDecimal("10.00000000"), 11,
                NOW, NOW.plusSeconds(120));

        assertTrue(lease.bindingDigest().equals(binding.bindingDigest()));
        assertFalse(lease.activeAt(NOW));
    }

    @Test
    void rejectsOverCapExpiredAndOverlongLease() {
        ExactPilotBinding binding = binding();
        assertThrows(IllegalArgumentException.class, () -> PilotExecutionLease.created(
                UUID.randomUUID(), binding, new BigDecimal("9.99999999"), 11,
                NOW, NOW.plusSeconds(120)));
        assertThrows(IllegalArgumentException.class, () -> PilotExecutionLease.created(
                UUID.randomUUID(), binding, new BigDecimal("10.00000000"), 11,
                NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> PilotExecutionLease.created(
                UUID.randomUUID(), binding, new BigDecimal("10.00000000"), 11,
                NOW, NOW.plusSeconds(301)));
    }

    @Test
    void regeneratedLeaseAcceptsDatabaseDerivedPositiveOrdinalWithoutTaskSpecificCeiling() {
        ExactPilotBinding binding = binding();
        PilotExecutionLease lease = PilotExecutionLease.createdReplacement(
                UUID.randomUUID(), binding, new BigDecimal("10.00000000"), 11,
                NOW, NOW.plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 37);

        assertEquals(37, lease.replacementOrdinal());
        assertEquals(PilotExecutionLease.REGENERATION_REASON, lease.replacementReason());
        assertThrows(IllegalArgumentException.class, () -> PilotExecutionLease.createdReplacement(
                UUID.randomUUID(), binding, new BigDecimal("10.00000000"), 11,
                NOW, NOW.plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 0));
    }

    private static ExactPilotBinding binding() {
        var order = new ExactPilotBinding.OrderEnvelope(
                1, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                new BigDecimal("100.00000000"), new BigDecimal("0.10000000"),
                new BigDecimal("10.00000000"));
        return ExactPilotBinding.verified(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", 11, 21, 31), order,
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "c".repeat(64)),
                new ExactPilotBinding.RiskPolicyIdentity(UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                NOW.minusSeconds(1), NOW.plusSeconds(180),
                new ExactPilotBinding.Correlation("request", "trace", "idempotency"),
                NOW, NOW.plusSeconds(120));
    }
}
