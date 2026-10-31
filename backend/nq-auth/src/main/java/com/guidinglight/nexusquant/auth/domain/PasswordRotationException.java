package com.guidinglight.nexusquant.auth.domain;

/** 拒绝原因不携带用户名、密码或 hash，维护入口只输出此稳定错误码。 */
public final class PasswordRotationException extends RuntimeException {
    public enum Reason {
        INVALID_ROTATION_REQUEST, USER_NOT_FOUND, USERNAME_MISMATCH, USER_DISABLED,
        STALE_AUTH_IDENTITY, UNSUPPORTED_PASSWORD_HASH, PASSWORD_UNCHANGED,
        UNEXPECTED_AFFECTED_ROWS, PASSWORD_STORAGE_FAILURE
    }

    private final Reason reason;

    public PasswordRotationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public PasswordRotationException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
