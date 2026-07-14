package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 只读诊断结果。所有安全布尔值与 executionReadiness 为不可配置常量，避免调用方升级语义。
 */
public record ReconciliationResult(
        List<ReconciliationFinding> matches,
        List<ReconciliationFinding> differences,
        List<ReconciliationFinding> blockers,
        List<ReconciliationFinding> warnings,
        List<ReconciliationFinding> unknowns,
        List<ReconciliationFinding> notEvaluated,
        Instant evaluatedAt,
        String snapshotAssessment
) {
    public ReconciliationResult {
        matches = List.copyOf(matches == null ? List.of() : matches);
        differences = List.copyOf(differences == null ? List.of() : differences);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        unknowns = List.copyOf(unknowns == null ? List.of() : unknowns);
        notEvaluated = List.copyOf(notEvaluated == null ? List.of() : notEvaluated);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        snapshotAssessment = Objects.requireNonNull(snapshotAssessment, "snapshotAssessment must not be null");
    }

    public boolean diagnosticOnly() { return true; }
    public boolean readOnly() { return true; }
    public boolean noSideEffect() { return true; }
    public boolean repairPerformed() { return false; }
    public boolean orderSubmitted() { return false; }
    public String executionReadiness() { return "BLOCKED"; }
}
