package com.guidinglight.nexusquant.adapter.binance.fee;

import java.util.Objects;

/** 只公开固定错误类别，不保留供应方原文、签名URI、凭据或底层异常链。 */
public final class BinanceFeeReadException extends RuntimeException {
    private final Reason reason;

    public BinanceFeeReadException(Reason reason) {
        super("BINANCE_FEE_READ_" + Objects.requireNonNull(reason).name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_SOURCE,
        INVALID_JSON,
        RESPONSE_TOO_LARGE,
        UNSUPPORTED_SCHEMA,
        INVALID_RATE,
        INVALID_DISCOUNT,
        SYMBOL_MISMATCH,
        DISCOUNT_NOT_AVAILABLE,
        INVALID_PROVIDER_TIME,
        PRIVATE_SCOPE_DENIED,
        CREDENTIAL_UNAVAILABLE,
        REMOTE_PERMISSION_DENIED,
        SOURCE_UNAVAILABLE,
        ENVIRONMENT_UNSUPPORTED,
        TIMEOUT,
        INTERRUPTED
    }
}
