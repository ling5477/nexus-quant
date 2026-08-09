package com.guidinglight.nexusquant.trading.application.safety;

import java.util.List;
import java.util.Objects;

/**
 * GateW-4 operational safety 的只读诊断结果。
 *
 * <p>所有 boolean boundary 均固定为安全值；即使 {@code overallStatus=PASS}，也不产生交易授权。</p>
 */
public record OperationalSafetyAssessmentResult(
        OperationalSafetyAssessmentStatus killSwitchStatus,
        OperationalSafetyAssessmentStatus humanReviewEvidenceStatus,
        OperationalSafetyAssessmentStatus persistenceRetentionStatus,
        OperationalSafetyAssessmentStatus backupRestoreStatus,
        OperationalSafetyAssessmentStatus incidentDrillStatus,
        OperationalSafetyAssessmentStatus localSoakStatus,
        OperationalSafetyAssessmentStatus realReadonlySoakStatus,
        OperationalSafetyAssessmentStatus overallStatus,
        List<OperationalSafetyAssessmentFindingCode> blockers,
        List<OperationalSafetyAssessmentFindingCode> warnings,
        List<OperationalSafetyAssessmentFindingCode> unknowns,
        List<OperationalSafetyAssessmentFindingCode> notEvaluated,
        boolean diagnosticOnly,
        boolean readOnly,
        boolean noSideEffect,
        boolean orderSubmitted,
        boolean tradingAuthorized,
        boolean liveDisabled
) {

    /**
     * 校验不可变列表与固定安全边界，拒绝构造可授权或有副作用结果。
     */
    public OperationalSafetyAssessmentResult {
        Objects.requireNonNull(killSwitchStatus, "killSwitchStatus must not be null");
        Objects.requireNonNull(humanReviewEvidenceStatus, "humanReviewEvidenceStatus must not be null");
        Objects.requireNonNull(persistenceRetentionStatus, "persistenceRetentionStatus must not be null");
        Objects.requireNonNull(backupRestoreStatus, "backupRestoreStatus must not be null");
        Objects.requireNonNull(incidentDrillStatus, "incidentDrillStatus must not be null");
        Objects.requireNonNull(localSoakStatus, "localSoakStatus must not be null");
        Objects.requireNonNull(realReadonlySoakStatus, "realReadonlySoakStatus must not be null");
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        unknowns = List.copyOf(Objects.requireNonNull(unknowns, "unknowns must not be null"));
        notEvaluated = List.copyOf(Objects.requireNonNull(notEvaluated, "notEvaluated must not be null"));
        if (!diagnosticOnly || !readOnly || !noSideEffect || orderSubmitted || tradingAuthorized || !liveDisabled) {
            throw new IllegalArgumentException(
                    "Operational safety assessment result must preserve fixed no-side-effect safety boundaries"
            );
        }
    }
}
