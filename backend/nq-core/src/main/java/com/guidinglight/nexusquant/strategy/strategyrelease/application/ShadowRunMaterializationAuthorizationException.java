package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/** Shadow materialization 写权限缺失；不回显 actor、role 或认证载荷。 */
public class ShadowRunMaterializationAuthorizationException extends RuntimeException {

    public ShadowRunMaterializationAuthorizationException() {
        super("SHADOW_MATERIALIZATION_FORBIDDEN: operator or admin role is required");
    }
}
