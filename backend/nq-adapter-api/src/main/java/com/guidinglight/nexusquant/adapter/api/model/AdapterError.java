package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterError 统一描述 adapter-api 的错误语义。
 * <p>
 * Why:
 * GateC-0 需要先冻结“内部统一语义”，这样 OKX/Binance/PAPER 的拒单、撤单失败、
 * 或订阅失败都能复用同一错误结构，而不是把交易所原始字段泄漏到 core。
 *
 * @param code      统一错误码，例如 REJECTED/ADAPTER_CALL_FAILED
 * @param message   可审计的错误说明
 * @param retryable 是否允许上层按策略决定后续补偿；GateC-0 不在这里做盲重试
 */
public record AdapterError(
        String code,
        String message,
        boolean retryable
) {
}
