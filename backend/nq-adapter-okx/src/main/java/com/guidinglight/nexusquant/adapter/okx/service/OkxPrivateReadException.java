package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Objects;

/** 不携带 provider body/header/credential 的私有只读异常。 */
public final class OkxPrivateReadException extends RuntimeException {

    private final OkxPrivateReadError category;

    public OkxPrivateReadException(OkxPrivateReadError category) {
        this(category, null);
    }

    public OkxPrivateReadException(OkxPrivateReadError category, Throwable cause) {
        super("OKX private read failed: " + Objects.requireNonNull(category, "category must not be null"), cause);
        this.category = category;
    }

    public OkxPrivateReadError category() {
        return category;
    }
}
