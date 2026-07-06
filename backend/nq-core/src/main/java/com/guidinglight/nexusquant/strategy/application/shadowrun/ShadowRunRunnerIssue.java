package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Shadow Run runner 的阻断或告警原因。
 *
 * <p>该 record 只保存本地诊断 code / message，不保存 credential、private payload、真实订单 ID、
 * 真实账户余额或交易授权语义。调用方传入的 issue 会被写入 Shadow Run blockers JSONB。
 */
public record ShadowRunRunnerIssue(String code, String message) {

    public ShadowRunRunnerIssue {
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("runner issue " + fieldName + " must not be blank");
        }
        return value.trim();
    }
}
