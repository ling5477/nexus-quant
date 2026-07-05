package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.util.Objects;

/**
 * PaperShadowComparisonEvidence 描述 Paper vs Shadow 只读对照所需证据的单项状态。
 *
 * <p>Why: 前端和审计需要稳定识别缺失证据，但证据项只能描述本地 fact-source 是否存在，
 * 不能携带交易指令、敏感材料或任何真实执行放行字段。
 */
public record PaperShadowComparisonEvidence(String code, String status, String message) {
    public PaperShadowComparisonEvidence {
        code = Objects.requireNonNull(code, "code must not be null").trim();
        status = Objects.requireNonNull(status, "status must not be null").trim();
        message = Objects.requireNonNull(message, "message must not be null").trim();
    }
}
