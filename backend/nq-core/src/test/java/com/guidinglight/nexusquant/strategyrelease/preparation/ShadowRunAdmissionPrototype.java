package com.guidinglight.nexusquant.strategyrelease.preparation;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Release-to-Shadow admission 的纯结果合同。
 *
 * <p>该结果只允许描述是否能生成未来 GateX 的创建计划；它不创建 Shadow Run，也不表示交易、
 * LIVE 或任何执行授权。
 */
record ShadowRunAdmissionPrototype(
        ShadowRunAdmissionStatus status,
        List<ShadowRunAdmissionFindingCode> blockers,
        List<ShadowRunAdmissionFindingCode> unknowns,
        List<ShadowRunAdmissionFindingCode> warnings,
        ShadowRunCreationPlanPrototype creationPlan,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean shadowRunCreated,
        boolean shadowRunStarted,
        boolean orderSubmitted
) {

    ShadowRunAdmissionPrototype {
        Objects.requireNonNull(status, "status must not be null");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (!diagnosticOnly
                || !noSideEffect
                || !notTradingAuthorization
                || !liveDisabled
                || shadowRunCreated
                || shadowRunStarted
                || orderSubmitted) {
            throw new IllegalArgumentException("admission safety boundary must remain fixed");
        }
        if (status == ShadowRunAdmissionStatus.ADMITTED) {
            if (creationPlan == null || !blockers.isEmpty() || !unknowns.isEmpty()) {
                throw new IllegalArgumentException("admitted result requires only a creation plan");
            }
        } else if (creationPlan != null) {
            throw new IllegalArgumentException("blocked or unknown result must not contain a creation plan");
        }
    }

    static ShadowRunAdmissionPrototype admitted(ShadowRunCreationPlanPrototype creationPlan) {
        return new ShadowRunAdmissionPrototype(
                ShadowRunAdmissionStatus.ADMITTED,
                List.of(),
                List.of(),
                List.of(ShadowRunAdmissionFindingCode.ADMISSION_NOT_TRADING_AUTHORIZATION),
                creationPlan,
                true,
                true,
                true,
                true,
                false,
                false,
                false
        );
    }

    static ShadowRunAdmissionPrototype blocked(
            List<ShadowRunAdmissionFindingCode> blockers,
            List<ShadowRunAdmissionFindingCode> unknowns
    ) {
        return nonAdmitted(ShadowRunAdmissionStatus.BLOCKED, blockers, unknowns);
    }

    static ShadowRunAdmissionPrototype unknown(List<ShadowRunAdmissionFindingCode> unknowns) {
        return nonAdmitted(ShadowRunAdmissionStatus.UNKNOWN, List.of(), unknowns);
    }

    private static ShadowRunAdmissionPrototype nonAdmitted(
            ShadowRunAdmissionStatus status,
            List<ShadowRunAdmissionFindingCode> blockers,
            List<ShadowRunAdmissionFindingCode> unknowns
    ) {
        return new ShadowRunAdmissionPrototype(
                status,
                blockers,
                unknowns,
                List.of(ShadowRunAdmissionFindingCode.ADMISSION_NOT_TRADING_AUTHORIZATION),
                null,
                true,
                true,
                true,
                true,
                false,
                false,
                false
        );
    }
}

/** Admission 结果仅有三种；UNKNOWN 与 BLOCKED 都不得创建任何本地事实。 */
enum ShadowRunAdmissionStatus {
    ADMITTED,
    BLOCKED,
    UNKNOWN
}

/** 只保存脱敏 reason code，按 blockers、unknowns 与 warnings 分栏返回。 */
enum ShadowRunAdmissionFindingCode {
    RELEASE_NOT_PUBLISHED,
    RELEASE_BINDING_NOT_COMPLETE,
    PUBLISH_ANCHOR_MISMATCH,
    ARTIFACT_VERIFICATION_UNKNOWN,
    ARTIFACT_VERIFICATION_REJECTED,
    ARTIFACT_DIGEST_MISMATCH,
    VALIDATION_NOT_APPROVED,
    VALIDATION_EVIDENCE_MISSING,
    VALIDATION_EVIDENCE_STALE,
    STRATEGY_VERSION_MISMATCH,
    DATASET_MISMATCH,
    EVALUATION_MISMATCH,
    SCHEMA_VERSION_UNSUPPORTED,
    INVALID_SHADOW_WINDOW,
    AUTHORIZATION_BOUNDARY_INVALID,
    SIDE_EFFECT_POLICY_VIOLATION,
    ADMISSION_NOT_TRADING_AUTHORIZATION,
    REQUIRED_FACT_MISSING
}

/** 调用方申请的未来 Shadow Run 绑定；它只是输入，不产生任何运行或持久化副作用。 */
record RequestedShadowBindingPrototype(
        String actionId,
        String traceId,
        String publishRecordId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        Instant windowStart,
        Instant windowEnd,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunSideEffectPolicyPrototype sideEffectPolicy
) {
}
