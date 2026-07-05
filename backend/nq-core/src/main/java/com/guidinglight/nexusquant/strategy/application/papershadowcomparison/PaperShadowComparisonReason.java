package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.util.Objects;

/**
 * PaperShadowComparisonReason 是 blocker / warning 的稳定原因项。
 *
 * <p>Why: API response 需要把 fail-closed 原因结构化，避免前端从自然语言中推断状态。
 * severity 仅用于展示优先级，不代表风险放行或执行授权。
 */
public record PaperShadowComparisonReason(String code, String severity, String message) {
    public PaperShadowComparisonReason {
        code = Objects.requireNonNull(code, "code must not be null").trim();
        severity = Objects.requireNonNull(severity, "severity must not be null").trim();
        message = Objects.requireNonNull(message, "message must not be null").trim();
    }
}
