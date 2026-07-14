package com.guidinglight.nexusquant.trading.application.orderpreview;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * DryRunOrderPreviewResult 分离结构、venue facts、risk、account 与 execution readiness。
 *
 * <p>该结果是 diagnostic-only；构造器强制无副作用、不提交订单且 execution readiness 恒为 BLOCKED。
 * unknown/not-evaluated 不得被调用方折叠成 PASS。</p>
 *
 * @param structuralStatus   输入与纯结构检查状态
 * @param venueFactStatus    local venue facts 状态
 * @param riskStatus         stateful risk 状态
 * @param accountStatus      account/permission 状态
 * @param executionReadiness 执行就绪状态，恒为 BLOCKED
 * @param diagnosticOnly     恒为 true
 * @param noSideEffect       恒为 true
 * @param orderSubmitted     恒为 false
 * @param grossNotional      price * quantity；输入为正时提供，不包含 fee
 * @param blockers           阻断分类
 * @param warnings           警告分类
 * @param unknowns           未知分类
 * @param notEvaluated       未评估分类
 */
public record DryRunOrderPreviewResult(
        OrderPreviewStatus structuralStatus,
        OrderPreviewStatus venueFactStatus,
        OrderPreviewStatus riskStatus,
        OrderPreviewStatus accountStatus,
        OrderPreviewStatus executionReadiness,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean orderSubmitted,
        BigDecimal grossNotional,
        List<OrderPreviewFindingCode> blockers,
        List<OrderPreviewFindingCode> warnings,
        List<OrderPreviewFindingCode> unknowns,
        List<OrderPreviewFindingCode> notEvaluated
) {

    public DryRunOrderPreviewResult {
        Objects.requireNonNull(structuralStatus, "structuralStatus must not be null");
        Objects.requireNonNull(venueFactStatus, "venueFactStatus must not be null");
        Objects.requireNonNull(riskStatus, "riskStatus must not be null");
        Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        if (executionReadiness != OrderPreviewStatus.BLOCKED) {
            throw new IllegalArgumentException("executionReadiness must remain BLOCKED");
        }
        if (!diagnosticOnly || !noSideEffect || orderSubmitted) {
            throw new IllegalArgumentException("preview safety flags are immutable");
        }
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
        unknowns = List.copyOf(unknowns);
        notEvaluated = List.copyOf(notEvaluated);
    }
}
