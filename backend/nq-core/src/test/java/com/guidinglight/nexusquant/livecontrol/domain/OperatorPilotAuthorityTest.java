package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperatorPilotAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-08-25T08:00:00Z");

    @Test
    void materializesCanonicalAuthorityAndOperatorSessionWithoutStrategyFacts() {
        OperatorPilotAuthority authority = authority(new BigDecimal("10.00000000"));
        assertTrue(authority.hasCanonicalDigest());
        assertTrue(authority.activeAt(NOW.plusSeconds(1)));
        authority.requireScope(
                2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, new BigDecimal("9.99000000"), NOW.plusSeconds(1));

        LiveSession session = LiveSession.createOperatorPilot(
                UUID.randomUUID(), 2, 1, authority.id(), authority.canonicalDigest(), 2,
                "BTC-USDT", authority.maxNotional(), NOW, NOW.plusSeconds(120), 2, NOW);
        assertEquals(LiveSessionAuthorityType.OPERATOR_PILOT, session.authorityType());
        assertNull(session.strategyReleaseId());
        assertNull(session.riskLimitSetId());
        assertTrue(session.hasCanonicalApprovalScopeHash());
        assertEquals(LiveSession.OPERATOR_PILOT_APPROVAL_SCOPE_SCHEMA,
                session.approvalScopeSchemaVersion());
    }

    @Test
    void rejectsScopeExpansionExpiryAndFundingPermissions() {
        OperatorPilotAuthority authority = authority(new BigDecimal("10.00000000"));
        assertThrows(IllegalArgumentException.class, () -> authority.requireScope(
                2, 1, 2, "ETH-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> authority.requireScope(
                2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.SELL,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> authority.requireScope(
                2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.MARKET, BigDecimal.ONE, NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> authority.requireScope(
                2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, new BigDecimal("10.00000001"), NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> authority.requireScope(
                2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, NOW.plusSeconds(121)));
        assertThrows(IllegalArgumentException.class, () -> new OperatorPilotAuthority(
                UUID.randomUUID(), 2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, 1, 1,
                true, false, NOW, NOW.plusSeconds(120), OperatorPilotAuthority.Status.ACTIVE,
                2, NOW, "a".repeat(64)));
    }

    @Test
    void rejectsInvalidMaterializationInputsAndKeepsStrategySchemaStable() {
        assertThrows(IllegalArgumentException.class, () -> authority(new BigDecimal("10.00000001")));
        assertThrows(IllegalArgumentException.class, () -> OperatorPilotAuthority.active(
                UUID.randomUUID(), 2, 1, 2, "ETH-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, NOW, NOW.plusSeconds(120), 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> OperatorPilotAuthority.active(
                UUID.randomUUID(), 2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.SELL,
                OperatorPilotAuthority.OrderType.LIMIT, BigDecimal.ONE, NOW, NOW.plusSeconds(120), 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> OperatorPilotAuthority.active(
                UUID.randomUUID(), 2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.MARKET, BigDecimal.ONE, NOW, NOW.plusSeconds(120), 2, NOW));

        RiskLimitSet risk = new RiskLimitSet(
                UUID.randomUUID(), 1, new BigDecimal("25"), new BigDecimal("10"),
                new BigDecimal("25"), new BigDecimal("1"), new BigDecimal("2"), 1, 1,
                java.util.List.of("BTC-USDT"), 300, BigDecimal.ONE, BigDecimal.ONE,
                1000, 10000, 2, NOW);
        LiveSession strategy = LiveSession.create(
                UUID.randomUUID(), 2, 1, "release-1", "b".repeat(64), 1,
                risk.id(), risk.canonicalDigest(), 2, java.util.List.of("BTC-USDT"),
                new BigDecimal("10"), NOW, NOW.plusSeconds(120), 2, NOW);
        assertEquals(LiveSessionAuthorityType.STRATEGY, strategy.authorityType());
        assertEquals(LiveSession.APPROVAL_SCOPE_SCHEMA, strategy.approvalScopeSchemaVersion());
        assertFalse(LiveSessionApprovalScopeEncoder.encode(strategy).contains("operatorPilotAuthorityId"));
    }

    private static OperatorPilotAuthority authority(BigDecimal maxNotional) {
        return OperatorPilotAuthority.active(
                UUID.randomUUID(), 2, 1, 2, "BTC-USDT", OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, maxNotional,
                NOW, NOW.plusSeconds(120), 2, NOW);
    }
}
