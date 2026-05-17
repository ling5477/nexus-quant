package com.guidinglight.nexusquant.adapter.api.service;

/**
 * HistoricalKlineAdapterException 表示交易所历史 K 线读取失败。
 * <p>
 * Why:
 * OKX / Binance 的错误码格式不同，对上层只暴露明确的 adapter 失败摘要，避免吞掉外部接口失败原因。
 */
public class HistoricalKlineAdapterException extends RuntimeException {

    public HistoricalKlineAdapterException(String message) {
        super(message);
    }

    public HistoricalKlineAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
