package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExactPilotScopeAuthorizationCanonicalEncoderTest {

    private static final Instant START = Instant.parse("2026-08-22T10:00:00.000000Z");

    @Test
    void bindsExactOrderRuntimeAccountRiskWindowAndIndependentPrincipals() {
        Fixture fixture = fixture(ExactPilotBinding.Side.BUY, decimal("100"), decimal("0.1"));

        ExactPilotScopeAuthorization authorization = ExactPilotScopeAuthorization.approved(
                fixture.facts(), fixture.command(), 11L, 22L);

        assertTrue(authorization.hasCanonicalDigest());
        String canonical = ExactPilotScopeAuthorizationCanonicalEncoder.encode(authorization);
        assertTrue(canonical.contains("\"creatorPrincipal\":11"));
        assertTrue(canonical.contains("\"approverPrincipal\":22"));
        assertTrue(canonical.contains("\"orderType\":\"LIMIT\""));
        assertFalse(canonical.contains("scopeDigest"));
    }

    @Test
    void anyOrderOrApproverChangeInvalidatesOldAuthorization() {
        Fixture first = fixture(ExactPilotBinding.Side.BUY, decimal("100"), decimal("0.1"));
        ExactPilotScopeAuthorization approved = ExactPilotScopeAuthorization.approved(
                first.facts(), first.command(), 11L, 22L);
        ExactPilotBinding.OrderEnvelope changedOrder = new ExactPilotBinding.OrderEnvelope(
                first.command().order().instrumentId(), first.command().order().exchangeInstrumentId(),
                ExactPilotBinding.Side.SELL, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10"));
        ExactPilotBindingCommand changedCommand = new ExactPilotBindingCommand(
                first.command().bindingId(), first.command().sessionId(), first.command().pilotScopeId(),
                first.command().observationSetId(), changedOrder, first.command().pilotWindowStart(),
                first.command().pilotWindowEnd(), first.command().correlation(),
                first.command().bindingExpiresAt());
        ExactPilotBinding.AuthoritativeFacts changedFacts = new ExactPilotBinding.AuthoritativeFacts(
                first.facts().sessionId(), first.facts().pilotScopeId(), first.facts().observationSetId(),
                first.facts().deployment(), first.facts().account(), changedOrder, first.facts().observations(),
                first.facts().riskPolicy(), first.facts().pilotWindowStart(), first.facts().pilotWindowEnd());

        assertFalse(approved.matches(changedFacts, changedCommand, 11L, 22L));
        assertFalse(approved.matches(first.facts(), first.command(), 11L, 23L));
        assertNotEquals(approved.scopeDigest(), ExactPilotScopeAuthorization.approved(
                changedFacts, changedCommand, 11L, 22L).scopeDigest());
    }

    @Test
    void rejectsSelfApprovalAndCallerFactsThatDifferFromAuthority() {
        Fixture fixture = fixture(ExactPilotBinding.Side.BUY, decimal("100"), decimal("0.1"));

        assertThrows(IllegalArgumentException.class, () -> ExactPilotScopeAuthorization.approved(
                fixture.facts(), fixture.command(), 11L, 11L));

        ExactPilotBindingCommand wrongInstrument = new ExactPilotBindingCommand(
                fixture.command().bindingId(), fixture.command().sessionId(), fixture.command().pilotScopeId(),
                fixture.command().observationSetId(), new ExactPilotBinding.OrderEnvelope(
                202L, "ETH-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10")), fixture.command().pilotWindowStart(),
                fixture.command().pilotWindowEnd(), fixture.command().correlation(),
                fixture.command().bindingExpiresAt());
        assertThrows(IllegalArgumentException.class, () -> ExactPilotScopeAuthorization.approved(
                fixture.facts(), wrongInstrument, 11L, 22L));
    }

    private static Fixture fixture(
            ExactPilotBinding.Side side,
            BigDecimal price,
            BigDecimal quantity
    ) {
        UUID sessionId = UUID.randomUUID();
        UUID pilotScopeId = UUID.randomUUID();
        UUID observationSetId = UUID.randomUUID();
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", side, ExactPilotBinding.OrderType.LIMIT,
                price, quantity, price.multiply(quantity));
        ExactPilotBinding.AuthoritativeFacts facts = new ExactPilotBinding.AuthoritativeFacts(
                sessionId, pilotScopeId, observationSetId,
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", 11L, 21L, 31L), order,
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(
                        UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                START, START.plusSeconds(600));
        ExactPilotBindingCommand command = new ExactPilotBindingCommand(
                UUID.randomUUID(), sessionId, pilotScopeId, observationSetId, order,
                START, START.plusSeconds(600),
                new ExactPilotBinding.Correlation("request-1", "trace-1", "idempotency-1"),
                START.plusSeconds(300));
        return new Fixture(facts, command);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }

    private record Fixture(
            ExactPilotBinding.AuthoritativeFacts facts,
            ExactPilotBindingCommand command
    ) {
    }
}
