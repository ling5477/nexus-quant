package com.guidinglight.nexusquant.validationreview.domain.port;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable validation review fact model 的 repository port。
 *
 * <p>所有查询必须在 SQL 层带 tenant/owner scope；port 不允许访问交易所、credential、Paper、Shadow、
 * risk、account、order 或 ledger 写侧。
 */
public interface ValidationReviewRepository {

    /** 创建本地 OPEN case；本轮不提供自动 materialization 或 HTTP 入口。 */
    ValidationReviewCase createCase(ValidationReviewCase reviewCase);

    /** 按 tenant + owner + case id 查询 OPERATOR scope。 */
    Optional<ValidationReviewCase> findOwnedCase(String tenantKey, long ownerId, UUID reviewCaseId);

    /** 按 tenant + case id 查询 ADMIN scope；不跨 tenant。 */
    Optional<ValidationReviewCase> findTenantCase(String tenantKey, UUID reviewCaseId);

    /** 按 tenant + owner + filter + offset bounded 查询 OPERATOR case 列表。 */
    List<ValidationReviewCase> listOwnedCases(
            String tenantKey,
            long ownerId,
            ValidationReviewCaseQuery query
    );

    /** 按 tenant + optional owner + filter + offset bounded 查询 ADMIN case 列表。 */
    List<ValidationReviewCase> listTenantCases(String tenantKey, ValidationReviewCaseQuery query);

    /** GateV-1 compatibility helper；新 API 使用 query overload。 */
    default List<ValidationReviewCase> listOwnedCases(String tenantKey, long ownerId, int limit) {
        return listOwnedCases(tenantKey, ownerId, new ValidationReviewCaseQuery(null, null, null, limit, 0));
    }

    /** GateV-1 compatibility helper；新 API 使用 query overload。 */
    default List<ValidationReviewCase> listTenantCases(String tenantKey, int limit) {
        return listTenantCases(tenantKey, new ValidationReviewCaseQuery(null, null, null, limit, 0));
    }

    /** 按 tenant + owner + case 查询稳定顺序的 append-only events。 */
    List<ValidationReviewEvent> listOwnedEvents(String tenantKey, long ownerId, UUID reviewCaseId, int limit);

    /** 按 tenant + case 查询 ADMIN scope 的稳定顺序 events。 */
    List<ValidationReviewEvent> listTenantEvents(String tenantKey, UUID reviewCaseId, int limit);

    /** 按 tenant + owner + case + idempotency key 查询首次 accepted event。 */
    Optional<ValidationReviewEvent> findOwnedEventByIdempotencyKey(
            String tenantKey,
            long ownerId,
            UUID reviewCaseId,
            String idempotencyKey
    );

    /** 按 tenant + case + idempotency key 查询 ADMIN scope 的首次 accepted event。 */
    Optional<ValidationReviewEvent> findTenantEventByIdempotencyKey(
            String tenantKey,
            UUID reviewCaseId,
            String idempotencyKey
    );

    /**
     * 在单事务中执行 OPERATOR owned transition 与 event append。
     */
    ValidationReviewTransitionResult transitionOwned(ValidationReviewTransitionCommand command);

    /**
     * 在单事务中执行 ADMIN same-tenant transition 与 event append。
     */
    ValidationReviewTransitionResult transitionInTenant(ValidationReviewTransitionCommand command);
}
