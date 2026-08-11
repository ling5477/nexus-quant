package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.time.Instant;

/**
 * Release admission preview 所需的服务端只读事实。
 *
 * <p>validation 与 window 来自 publish anchor 绑定的本地持久化事实；authorization boundary 与
 * no-side-effect policy 来自既有 Shadow production invariant。客户端不能提交或覆盖这些字段。
 */
public record StrategyReleaseAdmissionPreviewFacts(
        LatestDecisionFact validationFact,
        Instant windowStart,
        Instant windowEnd,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
) {

    /** repository 失败或事实缺失时的 fail-closed 空快照。 */
    public static StrategyReleaseAdmissionPreviewFacts missing() {
        return new StrategyReleaseAdmissionPreviewFacts(null, null, null, null, null);
    }
}
