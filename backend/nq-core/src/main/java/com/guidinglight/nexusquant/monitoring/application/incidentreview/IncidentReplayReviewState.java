package com.guidinglight.nexusquant.monitoring.application.incidentreview;

/**
 * IncidentReplayReviewState 表示 GateT-3 派生复核条目的人工诊断状态。
 *
 * <p>这些状态只用于 read model 展示，不是持久化 review / acknowledge / escalation / closeout 状态，
 * 也不表示自动处置或交易授权。
 */
public enum IncidentReplayReviewState {
    INTAKE,
    EVIDENCE_REVIEW,
    NEEDS_OPERATOR_REVIEW,
    ACKNOWLEDGED_RECOMMENDATION,
    ESCALATED_RECOMMENDATION,
    CLOSED_RECOMMENDATION,
    BLOCKED
}
