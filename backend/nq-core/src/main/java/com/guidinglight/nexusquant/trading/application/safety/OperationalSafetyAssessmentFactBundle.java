package com.guidinglight.nexusquant.trading.application.safety;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewStateMachine;

import java.time.Instant;
import java.util.List;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GateW-4 assessment 的已观测、无敏感信息事实集合。
 *
 * <p>调用方只能提供既有 evidence 的只读投影；本模型不创建 approval state、不读取数据库，
 * 也不持久化 assessment result。Human-review 绑定固定为 case id/version/type/subject/reference、
 * lifecycle、完整 event chain、retention 与 observedAt。</p>
 */
public record OperationalSafetyAssessmentFactBundle(
        HumanReviewEvidence humanReviewEvidence,
        OperationalSafetyAssessmentStatus persistenceRetentionStatus,
        OperationalSafetyAssessmentStatus backupRestoreStatus,
        OperationalSafetyAssessmentStatus incidentDrillStatus,
        OperationalSafetyAssessmentStatus localSoakStatus,
        OperationalSafetyAssessmentStatus realReadonlySoakStatus,
        Set<OperationalSafetyAssessmentFindingCode> incidentFindings
) {

    public static final String HUMAN_REVIEW_EVIDENCE_TYPE = "GATEW4_OPERATIONAL_SAFETY";
    public static final String HUMAN_REVIEW_SUBJECT = "NQ-GATEW-4";

    /**
     * 校验各 hard-gate fact 非空，并固定 incident code 的 enum 顺序。
     */
    public OperationalSafetyAssessmentFactBundle {
        Objects.requireNonNull(humanReviewEvidence, "humanReviewEvidence must not be null");
        Objects.requireNonNull(persistenceRetentionStatus, "persistenceRetentionStatus must not be null");
        Objects.requireNonNull(backupRestoreStatus, "backupRestoreStatus must not be null");
        Objects.requireNonNull(incidentDrillStatus, "incidentDrillStatus must not be null");
        Objects.requireNonNull(localSoakStatus, "localSoakStatus must not be null");
        Objects.requireNonNull(realReadonlySoakStatus, "realReadonlySoakStatus must not be null");
        Objects.requireNonNull(incidentFindings, "incidentFindings must not be null");
        EnumSet<OperationalSafetyAssessmentFindingCode> ordered = incidentFindings.isEmpty()
                ? EnumSet.noneOf(OperationalSafetyAssessmentFindingCode.class)
                : EnumSet.copyOf(incidentFindings);
        incidentFindings = Collections.unmodifiableSet(ordered);
    }

    /**
     * Human-review evidence 只允许这四种存在性/新鲜度结论。
     */
    public enum HumanReviewEvidenceStatus {
        HUMAN_REVIEW_EVIDENCE_PRESENT,
        HUMAN_REVIEW_EVIDENCE_MISSING,
        HUMAN_REVIEW_EVIDENCE_STALE,
        HUMAN_REVIEW_EVIDENCE_CONFLICT
    }

    /**
     * 既有 {@code validation_review_cases/events} 的只读 evidence binding。
     *
     * <p>{@code MISSING} 只携带 observedAt；其余状态必须携带完整绑定字段。Lifecycle 仍只表达
     * 人工诊断复核，不得解释为 {@code TRADE_AUTHORIZED}、{@code LIVE_APPROVED}、
     * {@code ORDER_APPROVED} 或 {@code CAN_TRADE}。</p>
     */
    public record HumanReviewEvidence(
            UUID reviewCaseId,
            long caseVersion,
            String evidenceType,
            String evidenceSubject,
            String evidenceReference,
            ValidationReviewState lifecycleState,
            boolean eventChainComplete,
            Instant retentionUntil,
            Instant observedAt,
            HumanReviewEvidenceStatus status
    ) {

        public HumanReviewEvidence {
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            Objects.requireNonNull(status, "status must not be null");
            if (status == HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_MISSING) {
                if (reviewCaseId != null || caseVersion != 0 || evidenceType != null
                        || evidenceSubject != null || evidenceReference != null
                        || lifecycleState != null || eventChainComplete || retentionUntil != null) {
                    throw new IllegalArgumentException("missing human review evidence must not contain binding fields");
                }
            } else {
                Objects.requireNonNull(reviewCaseId, "reviewCaseId must not be null");
                if (caseVersion < 0) {
                    throw new IllegalArgumentException("caseVersion must not be negative");
                }
                evidenceType = requireText(evidenceType, "evidenceType");
                evidenceSubject = requireText(evidenceSubject, "evidenceSubject");
                evidenceReference = requireText(evidenceReference, "evidenceReference");
                Objects.requireNonNull(lifecycleState, "lifecycleState must not be null");
                Objects.requireNonNull(retentionUntil, "retentionUntil must not be null");
            }
        }

        /**
         * 构造不存在 evidence 的显式事实，避免用 null 或 Optional 混淆语义。
         */
        public static HumanReviewEvidence missing(Instant observedAt) {
            return new HumanReviewEvidence(
                    null, 0, null, null, null, null, false, null, observedAt,
                    HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_MISSING
            );
        }

        /**
         * 从 tenant-scoped repository 返回的 case 与稳定升序 events 推导 GateW-4 binding。
         *
         * <p>该方法不信任调用方提供的 completeness boolean：它逐项核对 case id、tenant、version、
         * from/to state、event 时间顺序和最终 lifecycle，并从 evidenceAnchor 读取 subject/reference。
         * 缺失、过期或冲突只返回封闭四态，不抛出原始 payload。</p>
         *
         * @param reviewCase SQL scope 内可见的 case；empty 明确表示缺失
         * @param events     repository 按 createdAt/id 升序返回的 append-only events
         * @param observedAt 本次 read-model 观测时间
         * @return 由 durable facts 推导的只读 evidence binding
         */
        public static HumanReviewEvidence bind(
                Optional<ValidationReviewCase> reviewCase,
                List<ValidationReviewEvent> events,
                Instant observedAt
        ) {
            Objects.requireNonNull(reviewCase, "reviewCase must not be null");
            Objects.requireNonNull(events, "events must not be null");
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            if (reviewCase.isEmpty()) {
                if (!events.isEmpty()) {
                    throw new IllegalArgumentException("events must be empty when review case is missing");
                }
                return missing(observedAt);
            }

            ValidationReviewCase current = reviewCase.orElseThrow();
            String subject = text(current.evidenceAnchor().get("subject"));
            String reference = text(current.evidenceAnchor().get("reference"));
            boolean chainComplete = completeChain(current, events, observedAt);
            HumanReviewEvidenceStatus derivedStatus;
            if (current.retentionUntil() == null || !current.retentionUntil().isAfter(observedAt)) {
                derivedStatus = HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_STALE;
            } else if (!HUMAN_REVIEW_EVIDENCE_TYPE.equals(current.evidenceType())
                    || !HUMAN_REVIEW_SUBJECT.equals(subject)
                    || reference == null
                    || !chainComplete
                    || current.state() != ValidationReviewState.RESOLVED
                    && current.state() != ValidationReviewState.CLOSED) {
                derivedStatus = HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_CONFLICT;
            } else {
                derivedStatus = HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_PRESENT;
            }
            return new HumanReviewEvidence(
                    current.id(),
                    current.version(),
                    current.evidenceType(),
                    subject == null ? "MISSING_SUBJECT" : subject,
                    reference == null ? "MISSING_REFERENCE" : reference,
                    current.state(),
                    chainComplete,
                    current.retentionUntil() == null ? observedAt : current.retentionUntil(),
                    observedAt,
                    derivedStatus
            );
        }

        private static boolean completeChain(
                ValidationReviewCase reviewCase,
                List<ValidationReviewEvent> events,
                Instant observedAt
        ) {
            if (reviewCase.version() != events.size()) {
                return false;
            }
            ValidationReviewState expectedFrom = ValidationReviewState.OPEN;
            ValidationReviewStateMachine stateMachine = new ValidationReviewStateMachine();
            Instant previousAt = reviewCase.createdAt();
            long expectedVersion = 1;
            for (ValidationReviewEvent event : events) {
                if (!reviewCase.id().equals(event.reviewCaseId())
                        || !reviewCase.tenantKey().equals(event.tenantKey())
                        || event.caseVersion() != expectedVersion
                        || event.fromState() != expectedFrom
                        || !stateMachine.canTransition(event.fromState(), event.toState())
                        || event.createdAt().isBefore(previousAt)
                        || event.createdAt().isAfter(observedAt)) {
                    return false;
                }
                expectedFrom = event.toState();
                previousAt = event.createdAt();
                expectedVersion++;
            }
            return expectedFrom == reviewCase.state() && previousAt.equals(reviewCase.updatedAt());
        }

        private static String text(com.fasterxml.jackson.databind.JsonNode node) {
            return node == null || !node.isTextual() || node.textValue().isBlank()
                    ? null
                    : node.textValue().trim();
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
