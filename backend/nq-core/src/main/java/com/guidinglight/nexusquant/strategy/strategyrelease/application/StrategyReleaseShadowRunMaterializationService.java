package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision.Decision;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 从 publishRecordId 重新执行 server-owned admission 并物化一个未启动 Shadow Run。
 *
 * <p>本 service 不接受 digest、strategy/dataset/evaluation、validation、policy、path、storage key 或 plan；
 * 这些 provenance 全部来自与 GET preview 共用的 {@link StrategyReleaseAdmissionPreviewService} orchestration。
 */
@Service
public class StrategyReleaseShadowRunMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyReleaseShadowRunMaterializationService.class
    );

    private final AdmissionEvaluationSource admissionEvaluationSource;
    private final MaterializationSink materializationSink;

    @Autowired
    public StrategyReleaseShadowRunMaterializationService(
            StrategyReleaseAdmissionPreviewService admissionEvaluationService,
            ShadowRunMaterializationWriter writer
    ) {
        this(
                Objects.requireNonNull(admissionEvaluationService, "admissionEvaluationService must not be null")::evaluate,
                Objects.requireNonNull(writer, "writer must not be null")::materialize
        );
    }

    StrategyReleaseShadowRunMaterializationService(
            AdmissionEvaluationSource admissionEvaluationSource,
            MaterializationSink materializationSink
    ) {
        this.admissionEvaluationSource = Objects.requireNonNull(
                admissionEvaluationSource,
                "admissionEvaluationSource must not be null"
        );
        this.materializationSink = Objects.requireNonNull(materializationSink, "materializationSink must not be null");
    }

    /**
     * 重新验证 release/artifact/validation/policy 后执行 CREATE-only materialization。
     *
     * @param publishRecordId 唯一客户端业务事实
     * @param commandIdentity 标准 Idempotency-Key；区分 retry 与 legitimate rerun
     * @param actor 服务端 authentication/profile actor
     * @param traceId 当前服务端 trace id
     * @return publish 不存在时 empty；BLOCKED admission 抛拒绝异常且零写入
     */
    public Optional<ShadowRunMaterializationResult> materialize(
            String publishRecordId,
            String commandIdentity,
            ShadowRunMaterializationActor actor,
            String traceId
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.canMaterialize()) {
            throw new ShadowRunMaterializationAuthorizationException();
        }
        if (commandIdentity == null || commandIdentity.isBlank()
                || commandIdentity.trim().length() > 128
                || commandIdentity.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 1..128 characters without control characters"
            );
        }
        StrategyReleaseAdmissionPreviewService.AdmissionEvaluation evaluation =
                admissionEvaluationSource.evaluate(publishRecordId, traceId).orElse(null);
        if (evaluation == null) {
            return Optional.empty();
        }
        ReleaseToShadowAdmissionDecision admission = evaluation.admission();
        if (admission.decision() != Decision.ELIGIBLE || admission.creationPlan() == null) {
            throw new ShadowRunMaterializationRejectedException(
                    admission.reasonCodes().stream().map(Enum::name).toList()
            );
        }
        if (evaluation.guard() == null) {
            throw new AdmissionStaleException();
        }

        ShadowRunCreationPlan materializationPlan = admission.creationPlan()
                .bindMaterializationCommand(commandIdentity);
        ShadowRunMaterializationResult result = materializationSink.materialize(
                materializationPlan,
                evaluation.guard(),
                actor.actorId()
        );
        log.info(
                "shadow release materialization completed, publishRecordId={}, shadowRunId={}, status={}, idempotentReplay={}, traceId={}",
                result.publishRecordId(),
                result.shadowRunId(),
                result.status(),
                result.idempotentReplay(),
                traceId
        );
        return Optional.of(result);
    }

    @FunctionalInterface
    interface AdmissionEvaluationSource {
        Optional<StrategyReleaseAdmissionPreviewService.AdmissionEvaluation> evaluate(
                String publishRecordId,
                String traceId
        );
    }

    @FunctionalInterface
    interface MaterializationSink {
        ShadowRunMaterializationResult materialize(ShadowRunCreationPlan plan, AdmissionGuard guard, long actorId);
    }
}
