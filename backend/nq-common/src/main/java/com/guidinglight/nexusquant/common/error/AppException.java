package com.guidinglight.nexusquant.common.error;

/**
 * AppException 是跨模块统一的业务异常占位实现。
 *
 * Why:
 * Gate A 只做骨架，但必须固定“错误码 + traceId”这两个最小可追踪字段，
 * 以满足 docs/CONTRACTS.md 与 docs/ARCHITECTURE.md 的可审计要求。
 *
 * How:
 * 通过 ErrorCode 标识错误类型，通过 traceId 关联一次请求或事件链路。
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String traceId;

    /**
     * 构造统一异常。
     *
     * @param errorCode 错误码，表示可机器识别的失败类别
     * @param message 人类可读错误信息
     * @param traceId 链路追踪标识，用于日志与事件串联
     */
    public AppException(ErrorCode errorCode, String message, String traceId) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getTraceId() {
        return traceId;
    }
}
