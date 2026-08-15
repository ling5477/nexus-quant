package com.guidinglight.nexusquant.account.domain;

import java.util.Locale;

/**
 * credential permission probe 的强类型权限预期。
 *
 * <p>GateW 诊断只接受只读 key；GateY pilot readiness 要求 READ + TRADE，同时明确拒绝
 * WITHDRAW。外部 mode 只能映射到这里列出的策略，未知值必须在 credential 访问前拒绝。</p>
 */
public enum CredentialPermissionExpectation {
    READ_ONLY_DIAGNOSTIC,
    GATEY_PILOT_READINESS;

    public static CredentialPermissionExpectation fromRequestedMode(String value) {
        if (value == null || value.isBlank()) {
            return READ_ONLY_DIAGNOSTIC;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PAPER", "READ_ONLY_DIAGNOSTIC" -> READ_ONLY_DIAGNOSTIC;
            case "GATEY_PILOT_READINESS" -> GATEY_PILOT_READINESS;
            default -> throw new IllegalArgumentException("unsupported credential permission expectation");
        };
    }
}
