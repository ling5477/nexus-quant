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
public record DiagnosticOrderRiskPreflightResult(
        Instant evaluatedAt,
        DiagnosticOrderRiskPreflightStatus structuralStatus,
        DiagnosticOrderRiskPreflightStatus venueFactStatus,
        DiagnosticOrderRiskPreflightStatus reconciliationStatus,
        DiagnosticOrderRiskPreflightStatus localAccountStatus,
        DiagnosticOrderRiskPreflightStatus credentialMetadataStatus,
        DiagnosticOrderRiskPreflightStatus marketdataQualityStatus,
        DiagnosticOrderRiskPreflightStatus pureRiskStatus,
        DiagnosticOrderRiskPreflightStatus statefulRiskStatus,
        DiagnosticOrderRiskPreflightStatus balanceStatus,
        DiagnosticOrderRiskPreflightStatus permissionStatus,
        DiagnosticOrderRiskPreflightStatus executionReadiness,
        boolean diagnosticOnly,
        boolean readOnly,
        boolean noSideEffect,
        boolean orderSubmitted,
        boolean tradingAuthorized,
        List<DiagnosticOrderRiskPreflightFindingCode> blockers,
        List<DiagnosticOrderRiskPreflightFindingCode> warnings,
        List<DiagnosticOrderRiskPreflightFindingCode> unknowns,
        List<DiagnosticOrderRiskPreflightFindingCode> notEvaluated
) {

    public DiagnosticOrderRiskPreflightResult {
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
        if (executionReadiness != DiagnosticOrderRiskPreflightStatus.BLOCKED) {
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

    private static void requireStatuses(DiagnosticOrderRiskPreflightStatus... statuses) {
        for (DiagnosticOrderRiskPreflightStatus status : statuses) {
            Objects.requireNonNull(status, "status must not be null");
        }
    }

    @SafeVarargs
    private static void requireDisjoint(List<DiagnosticOrderRiskPreflightFindingCode>... groups) {
        Set<DiagnosticOrderRiskPreflightFindingCode> seen = new HashSet<>();
        for (List<DiagnosticOrderRiskPreflightFindingCode> group : groups) {
            for (DiagnosticOrderRiskPreflightFindingCode code : group) {
                Objects.requireNonNull(code, "finding code must not be null");
                if (!seen.add(code)) {
                    throw new IllegalArgumentException("finding groups must be disjoint: " + code);
                }
            }
        }
    }
}
