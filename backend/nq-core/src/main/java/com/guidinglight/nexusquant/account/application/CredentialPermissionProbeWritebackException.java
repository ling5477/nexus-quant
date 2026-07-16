package com.guidinglight.nexusquant.account.application;

/**
 * permission probe 最终写回的脱敏失败分类。
 *
 * <p>异常只携带有限枚举，不保留 JDBC、SQL、provider body 或 credential 相关 cause/message；
 * 外层事务据此整体回滚 metadata 与 audit，调用方必须把本次 probe 视为 BLOCKED。</p>
 */
final class CredentialPermissionProbeWritebackException extends IllegalStateException {

    static final String ATOMIC_WRITEBACK_FAILED = "ATOMIC_WRITEBACK_FAILED";
    static final String VERSION_CONFLICT = "VERSION_CONFLICT";

    private CredentialPermissionProbeWritebackException(String category) {
        super(category);
    }

    static CredentialPermissionProbeWritebackException atomicWritebackFailed() {
        return new CredentialPermissionProbeWritebackException(ATOMIC_WRITEBACK_FAILED);
    }

    static CredentialPermissionProbeWritebackException versionConflict() {
        return new CredentialPermissionProbeWritebackException(VERSION_CONFLICT);
    }
}
