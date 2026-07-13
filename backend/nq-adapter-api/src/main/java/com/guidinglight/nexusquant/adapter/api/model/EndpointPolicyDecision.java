package com.guidinglight.nexusquant.adapter.api.model;

import java.util.Objects;

/**
 * EndpointPolicyDecision 是无副作用 endpoint guard 的结果。
 *
 * <p>GateW-1 中 {@link #tradingAuthorization()} 永远为 {@code false}。公开读取被 policy 放行，
 * 只表示可以交给既有 public transport 继续处理，绝不表示账户、订单或 LIVE 交易被授权。</p>
 *
 * @param allowed                是否允许进入后续 transport
 * @param capability             本次声明的能力；不可为空
 * @param endpointAccessClass    endpoint 副作用分类；不可为空
 * @param reason                 脱敏决策原因；不可为空
 * @param tradingAuthorization   固定为 false，防止 contract 被误用为交易授权
 */
public record EndpointPolicyDecision(
        boolean allowed,
        ExchangeCapability capability,
        EndpointAccessClass endpointAccessClass,
        EndpointGuardReason reason,
        boolean tradingAuthorization
) {

    public EndpointPolicyDecision {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(endpointAccessClass, "endpointAccessClass must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        // GateW capability/guard 合同从不授予 trading authorization。
        tradingAuthorization = false;
    }

    public static EndpointPolicyDecision allowPublicRead(ExchangeCapability capability) {
        return new EndpointPolicyDecision(
                true,
                capability,
                EndpointAccessClass.PUBLIC_READ,
                EndpointGuardReason.ALLOW_PUBLIC_READ,
                false
        );
    }

    /**
     * 仅允许已类型化、已精确匹配的 private read-only operation 进入专用 transport。
     * 该决策固定不构成交易授权。
     */
    public static EndpointPolicyDecision allowPrivateReadOnly(ExchangeCapability capability) {
        return new EndpointPolicyDecision(
                true,
                capability,
                EndpointAccessClass.PRIVATE_READ_ONLY,
                EndpointGuardReason.ALLOW_PRIVATE_READ_ONLY,
                false
        );
    }

    public static EndpointPolicyDecision deny(
            ExchangeCapability capability,
            EndpointAccessClass endpointAccessClass,
            EndpointGuardReason reason
    ) {
        return new EndpointPolicyDecision(false, capability, endpointAccessClass, reason, false);
    }
}
