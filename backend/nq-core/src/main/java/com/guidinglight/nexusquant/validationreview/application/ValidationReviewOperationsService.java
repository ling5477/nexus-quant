package com.guidinglight.nexusquant.validationreview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;
import com.guidinglight.nexusquant.validationreview.domain.port.ValidationReviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GateV-2 operator review query 与 lifecycle application service。
 *
 * <p>职责限定为本地 validation review facts：查询始终把 tenant/owner scope 下推到 SQL；写侧复用
 * GateV-1 transition service/state machine/repository，并在同一事务追加 accepted operational audit。
 * 本 service 不创建 case，不访问 Strategy/Paper/Shadow/Risk/Account/Order/Ledger 或外部 provider。
 */
@Service
public class ValidationReviewOperationsService {

    private static final int EVENT_LIMIT = ValidationReviewCaseQuery.MAX_LIMIT;

    private final ValidationReviewRepository repository;
    private final ValidationReviewTransitionService transitionService;
    private final AuditLogRepository auditLogRepository;
    private final ValidationReviewOperationalAuditService rejectedAuditService;
    private final ObjectMapper objectMapper;
    private final ValidationReviewRequestHasher requestHasher;
    private final Clock clock;

    /**
     * 创建 production service，时间固定使用 UTC system clock。
     *
     * @param repository tenant/owner scoped durable review repository
     * @param transitionService GateV-1 transition application boundary
     * @param auditLogRepository accepted transition 的既有 audit port
     * @param rejectedAuditService rejected attempt 的独立事务 audit writer
     * @param objectMapper canonical JSON 与 event metadata mapper
     */
    @Autowired
    public ValidationReviewOperationsService(
            ValidationReviewRepository repository,
            ValidationReviewTransitionService transitionService,
            AuditLogRepository auditLogRepository,
            ValidationReviewOperationalAuditService rejectedAuditService,
            ObjectMapper objectMapper
    ) {
        this(
                repository,
                transitionService,
                auditLogRepository,
                rejectedAuditService,
                objectMapper,
                Clock.systemUTC()
        );
    }

