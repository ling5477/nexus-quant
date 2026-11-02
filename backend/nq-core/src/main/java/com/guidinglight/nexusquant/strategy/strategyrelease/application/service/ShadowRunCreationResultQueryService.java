package com.guidinglight.nexusquant.strategy.strategyrelease.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.ShadowRunCreationEvidenceException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.ShadowRunMaterializationAuthorizationException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationPlan;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 从原创建主事实和唯一 CREATED 审计恢复命令结果，不重新评估发布准入。
 *
 * <p>只允许原操作人读取自己的命令，管理员也不能借编号跨操作人读取。主事实和审计采用同一
 * 只读快照；查询不生成回执表、审计、运行实例、订单或账务，404 也不能证明原命令从未执行。
 */
@Service
public class ShadowRunCreationResultQueryService {

    private static final List<String> NO_SIDE_EFFECT_FLAGS =
            List.of(
                    "noOrderSubmission",
                    "noCredentialAccess",
                    "noPrivateEndpoint",
                    "noLedgerMutation",
                    "noAccountMutation",
                    "noExternalPrivateIo");

    private final ShadowRunFactRepository repository;

    public ShadowRunCreationResultQueryService(ShadowRunFactRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 按原发布和原编号查询；审计不完整时明确不可用，禁止重新生成编号掩盖未知结果。
     *
     * @param actor 服务端当前认证主体，不能采用客户端传入的操作人或角色
     * @return 当前快照未查到或不属于调用人时为空；存在但无法核实的证据抛不可用异常
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Optional<ShadowRunCreationResult> query(
            String publishRecordId, String commandIdentity, ShadowRunMaterializationActor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.canMaterialize()) {
            throw new ShadowRunMaterializationAuthorizationException();
        }
        if (publishRecordId == null
                || publishRecordId.isBlank()
                || publishRecordId.length() > 128) {
            throw new IllegalArgumentException("publishRecordId must be 1..128 characters");
        }
        // 与原 POST 的原始 header 校验一致，不允许控制字符经 trim 后变成另一个合法编号。
        if (commandIdentity != null && commandIdentity.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not contain control characters");
        }
        String key = ShadowRunCreationPlan.materializationIdempotencyKey(commandIdentity);
        ShadowRun run = repository.findByIdempotencyKey(key).orElse(null);
        if (run == null || !publishRecordId.equals(run.publishId())) {
            return Optional.empty();
        }
        List<ShadowRunEvent> events = repository.listCreationEvents(run.id());
        if (events == null || events.size() != 1) {
            throw new ShadowRunCreationEvidenceException();
        }
        ShadowRunEvent event = events.getFirst();
        JsonNode actorId = event.metadata().get("actorId");
        if (actorId == null
                || !actorId.isIntegralNumber()
                || !actorId.canConvertToLong()
                || actorId.longValue() <= 0) {
            throw new ShadowRunCreationEvidenceException();
        }
        if (actorId.longValue() != actor.actorId()) {
            return Optional.empty();
        }
        requireCreationEvidence(run, event, key);
        return Optional.of(
                new ShadowRunCreationResult(
                        run.id(),
                        run.publishId(),
                        run.artifactDigest(),
                        run.releaseBindingMode(),
                        ShadowRunStatus.CREATED,
                        run.status(),
                        run.createdAt(),
                        run.updatedAt()));
    }

    private static void requireCreationEvidence(ShadowRun run, ShadowRunEvent event, String key) {
        JsonNode metadata = event.metadata();
        JsonNode policy = run.sideEffectPolicy();
        boolean valid =
                key.equals(run.idempotencyKey())
                        && key.equals(run.requestId())
                        && run.releaseBindingMode() == ShadowRunReleaseBindingMode.RELEASE_BOUND
                        && (run.authorizationBoundary()
                                        == ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                                || run.authorizationBoundary()
                                        == ShadowRunAuthorizationBoundary.REVIEW_ONLY)
                        && event.shadowRunId().equals(run.id())
                        && event.eventType() == ShadowRunEventType.CREATED
                        && event.fromStatus() == null
                        && event.toStatus() == ShadowRunStatus.CREATED
                        && "RELEASE_TO_SHADOW_MATERIALIZED".equals(event.reasonCode())
                        && key.equals(event.requestId())
                        && run.traceId().equals(event.traceId())
                        && run.createdAt().equals(event.createdAt())
                        && textEquals(metadata, "publishRecordId", run.publishId())
                        && textEquals(metadata, "releaseBindingMode", "RELEASE_BOUND")
                        && textEquals(metadata, "idempotencyIdentity", key)
                        && booleanEquals(metadata, "materializationOnly", true)
                        && booleanEquals(metadata, "shadowRunStarted", false)
                        && booleanEquals(metadata, "tradingAuthorized", false)
                        && textEquals(
                                metadata,
                                "manifestSchemaVersion",
                                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION)
                        && textEquals(
                                policy,
                                "manifestSchemaVersion",
                                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION)
                        && sameRequiredText(metadata, policy, "inputReference")
                        && sameRequiredText(metadata, policy, "provenanceReference")
                        && NO_SIDE_EFFECT_FLAGS.stream()
                                .allMatch(flag -> booleanEquals(policy, flag, true));
        if (!valid) {
            throw new ShadowRunCreationEvidenceException();
        }
    }

    private static boolean sameRequiredText(JsonNode first, JsonNode second, String field) {
        JsonNode value = first.get(field);
        return value != null
                && value.isTextual()
                && !value.textValue().isBlank()
                && textEquals(second, field, value.textValue());
    }

    private static boolean textEquals(JsonNode object, String field, String expected) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() && Objects.equals(expected, value.textValue());
    }

    private static boolean booleanEquals(JsonNode object, String field, boolean expected) {
        JsonNode value = object.get(field);
        return value != null && value.isBoolean() && value.booleanValue() == expected;
    }
}
