package com.guidinglight.nexusquant.adapter.api.model;

/**
 * EndpointGuardReason 是 endpoint policy 的脱敏、可审计决策原因。
 *
 * <p>原因码不得携带 URL query、credential、签名、响应体或账户信息。</p>
 */
public enum EndpointGuardReason {

    ALLOW_PUBLIC_READ,
    DENY_PRIVATE_RUNTIME_DISABLED,
    DENY_MUTATING_ENDPOINT,
    DENY_FUNDS_MOVEMENT,
    DENY_UNKNOWN_ENDPOINT,
    DENY_CREDENTIAL_ACCESS_DISABLED,
    DENY_LIVE_DISABLED
}
