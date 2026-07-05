package com.guidinglight.nexusquant.integration.dh;

import java.util.List;

/**
 * DhDryRunResponseEnvelope 表示 DH dry-run response 的只读 envelope。
 *
 * <p>Why: NQ 侧必须先解析并验证 envelope，再把结果作为 dry-run record 保存。任何 error envelope、
 * schema mismatch、非 dryRun、交易动作或可执行字段都只能 fail-closed，不能进入执行链路。</p>
 *
 * @param decisionId    DH decision id；成功响应必填
 * @param dryRun        必须为 true
 * @param action        只允许 OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS
 * @param confidence    置信度，成功响应要求为 0..1
 * @param riskLevel     风险等级摘要
 * @param reasons       决策理由摘要
 * @param traceSummary  trace 摘要
 * @param replayRef     replay reference
 * @param auditRef      audit reference
 * @param schemaVersion schema version；必须匹配 request
 * @param error         error envelope；存在时必须 fail-closed
 */
public record DhDryRunResponseEnvelope(
        String decisionId,
        boolean dryRun,
        String action,
        Double confidence,
        String riskLevel,
        List<String> reasons,
        String traceSummary,
        String replayRef,
        String auditRef,
        String schemaVersion,
        ErrorEnvelope error) {

    /**
     * ErrorEnvelope 表示 DH policy/security/provider/client 错误。
     *
     * @param code    stable error code；未知 code 映射 UNKNOWN_ERROR
     * @param message 安全错误摘要；不得携带 credential 或 raw provider payload
     */
    public record ErrorEnvelope(String code, String message) {
    }
}
