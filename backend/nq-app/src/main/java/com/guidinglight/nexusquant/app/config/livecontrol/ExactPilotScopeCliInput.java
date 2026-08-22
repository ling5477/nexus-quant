package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingDraft;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeAuthorizationCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Root/operator JSON input；closed schema 只包含 non-secret exact scope values。 */
public record ExactPilotScopeCliInput(
        long creatorPrincipal,
        long approverPrincipal,
        PilotScopeInput pilotScope,
        PilotApprovalInput pilotApproval,
        BindingInput binding,
        ScopeApprovalInput exactScopeApproval
) {
    public ExactPilotScopeCliInput {
        if (creatorPrincipal <= 0 || approverPrincipal <= 0 || creatorPrincipal == approverPrincipal) {
            throw new IllegalArgumentException("independent creator and approver principals are required");
        }
        Objects.requireNonNull(pilotScope, "pilotScope must not be null");
        Objects.requireNonNull(pilotApproval, "pilotApproval must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(exactScopeApproval, "exactScopeApproval must not be null");
    }

    public ExactPilotScopeControlCommand toCommand() {
        PilotScopeMaterializationCommand materialization = pilotScope.toCommand();
        return new ExactPilotScopeControlCommand(
                materialization, pilotApproval.toCommand(materialization.sessionId()),
                binding.toDraft(), exactScopeApproval.toCommand());
    }

    public record PilotScopeInput(
            UUID sessionId,
            UUID pilotScopeId,
            long exchangeAccountId,
            long credentialReferenceId,
            String strategyReleaseId,
            String releaseDigest,
            long releaseAdmissionRevision,
            RiskInput risk,
            List<String> symbolAllowlist,
            BigDecimal capitalCap,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            String expectedPilotScopeHash,
            ExactPilotBinding.Correlation correlation
    ) {
        private PilotScopeMaterializationCommand toCommand() {
            Objects.requireNonNull(risk, "risk must not be null");
            Objects.requireNonNull(correlation, "pilot scope correlation must not be null");
            return new PilotScopeMaterializationCommand(
                    sessionId, pilotScopeId, exchangeAccountId, credentialReferenceId,
                    strategyReleaseId, releaseDigest, releaseAdmissionRevision, risk.toCommand(),
                    symbolAllowlist, capitalCap, pilotWindowStart, pilotWindowEnd, expectedPilotScopeHash,
                    correlation.idempotencyKey(), correlation.requestId(), correlation.traceId());
        }
    }

    public record RiskInput(
            UUID riskPolicyId,
            String riskPolicyDigest,
            int version,
            BigDecimal capitalCap,
            BigDecimal maxOrderNotional,
            BigDecimal maxSymbolPositionNotional,
            BigDecimal maxDailyRealizedLoss,
            BigDecimal maxDailyTotalLoss,
            int maxOpenOrders,
            int maxIntradayOrders,
            List<String> symbolAllowlist,
            int maxSessionDurationSeconds,
            BigDecimal spreadLimitBps,
            BigDecimal slippageLimitBps,
            int maxMarketDataAgeMs,
            int minDataCoverageBps
    ) {
        private PilotScopeMaterializationCommand.RiskSelection toCommand() {
            return new PilotScopeMaterializationCommand.RiskSelection(
                    riskPolicyId, riskPolicyDigest, version, capitalCap, maxOrderNotional,
                    maxSymbolPositionNotional, maxDailyRealizedLoss, maxDailyTotalLoss,
                    maxOpenOrders, maxIntradayOrders, symbolAllowlist, maxSessionDurationSeconds,
                    spreadLimitBps, slippageLimitBps, maxMarketDataAgeMs, minDataCoverageBps);
        }
    }

    public record PilotApprovalInput(
            UUID approvalId,
            UUID pilotScopeId,
            String expectedPilotScopeHash,
            String reason,
            Instant approvedAt,
            Instant expiresAt
    ) {
        private PilotScopeApprovalCommand toCommand(UUID sessionId) {
            return new PilotScopeApprovalCommand(
                    approvalId, sessionId, pilotScopeId, expectedPilotScopeHash,
                    reason, approvedAt, expiresAt);
        }
    }

    public record BindingInput(
            UUID bindingId,
            long instrumentId,
            String exchangeInstrumentId,
            ExactPilotBinding.Side side,
            ExactPilotBinding.OrderType orderType,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal notional,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            ExactPilotBinding.Correlation correlation,
            Instant bindingExpiresAt
    ) {
        private ExactPilotBindingDraft toDraft() {
            return new ExactPilotBindingDraft(
                    bindingId, new ExactPilotBinding.OrderEnvelope(
                    instrumentId, exchangeInstrumentId, side, orderType, price, quantity, notional),
                    pilotWindowStart, pilotWindowEnd, correlation, bindingExpiresAt);
        }
    }

    public record ScopeApprovalInput(
            ExactPilotBinding.Correlation creatorCorrelation,
            ExactPilotBinding.Correlation approverCorrelation,
            String reason,
            Instant approvedAt,
            Instant expiresAt
    ) {
        private ExactPilotScopeAuthorizationCommand toCommand() {
            return new ExactPilotScopeAuthorizationCommand(
                    creatorCorrelation, approverCorrelation, reason, approvedAt, expiresAt);
        }
    }
}
