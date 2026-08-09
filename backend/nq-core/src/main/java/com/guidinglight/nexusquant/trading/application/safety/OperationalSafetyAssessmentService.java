package com.guidinglight.nexusquant.trading.application.safety;

import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.BACKUP_RESTORE_NOT_PROVEN;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.HUMAN_REVIEW_EVIDENCE_CONFLICT;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.HUMAN_REVIEW_EVIDENCE_MISSING;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.HUMAN_REVIEW_EVIDENCE_STALE;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.INCIDENT_DRILL_INCOMPLETE;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.KILL_SWITCH_ENGAGED;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.KILL_SWITCH_STORAGE_FAILURE;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.KILL_SWITCH_UNKNOWN;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.LOCAL_SOAK_NOT_PROVEN;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.PERSISTENCE_RETENTION_NOT_PROVEN;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.REAL_READONLY_SOAK_CREDENTIAL_REQUIRED;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFindingCode.REAL_READONLY_SOAK_NOT_EVALUATED;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentStatus.BLOCKED;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentStatus.NOT_EVALUATED;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentStatus.PASS;
import static com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentStatus.UNKNOWN;

import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFactBundle.HumanReviewEvidence;
import com.guidinglight.nexusquant.trading.application.safety.OperationalSafetyAssessmentFactBundle.HumanReviewEvidenceStatus;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GateW-4 internal-only operational safety assessment。
 *
 * <p>本 service 是无状态纯函数：没有 Spring bean、repository、credential、network、scheduler、
 * order、ledger 或 mutable cache 依赖。它只对调用方提供的 immutable facts 做保守分类，线程安全且
 * 不产生 IO；ENGAGED 始终 BLOCKED，UNKNOWN 永不升级为 PASS。</p>
 */
public final class OperationalSafetyAssessmentService {

    /**
     * 评估一次 GateW-4 operational safety snapshot。
     *
     * @param request 受控时间下的 kill-switch 与 hard-gate facts
     * @return diagnostic-only、read-only、no-side-effect 结果
     */
    public OperationalSafetyAssessmentResult assess(OperationalSafetyAssessmentRequest request) {
        java.util.Objects.requireNonNull(request, "request must not be null");
        Set<OperationalSafetyAssessmentFindingCode> blockers = new LinkedHashSet<>();
        Set<OperationalSafetyAssessmentFindingCode> warnings = new LinkedHashSet<>();
        Set<OperationalSafetyAssessmentFindingCode> unknowns = new LinkedHashSet<>();
        Set<OperationalSafetyAssessmentFindingCode> notEvaluated = new LinkedHashSet<>();

        OperationalSafetyAssessmentStatus killSwitchStatus = evaluateKillSwitch(
                request.killSwitchSnapshot(), blockers, unknowns);
        OperationalSafetyAssessmentStatus humanReviewStatus = evaluateHumanReview(
                request.facts().humanReviewEvidence(), request.evaluatedAt(), blockers);

        classify(
                request.facts().persistenceRetentionStatus(),
                PERSISTENCE_RETENTION_NOT_PROVEN,
                blockers, unknowns, notEvaluated);
        classify(
                request.facts().backupRestoreStatus(),
                BACKUP_RESTORE_NOT_PROVEN,
                blockers, unknowns, notEvaluated);
        classify(
                request.facts().incidentDrillStatus(),
                INCIDENT_DRILL_INCOMPLETE,
                blockers, unknowns, notEvaluated);
        classify(
                request.facts().localSoakStatus(),
                LOCAL_SOAK_NOT_PROVEN,
                blockers, unknowns, notEvaluated);
        classify(
                request.facts().realReadonlySoakStatus(),
                REAL_READONLY_SOAK_NOT_EVALUATED,
                blockers, unknowns, notEvaluated);
        blockers.addAll(request.facts().incidentFindings());
        if (request.facts().realReadonlySoakStatus() == NOT_EVALUATED) {
            warnings.add(REAL_READONLY_SOAK_CREDENTIAL_REQUIRED);
        }

        OperationalSafetyAssessmentStatus overall = overall(
                killSwitchStatus,
                humanReviewStatus,
                request.facts().persistenceRetentionStatus(),
                request.facts().backupRestoreStatus(),
                request.facts().incidentDrillStatus(),
                request.facts().localSoakStatus(),
                request.facts().realReadonlySoakStatus()
        );
        return new OperationalSafetyAssessmentResult(
                killSwitchStatus,
                humanReviewStatus,
                request.facts().persistenceRetentionStatus(),
                request.facts().backupRestoreStatus(),
                request.facts().incidentDrillStatus(),
                request.facts().localSoakStatus(),
                request.facts().realReadonlySoakStatus(),
                overall,
                List.copyOf(blockers),
                List.copyOf(warnings),
                List.copyOf(unknowns),
                List.copyOf(notEvaluated),
                true,
                true,
                true,
                false,
                false,
                true
        );
    }

