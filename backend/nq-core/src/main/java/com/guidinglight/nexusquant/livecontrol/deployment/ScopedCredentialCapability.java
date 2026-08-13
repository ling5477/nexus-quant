package com.guidinglight.nexusquant.livecontrol.deployment;

/**
 * Scoped credential capability taxonomy。
 *
 * <p>只有 PRIVATE_READONLY_DIAGNOSTIC 可用于当前 read-only diagnostic runtime；FUTURE_MICRO_LIVE 仅作为未来
 * deployment contract 的不可调用占位，FORBIDDEN 覆盖提现、转账、资金划拨等永久禁止能力。</p>
 */
public enum ScopedCredentialCapability {
    PRIVATE_READONLY_DIAGNOSTIC(true),
    FUTURE_MICRO_LIVE(false),
    FORBIDDEN(false);

    private final boolean privateReadonlyDiagnosticCallable;

    ScopedCredentialCapability(boolean privateReadonlyDiagnosticCallable) {
        this.privateReadonlyDiagnosticCallable = privateReadonlyDiagnosticCallable;
    }

    public boolean privateReadonlyDiagnosticCallable() {
        return privateReadonlyDiagnosticCallable;
    }
}
