package com.guidinglight.nexusquant.trading.application.riskpreflight;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GateW-3 internal diagnostic risk preflight 结果。
 *
 * <p>结果强制 executionReadiness=BLOCKED、tradingAuthorized=false，且四类 finding 互斥。
 * UNKNOWN/NOT_EVALUATED 永远不能被调用方折叠为交易就绪。</p>
 */
public record GateW3RiskPreflightResult(
        Instant evaluatedAt,
        GateW3RiskPreflightStatus structuralStatus,
        GateW3RiskPreflightStatus venueFactStatus,
        GateW3RiskPreflightStatus reconciliationStatus,
        GateW3RiskPreflightStatus localAccountStatus,
        GateW3RiskPreflightStatus credentialMetadataStatus,
        GateW3RiskPreflightStatus marketdataQualityStatus,
        GateW3RiskPreflightStatus pureRiskStatus,
        GateW3RiskPreflightStatus statefulRiskStatus,
        GateW3RiskPreflightStatus balanceStatus,
        GateW3RiskPreflightStatus permissionStatus,
        GateW3RiskPreflightStatus executionReadiness,
        boolean diagnosticOnly,
        boolean readOnly,
        boolean noSideEffect,
        boolean orderSubmitted,
        boolean tradingAuthorized,
        List<GateW3RiskPreflightFindingCode> blockers,
        List<GateW3RiskPreflightFindingCode> warnings,
        List<GateW3RiskPreflightFindingCode> unknowns,
        List<GateW3RiskPreflightFindingCode> notEvaluated
) {

    public GateW3RiskPreflightResult {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        requireStatuses(
                structuralStatus,
                venueFactStatus,
                reconciliationStatus,
                localAccountStatus,
                credentialMetadataStatus,
                marketdataQualityStatus,
                pureRiskStatus,
                statefulRiskStatus,
                balanceStatus,
                permissionStatus,
                executionReadiness
        );
        if (executionReadiness != GateW3RiskPreflightStatus.BLOCKED) {
            throw new IllegalArgumentException("executionReadiness must remain BLOCKED");
        }
        if (!diagnosticOnly || !readOnly || !noSideEffect || orderSubmitted || tradingAuthorized) {
            throw new IllegalArgumentException("risk preflight safety flags are immutable");
        }
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        unknowns = List.copyOf(unknowns == null ? List.of() : unknowns);
        notEvaluated = List.copyOf(notEvaluated == null ? List.of() : notEvaluated);
        requireDisjoint(blockers, warnings, unknowns, notEvaluated);
    }

    private static void requireStatuses(GateW3RiskPreflightStatus... statuses) {
        for (GateW3RiskPreflightStatus status : statuses) {
            Objects.requireNonNull(status, "status must not be null");
        }
    }

    @SafeVarargs
    private static void requireDisjoint(List<GateW3RiskPreflightFindingCode>... groups) {
        Set<GateW3RiskPreflightFindingCode> seen = new HashSet<>();
        for (List<GateW3RiskPreflightFindingCode> group : groups) {
            for (GateW3RiskPreflightFindingCode code : group) {
                Objects.requireNonNull(code, "finding code must not be null");
                if (!seen.add(code)) {
                    throw new IllegalArgumentException("finding groups must be disjoint: " + code);
                }
            }
        }
    }
}
