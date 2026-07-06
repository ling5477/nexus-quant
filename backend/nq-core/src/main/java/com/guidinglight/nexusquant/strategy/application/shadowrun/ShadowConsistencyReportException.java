package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Shadow consistency report 生成失败异常。
 *
 * <p>该异常只表示本地 comparison / persistence 编排失败，不代表交易失败、订单失败或 LIVE
 * 状态变化。持久化失败不会被吞掉，调用方应按普通本地服务异常处理。
 */
public class ShadowConsistencyReportException extends RuntimeException {

    public ShadowConsistencyReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
