package com.guidinglight.nexusquant.monitoring.application.incidentreview.model;

/**
 * IncidentReplayReviewDecision 表示验证工作流只读复核建议。
 *
 * <p>所有取值都是人工复核建议：ACKNOWLEDGE / ESCALATE / CLOSEOUT 不是系统写侧动作，
 * 不是自动处置，也不是 incident 真实关闭。
 */
public enum IncidentReplayReviewDecision {
    NO_DECISION,
    REVIEW_NEEDED,
    ACKNOWLEDGE_RECOMMENDED,
    ESCALATE_RECOMMENDED,
    CLOSEOUT_RECOMMENDED,
    BLOCKED,
    STALE_EVIDENCE
}
