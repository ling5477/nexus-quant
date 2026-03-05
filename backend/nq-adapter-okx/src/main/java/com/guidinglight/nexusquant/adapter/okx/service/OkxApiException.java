package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxApiException 表示 OKX HTTP 调用或业务响应失败。
 * <p>
 * Why:
 * GateC-1 排障需要看到 http status、endpoint、OKX error code 与 traceId，
 * 因此这里把最小定位信息集中在一个异常模型中，避免上层只能拿到模糊的 `IOException`。
 */
public class OkxApiException extends RuntimeException {

    private final int httpStatus;
    private final String endpoint;
    private final String errorCode;
    private final OkxErrorCode errorKind;
    private final String requestTraceId;

    /**
     * @param message        可读错误说明
     * @param httpStatus     HTTP 状态码；纯客户端错误时可为 0
     * @param endpoint       失败的 endpoint
     * @param errorCode      OKX 业务错误码；未知时可为空
     * @param requestTraceId 本次请求 traceId
     * @param cause          原始异常
     */
    public OkxApiException(
            String message,
            int httpStatus,
            String endpoint,
            String errorCode,
            OkxErrorCode errorKind,
            String requestTraceId,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.endpoint = endpoint;
        this.errorCode = errorCode;
        this.errorKind = errorKind == null ? OkxErrorCode.fromRawCode(errorCode) : errorKind;
        this.requestTraceId = requestTraceId;
    }

    public OkxApiException(
            String message,
            int httpStatus,
            String endpoint,
            String errorCode,
            String requestTraceId,
            Throwable cause
    ) {
        this(message, httpStatus, endpoint, errorCode, OkxErrorCode.fromRawCode(errorCode), requestTraceId, cause);
    }

    public OkxApiException(
            String message,
            int httpStatus,
            String endpoint,
            String errorCode,
            OkxErrorCode errorKind,
            String requestTraceId
    ) {
        this(message, httpStatus, endpoint, errorCode, errorKind, requestTraceId, null);
    }

    public OkxApiException(String message, int httpStatus, String endpoint, String errorCode, String requestTraceId) {
        this(message, httpStatus, endpoint, errorCode, OkxErrorCode.fromRawCode(errorCode), requestTraceId, null);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String endpoint() {
        return endpoint;
    }

    public String errorCode() {
        return errorCode;
    }

    public OkxErrorCode errorKind() {
        return errorKind;
    }

    public String requestTraceId() {
        return requestTraceId;
    }
}