    ValidationReviewOperationsService(
            ValidationReviewRepository repository,
            ValidationReviewTransitionService transitionService,
            AuditLogRepository auditLogRepository,
            ValidationReviewOperationalAuditService rejectedAuditService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.transitionService = Objects.requireNonNull(transitionService, "transitionService must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.rejectedAuditService = Objects.requireNonNull(rejectedAuditService, "rejectedAuditService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.requestHasher = new ValidationReviewRequestHasher(objectMapper);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 按角色执行 bounded list；OPERATOR 不得提交 ownerId，ADMIN 可在 NQ_LOCAL 内筛选 owner。
     *
     * @param actor 服务端认证上下文解析出的 actor
     * @param query 已验证的 bounded filter/offset
     * @return SQL scope 内稳定排序的 case 列表；无写侧
     */
    @Transactional(readOnly = true)
    public List<ValidationReviewCase> listCases(ValidationReviewActor actor, ValidationReviewCaseQuery query) {
        requireReviewRole(actor);
        Objects.requireNonNull(query, "query must not be null");
        if (actor.admin()) {
            return repository.listTenantCases(ValidationReviewCase.LOCAL_TENANT_KEY, query);
        }
        if (query.ownerId() != null) {
            throw forbidden(null, null, "operator must not submit ownerId filter");
        }
        return repository.listOwnedCases(ValidationReviewCase.LOCAL_TENANT_KEY, actor.userId(), query);
    }

    /**
     * 按 OPERATOR owner 或 ADMIN tenant SQL scope 查询详情。
     *
     * @param actor 服务端可信 actor
     * @param caseId path case id
     * @return scope 内 case；无写侧
     * @throws ValidationReviewException case 不存在或 scope 不可见
     */
    @Transactional(readOnly = true)
    public ValidationReviewCase detail(ValidationReviewActor actor, UUID caseId) {
        requireReviewRole(actor);
        Objects.requireNonNull(caseId, "caseId must not be null");
        return findScopedCase(actor, caseId);
    }

    /**
     * 查询稳定顺序 events；先用相同 SQL scope 确认 case 可见，空 event 与 not found 不混淆。
     *
     * @param actor 服务端可信 actor
     * @param caseId path case id
     * @return 最多 100 条 createdAt/id 升序 events；无写侧
     */
    @Transactional(readOnly = true)
    public List<ValidationReviewEvent> events(ValidationReviewActor actor, UUID caseId) {
        requireReviewRole(actor);
        Objects.requireNonNull(caseId, "caseId must not be null");
        findScopedCase(actor, caseId);
        return actor.admin()
                ? repository.listTenantEvents(ValidationReviewCase.LOCAL_TENANT_KEY, caseId, EVENT_LIMIT)
                : repository.listOwnedEvents(
                        ValidationReviewCase.LOCAL_TENANT_KEY,
                        actor.userId(),
                        caseId,
                        EVENT_LIMIT
                );
    }

    /**
     * 执行有限 lifecycle action。
     *
     * <p>accepted case/event/audit 共用本事务；失败在独立 audit 事务写入 allowlisted metadata，
     * 不追加 lifecycle event，也不吞原始 domain/DB 异常。
     *
     * @param actor 服务端认证上下文解析出的 actor
     * @param caseId path case id
     * @param action allowlisted lifecycle action
     * @param expectedVersion 必填 optimistic version
     * @param reason 必填人工原因；规范化并执行敏感 marker 检查
     * @param metadata 可空 JSON object；递归执行敏感字段和值检查
     * @param idempotencyKey {@code Idempotency-Key} header 值
     * @param requestId 服务端生成 request id
     * @param traceId 服务端 trace context id
     * @return accepted result，或相同 key/hash 首次 event 对应 snapshot
     * @throws ValidationReviewException RBAC、scope、版本、状态机或幂等冲突
     * @throws IllegalArgumentException payload、hash 输入或敏感数据不合法
     */
    @Transactional
    public ValidationReviewTransitionResult transition(
            ValidationReviewActor actor,
            UUID caseId,
            ValidationReviewAction action,
            Long expectedVersion,
            String reason,
            JsonNode metadata,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        try {
            requireReviewRole(actor);
            requireText(requestId, "requestId", 128);
            requireText(traceId, "traceId", 128);
            if (expectedVersion == null) {
                throw new IllegalArgumentException("expectedVersion is required");
            }
            ValidationReviewCase current = findScopedCase(actor, caseId);
            ValidationReviewRequestHasher.CanonicalRequest canonical = requestHasher.canonicalize(
                    caseId,
                    action,
                    expectedVersion,
                    reason,
                    metadata
            );
            ObjectNode eventMetadata = objectMapper.createObjectNode();
            eventMetadata.put("reason", canonical.reason());
            eventMetadata.set("metadata", canonical.metadata());
            ValidationReviewTransitionCommand command = new ValidationReviewTransitionCommand(
                    caseId,
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    current.ownerId(),
                    action.targetState(),
                    expectedVersion,
                    actor.userId(),
                    idempotencyKey,
                    canonical.requestHash(),
                    requestId,
                    traceId,
                    eventMetadata,
                    Instant.now(clock)
            );
            ValidationReviewTransitionResult result = actor.admin()
                    ? transitionService.transitionAsAdmin(command)
                    : transitionService.transitionOwned(command);
            if (!result.idempotentReplay()) {
                auditLogRepository.append(
                        ValidationReviewOperationalAuditService.AUDIT_DOMAIN,
                        "VALIDATION_REVIEW_ACCEPTED",
                        Long.toString(actor.userId()),
                        traceId,
                        ValidationReviewOperationalAuditService.metadata(
                                caseId,
                                action,
                                result.event().fromState(),
                                result.event().toState(),
                                actor.userId(),
                                requestId,
                                traceId,
                                null
                        )
                );
            }
            return result;
        } catch (ValidationReviewException ex) {
            rejectedAuditService.recordRejected(
                    caseId,
                    action,
                    ex.fromState(),
                    action.targetState(),
                    actor.userId(),
                    requestId,
                    traceId,
                    ex.errorCode()
            );
            throw ex;
        } catch (IllegalArgumentException ex) {
            rejectedAuditService.recordRejected(
                    caseId,
                    action,
                    null,
                    action.targetState(),
                    actor.userId(),
                    requestId,
                    traceId,
                    "REVIEW_REQUEST_INVALID"
            );
            throw ex;
        }
    }

    private ValidationReviewCase findScopedCase(ValidationReviewActor actor, UUID caseId) {
        return (actor.admin()
                ? repository.findTenantCase(ValidationReviewCase.LOCAL_TENANT_KEY, caseId)
                : repository.findOwnedCase(ValidationReviewCase.LOCAL_TENANT_KEY, actor.userId(), caseId))
                .orElseThrow(() -> new ValidationReviewException(
                        "REVIEW_CASE_NOT_FOUND",
                        "validation review case was not found in the requested scope",
                        caseId,
                        null,
                        null
                ));
    }

    private static void requireReviewRole(ValidationReviewActor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.admin() && !actor.operator()) {
            throw forbidden(null, null, "review role is required");
        }
    }

    private static ValidationReviewException forbidden(
            UUID caseId,
            com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState target,
            String message
    ) {
        return new ValidationReviewException("REVIEW_ACTION_FORBIDDEN", message, caseId, null, target);
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        ValidationReviewCase.requireText(value, fieldName, maxLength);
    }
}
