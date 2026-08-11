package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.time.Instant;
import java.util.UUID;

/**
 * Release admission preview 所需的服务端只读事实。
 *
 * <p>validation 与 window 来自 publish anchor 绑定的本地持久化事实；authorization boundary 与
 * no-side-effect policy 来自既有 Shadow production invariant。客户端不能提交或覆盖这些字段。
 */
public record StrategyReleaseAdmissionPreviewFacts(
        String backtestRunId,
        LatestDecisionFact validationFact,
        Instant windowStart,
        Instant windowEnd,
        PaperEvidenceIdentity latestPaperIdentity,
        ShadowEvidenceIdentity latestShadowEvidenceIdentity,
        ConsistencyEvidenceIdentity latestConsistencyIdentity,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
) {

    /** 兼容既有单元测试构造；production JDBC 必须提供完整 evidence identity。 */
    public StrategyReleaseAdmissionPreviewFacts(
            LatestDecisionFact validationFact,
            Instant windowStart,
            Instant windowEnd,
            ShadowRunAuthorizationBoundary authorizationBoundary,
            ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
    ) {
        this(
                null,
                validationFact,
                windowStart,
                windowEnd,
                validationFact == null || validationFact.paperRunId() == null
                        ? null
                        : new PaperEvidenceIdentity(
                                validationFact.paperRunId(),
                                validationFact.paperRunStatus(),
                                validationFact.paperTradeEnv(),
                                validationFact.evidenceUpdatedAt()
                        ),
                validationFact == null || validationFact.shadowRunId() == null
                        ? null
                        : new ShadowEvidenceIdentity(
                                validationFact.shadowRunId(),
                                validationFact.shadowRunStatus(),
                                validationFact.evidenceUpdatedAt()
                        ),
                null,
                authorizationBoundary,
                sideEffectPolicy
        );
    }

    /** repository 失败或事实缺失时的 fail-closed 空快照。 */
    public static StrategyReleaseAdmissionPreviewFacts missing() {
        return new StrategyReleaseAdmissionPreviewFacts(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /** 最新 SIM Paper admission evidence identity；NULL 与空字符串在 fingerprint 中严格区分。 */
    public record PaperEvidenceIdentity(
            String paperRunId,
            String status,
            String tradeEnvironment,
            Instant updatedAt
    ) {
    }

    /** 最新 evidence-bearing Shadow identity；CREATED 不得进入该模型。 */
    public record ShadowEvidenceIdentity(UUID shadowRunId, String status, Instant updatedAt) {
    }

    /** 最新 Shadow consistency evidence identity。 */
    public record ConsistencyEvidenceIdentity(
            UUID consistencyReportId,
            UUID shadowRunId,
            String status,
            Instant generatedAt
    ) {
    }
}
