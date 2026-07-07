package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * ShadowRunReadOnlyNotFoundException 表示 Shadow Run 只读 API 查询不到目标本地事实。
 *
 * <p>该异常只用于 read-only detail / replay 查询面映射 404，不触发创建、启动、重跑、
 * 交易或外部调用。
 */
public class ShadowRunReadOnlyNotFoundException extends RuntimeException {

    public ShadowRunReadOnlyNotFoundException(String message) {
        super(message);
    }
}
