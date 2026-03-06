package com.guidinglight.nexusquant.adapter.binance.service;

/**
 * BinanceApiException 表示 Binance REST 层面的结构化失败。
 *
 * Why:
 * GateC-2 需要把 http status、Binance code/msg、endpoint、trace_id 一次性带齐，
 * 这样后续接入 TradingAdapter 与 reconcile 时才能保留统一排障口径，而不是抛出泛化 RuntimeException。
 */
public class BinanceApiException extends RuntimeException {

    private final int httpStatus;
    private final String endpoint;
    private final String errorCode;
    private final String errorMessage;
    private final String traceId;

    public BinanceApiException(
            String message,
            int httpStatus,
            String endpoint,
            String errorCode,
            String errorMessage,
            String traceId
    ) {
        super(message);
        this.httpStatus = httpStatus;
        this.endpoint = endpoint;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
    }

    public BinanceApiException(
            String message,
            int httpStatus,
            String endpoint,
            String errorCode,
            String errorMessage,
            String traceId,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.endpoint = endpoint;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
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

    public String errorMessage() {
        return errorMessage;
    }

    public String traceId() {
        return traceId;
    }
}
