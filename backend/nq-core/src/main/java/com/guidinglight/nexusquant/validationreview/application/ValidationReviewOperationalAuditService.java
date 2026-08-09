package com.guidinglight.nexusquant.validationreview.application;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validation review rejected attempt 的脱敏 operational audit writer。
 *
 * <p>拒绝行为不能伪造成 lifecycle event；使用独立事务确保业务事务回滚后仍保留最小拒绝证据。
 */
@Service
public class ValidationReviewOperationalAuditService {

    static final String AUDIT_DOMAIN = "VALIDATION_REVIEW";

    private final AuditLogRepository auditLogRepository;

    /** @param auditLogRepository 复用平台既有 audit_logs port */
    public ValidationReviewOperationalAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
    }

    /**
     * 在新事务中记录拒绝结果，只写 allowlisted operational fields。
     *
     * @param caseId path case id
     * @param action 固定 lifecycle action
     * @param fromState 可空已知原状态
     * @param toState 固定目标状态
     * @param actorId 服务端认证 user id
     * @param requestId 服务端 request id
     * @param traceId trace context id
     * @param errorCode 脱敏稳定错误码
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(
            UUID caseId,
            ValidationReviewAction action,
            ValidationReviewState fromState,
            ValidationReviewState toState,
            long actorId,
            String requestId,
            String traceId,
            String errorCode
    ) {
        auditLogRepository.append(
                AUDIT_DOMAIN,
                "VALIDATION_REVIEW_REJECTED",
                Long.toString(actorId),
                traceId,
                metadata(caseId, action, fromState, toState, actorId, requestId, traceId, errorCode)
        );
    }

    static Map<String, Object> metadata(
            UUID caseId,
            ValidationReviewAction action,
            ValidationReviewState fromState,
            ValidationReviewState toState,
            long actorId,
            String requestId,
            String traceId,
            String errorCode
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "caseId", caseId == null ? null : caseId.toString());
        put(values, "action", action == null ? null : action.name());
        put(values, "fromState", fromState == null ? null : fromState.name());
        put(values, "toState", toState == null ? null : toState.name());
        values.put("actorId", actorId);
        put(values, "requestId", requestId);
        put(values, "traceId", traceId);
        put(values, "errorCode", errorCode);
        return Map.copyOf(values);
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            values.put(key, value);
        }
    }
}
