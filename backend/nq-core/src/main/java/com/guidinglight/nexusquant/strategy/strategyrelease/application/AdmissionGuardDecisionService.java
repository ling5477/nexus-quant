package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.util.Objects;

import org.springframework.stereotype.Service;

/** GET issuance 与 locked writer 共用的 canonical guarded-materialization decision。 */
@Service
public class AdmissionGuardDecisionService {

    private final StrategyValidationOverviewQueryService validationQueryService;

    public AdmissionGuardDecisionService(StrategyValidationOverviewQueryService validationQueryService) {
        this.validationQueryService = Objects.requireNonNull(
                validationQueryService,
                "validationQueryService must not be null"
        );
    }

    public ReleaseToShadowAdmissionDecision.Decision evaluate(StrategyReleaseAdmissionPreviewFacts facts) {
        if (facts == null
                || facts.backtestRunId() == null
                || facts.backtestRunId().isBlank()
                || facts.validationFact() == null
                || facts.validationFact().strategyVersionId() == null
                || facts.validationFact().datasetId() == null
                || facts.validationFact().evaluationReportId() == null
                || facts.validationFact().publishId() == null
                || facts.windowStart() == null
                || facts.windowEnd() == null
                || !facts.windowEnd().isAfter(facts.windowStart())
                || (facts.authorizationBoundary() != ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                && facts.authorizationBoundary() != ShadowRunAuthorizationBoundary.REVIEW_ONLY)
                || facts.sideEffectPolicy() == null
                || !facts.sideEffectPolicy().allNoSideEffects()) {
            return ReleaseToShadowAdmissionDecision.Decision.BLOCKED;
        }
        StrategyValidationDecision validation = validationQueryService.evaluateDecision(facts.validationFact());
        return validation == StrategyValidationDecision.APPROVED
                ? ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE
                : ReleaseToShadowAdmissionDecision.Decision.BLOCKED;
    }
}
