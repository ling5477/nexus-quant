package com.guidinglight.nexusquant.trading.application.preflight;

/**
 * TradingPreflightReason 描述 preflight blocker 或 warning 的稳定机器码与可读说明。
 *
 * <p>Why: 前端和文档需要用明确 reason code 展示阻断原因；message 只解释当前只读事实，
 * 不携带 credential、订单、签名、headers、raw payload 或外部 provider 响应。
 */
public record TradingPreflightReason(
        String code,
        String severity,
        String message
) {
}
