package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExactPilotBindingCanonicalEncoderTest {

    private static final Instant CREATED = Instant.parse("2026-08-22T01:02:03.123456Z");

    @Test
    void canonicalizesEveryExactFactWithStableFieldOrderAndUtf8Digest() {
        ExactPilotBinding first = binding(new BigDecimal("100"), new BigDecimal("0.1"));
        ExactPilotBinding second = bindingWithIdentities(
                first, new BigDecimal("100.00000000"), new BigDecimal("0.10000000"));

        String canonical = ExactPilotBindingCanonicalEncoder.encode(first);

        assertEquals(canonical, ExactPilotBindingCanonicalEncoder.encode(second));
        assertEquals(first.bindingDigest(), second.bindingDigest());
        assertTrue(first.hasCanonicalDigest());
        assertTrue(canonical.indexOf("\"sourceCommit\"") < canonical.indexOf("\"serverIdentity\""));
        assertTrue(canonical.indexOf("\"ownerId\"") < canonical.indexOf("\"instrumentId\""));
        assertTrue(canonical.indexOf("\"price\"") < canonical.indexOf("\"instrumentSnapshotIdentity\""));
        assertTrue(canonical.indexOf("\"riskPolicyVersion\"") < canonical.indexOf("\"pilotWindowStart\""));
        assertTrue(canonical.endsWith("\"bindingExpiresAt\":\"2026-08-22T01:07:03.123456Z\"}"));
        assertTrue(canonical.contains("\"price\":\"100.00000000\""));
        assertTrue(canonical.contains("\"quantity\":\"0.10000000\""));
        assertFalse(canonical.contains("bindingDigest"));
    }

    @Test
    void rejectsTamperWildcardsNonCanonicalTimeAndLongLivedBinding() {
        ExactPilotBinding valid = binding(new BigDecimal("100"), new BigDecimal("0.1"));
        ExactPilotBinding tampered = new ExactPilotBinding(
                valid.id(), valid.sessionId(), valid.pilotScopeId(), valid.observationSetId(),
                valid.deployment(), valid.account(), valid.order(), valid.observations(), valid.riskPolicy(),
                valid.pilotWindowStart(), valid.pilotWindowEnd(), valid.correlation(), valid.bindingCreatedAt(),
                valid.bindingExpiresAt(), "f".repeat(64));
        assertFalse(tampered.hasCanonicalDigest());

        assertThrows(IllegalArgumentException.class, () -> new ExactPilotBinding.DeploymentIdentity(
                "1".repeat(40), "1".repeat(40), "a".repeat(64), "latest",
                ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE));
        assertThrows(IllegalArgumentException.class, () -> ExactPilotBinding.verified(
                valid.id(), valid.sessionId(), valid.pilotScopeId(), valid.observationSetId(),
                valid.deployment(), valid.account(), valid.order(), valid.observations(), valid.riskPolicy(),
                valid.pilotWindowStart(), valid.pilotWindowEnd(), valid.correlation(),
                CREATED.plusNanos(1), valid.bindingExpiresAt()));
        assertThrows(IllegalArgumentException.class, () -> ExactPilotBinding.verified(
                valid.id(), valid.sessionId(), valid.pilotScopeId(), valid.observationSetId(),
                valid.deployment(), valid.account(), valid.order(), valid.observations(), valid.riskPolicy(),
                valid.pilotWindowStart(), CREATED.plusSeconds(901), valid.correlation(), CREATED,
                CREATED.plusSeconds(901)));
    }

    @Test
    void lifecycleAndOrderTypeCannotRepresentAuthorizationOrFallback() {
        assertEquals(List.of(ExactPilotBinding.OrderType.LIMIT),
                Arrays.asList(ExactPilotBinding.OrderType.values()));
        assertEquals(List.of(
                        ExactPilotBinding.Lifecycle.VERIFIED,
                        ExactPilotBinding.Lifecycle.CONSUMED,
                        ExactPilotBinding.Lifecycle.EXPIRED,
                        ExactPilotBinding.Lifecycle.INVALID),
                Arrays.asList(ExactPilotBinding.Lifecycle.values()));
    }

    private static ExactPilotBinding binding(BigDecimal price, BigDecimal quantity) {
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                price, quantity, price.multiply(quantity));
        return ExactPilotBinding.verified(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", 11L, 21L, 31L), order,
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(
                        UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                CREATED.minusSeconds(60), CREATED.plusSeconds(600),
                new ExactPilotBinding.Correlation("request-一", "trace-1", "idempotency-1"),
                CREATED, CREATED.plusSeconds(300));
    }

    private static ExactPilotBinding bindingWithIdentities(
            ExactPilotBinding source,
            BigDecimal price,
            BigDecimal quantity
    ) {
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                source.order().instrumentId(), source.order().exchangeInstrumentId(), source.order().side(),
                source.order().orderType(), price, quantity, price.multiply(quantity));
        return ExactPilotBinding.verified(
                source.id(), source.sessionId(), source.pilotScopeId(), source.observationSetId(),
                source.deployment(), source.account(), order, source.observations(), source.riskPolicy(),
                source.pilotWindowStart(), source.pilotWindowEnd(), source.correlation(),
                source.bindingCreatedAt(), source.bindingExpiresAt());
    }
}
