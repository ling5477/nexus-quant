package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 在单一 DB decisionAt 上对已持久化 exact observation set 做 fail-closed 判断。 */
public final class PilotScopeFreshnessPolicy {

    public PilotScopePreflightResult evaluate(
            PilotScopeBinding scope,
            PilotObservationSet observations,
            BigDecimal requiredBalance,
            Instant decisionAt
    ) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(observations, "observations must not be null");
        requiredBalance = CanonicalDigestSupport.money(requiredBalance, "requiredBalance");
        Objects.requireNonNull(decisionAt, "decisionAt must not be null");
        List<PilotScopePreflightResult.Violation> violations = new ArrayList<>();

        addIfStale(observations.instrumentMetadata().envelope().observedAt(), scope.instrumentMaximumAgeMs(),
                decisionAt, PilotScopePreflightResult.Violation.INSTRUMENT_STALE, violations);
        addIfStale(observations.feeSchedule().envelope().observedAt(), scope.feeMaximumAgeMs(),
                decisionAt, PilotScopePreflightResult.Violation.FEE_STALE, violations);
        addIfStale(observations.balanceSnapshot().envelope().observedAt(), scope.balanceMaximumAgeMs(),
                decisionAt, PilotScopePreflightResult.Violation.BALANCE_STALE, violations);
        addIfStale(observations.clockSync().envelope().observedAt(), scope.clockMaximumAgeMs(),
                decisionAt, PilotScopePreflightResult.Violation.CLOCK_STALE, violations);
        addIfStale(observations.marketSnapshot().envelope().observedAt(), scope.instrumentMaximumAgeMs(),
                decisionAt, PilotScopePreflightResult.Violation.MARKET_STALE, violations);

        if (observations.instrumentMetadata().items().stream()
                .anyMatch(item -> item.tradingStatus() != PilotPrerequisiteObservation.TradingStatus.LIVE)) {
            violations.add(PilotScopePreflightResult.Violation.INSTRUMENT_NOT_LIVE);
        }
        if (observations.feeSchedule().feeEvidenceClass() != PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE) {
            violations.add(PilotScopePreflightResult.Violation.FEE_NOT_OBSERVED_PRIVATE);
        }
        if (observations.balanceSnapshot().availableBalance().compareTo(requiredBalance) < 0) {
            violations.add(PilotScopePreflightResult.Violation.BALANCE_INSUFFICIENT);
        }
        if (Math.abs(observations.clockSync().observedSkewMs()) > scope.maximumToleratedSkewMs()) {
            violations.add(PilotScopePreflightResult.Violation.CLOCK_SKEW_EXCEEDED);
        }
        if (!scope.id().equals(observations.pilotScopeId())
                || !scope.instrumentMetadataDigest().equals(observations.instrumentMetadata().instrumentMetadataDigest())
                || !scope.feeScheduleDigest().equals(observations.feeSchedule().feeScheduleDigest())
                || !scope.feeTier().equals(observations.feeSchedule().feeTier())
                || scope.feeEvidenceClass() != observations.feeSchedule().feeEvidenceClass()
                || !scope.signedTimestampSource().equals(observations.clockSync().signedTimestampSource())
                || observations.instrumentMetadata().items().stream()
                .noneMatch(item -> item.symbol().equals(observations.marketSnapshot().instrument()))) {
            violations.add(PilotScopePreflightResult.Violation.SCOPE_FACT_MISMATCH);
        }
        return new PilotScopePreflightResult(
                violations.isEmpty(), scope.id(), observations.id(), decisionAt, violations,
                observations.observations().stream().map(PilotPrerequisiteObservation::id).toList()
        );
    }

    private static void addIfStale(
            Instant observedAt,
            long maximumAgeMs,
            Instant decisionAt,
            PilotScopePreflightResult.Violation violation,
            List<PilotScopePreflightResult.Violation> violations
    ) {
        if (observedAt.plusMillis(maximumAgeMs).isBefore(decisionAt)) {
            violations.add(violation);
        }
    }
}