    private static OperationalSafetyAssessmentStatus evaluateKillSwitch(
            KillSwitchSnapshot snapshot,
            Set<OperationalSafetyAssessmentFindingCode> blockers,
            Set<OperationalSafetyAssessmentFindingCode> unknowns
    ) {
        if (snapshot.status() == KillSwitchStatus.ENGAGED) {
            blockers.add(KILL_SWITCH_ENGAGED);
            return BLOCKED;
        }
        if (snapshot.status() == KillSwitchStatus.UNKNOWN) {
            unknowns.add("KILL_SWITCH_STATE_READ_FAILED".equals(snapshot.reasonCode())
                    ? KILL_SWITCH_STORAGE_FAILURE
                    : KILL_SWITCH_UNKNOWN);
            return UNKNOWN;
        }
        return PASS;
    }

    private static OperationalSafetyAssessmentStatus evaluateHumanReview(
            HumanReviewEvidence evidence,
            java.time.Instant evaluatedAt,
            Set<OperationalSafetyAssessmentFindingCode> blockers
    ) {
        if (evidence.status() == HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_MISSING) {
            blockers.add(HUMAN_REVIEW_EVIDENCE_MISSING);
            return BLOCKED;
        }
        if (evidence.status() == HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_STALE
                || evidence.retentionUntil().isBefore(evaluatedAt)
                || evidence.observedAt().isAfter(evaluatedAt)) {
            blockers.add(HUMAN_REVIEW_EVIDENCE_STALE);
            return BLOCKED;
        }
        boolean validBinding = evidence.status() == HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_PRESENT
                && OperationalSafetyAssessmentFactBundle.HUMAN_REVIEW_EVIDENCE_TYPE.equals(evidence.evidenceType())
                && OperationalSafetyAssessmentFactBundle.HUMAN_REVIEW_SUBJECT.equals(evidence.evidenceSubject())
                && evidence.caseVersion() > 0
                && evidence.eventChainComplete()
                && (evidence.lifecycleState() == ValidationReviewState.RESOLVED
                || evidence.lifecycleState() == ValidationReviewState.CLOSED);
        if (!validBinding) {
            blockers.add(HUMAN_REVIEW_EVIDENCE_CONFLICT);
            return BLOCKED;
        }
        return PASS;
    }

    private static void classify(
            OperationalSafetyAssessmentStatus status,
            OperationalSafetyAssessmentFindingCode code,
            Set<OperationalSafetyAssessmentFindingCode> blockers,
            Set<OperationalSafetyAssessmentFindingCode> unknowns,
            Set<OperationalSafetyAssessmentFindingCode> notEvaluated
    ) {
        switch (status) {
            case BLOCKED -> blockers.add(code);
            case UNKNOWN -> unknowns.add(code);
            case NOT_EVALUATED -> notEvaluated.add(code);
            case PASS -> {
                // No finding is emitted for a proven hard gate.
            }
        }
    }

    private static OperationalSafetyAssessmentStatus overall(OperationalSafetyAssessmentStatus... statuses) {
        List<OperationalSafetyAssessmentStatus> values = new ArrayList<>(List.of(statuses));
        if (values.contains(BLOCKED)) {
            return BLOCKED;
        }
        if (values.contains(UNKNOWN)) {
            return UNKNOWN;
        }
        if (values.contains(NOT_EVALUATED)) {
            return NOT_EVALUATED;
        }
        return PASS;
    }
}
