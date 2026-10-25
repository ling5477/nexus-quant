package com.guidinglight.nexusquant.validationreview.domain;

/**
 * Validation review case 的 bounded query 条件。
 *
 * <p>tenant 不在查询对象中，必须由 repository 方法的服务端参数提供；ownerId 仅供 ADMIN filter 使用。
 *
 * @param state 可空状态筛选
 * @param severity 可空严重度筛选
 * @param ownerId 可空 ADMIN owner 筛选
 * @param limit 1..100
 * @param offset 0..10000
 */
public record ValidationReviewCaseQuery(
        ValidationReviewState state,
        ValidationReviewSeverity severity,
        Long ownerId,
        int limit,
        int offset
) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_OFFSET = 10_000;

    public ValidationReviewCaseQuery {
        if (ownerId != null) {
            ValidationReviewCase.requirePositive(ownerId, "ownerId");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("offset must be between 0 and " + MAX_OFFSET);
        }
    }
}
