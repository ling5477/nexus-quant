package com.guidinglight.nexusquant.api.web;

/**
 * 对外稳定错误键与编号的唯一注册表；既有 code 不承担身份推导职责。
 */
public enum ApiErrorIdentity {
    ORDER_VERSION_CONFLICT("NQ-TRD-1001");

    private final String errorId;

    ApiErrorIdentity(String errorId) {
        this.errorId = errorId;
    }

    public String errorId() {
        return errorId;
    }

    public String errorKey() {
        return name();
    }
}
