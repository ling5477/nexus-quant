package com.guidinglight.nexusquant.strategy.application.shadowrun;

import java.util.List;

/**
 * GateR-4 Shadow Run risk preflight 只读快照模型。
 *
 * <p>职责：表达本地风险预检的 allow / block / warn 结果。Why：Shadow Run 需要可复盘的风险
 * 输入和阻断原因，但 GateR-4 仍禁止真实风控放行、真实下单、private endpoint 和账户/ledger mutation。
 *
 * @param allowed           是否允许本地 Shadow Run 继续到 completed；不表示真实交易允许
 * @param blocked           是否阻断本地 Shadow Run；为 true 时 runner 应进入 BLOCKED
 * @param severity          整体严重度，例如 INFO / WARN / BLOCK
 * @param ruleResults       本地规则结果列表
 * @param blockers          阻断原因；会进入 ShadowRunRunnerResult
 * @param warnings          告警原因；会进入 ShadowRunRunnerResult
 * @param requiredNextSteps 后续人工/系统复核步骤；会进入 ShadowRunRunnerResult
 * @param traceId           全链路 trace id
 */
public record RiskPreflightSnapshot(
        boolean allowed,
        boolean blocked,
        String severity,
        List<RiskPreflightRuleResult> ruleResults,
        List<ShadowRunRunnerIssue> blockers,
        List<ShadowRunRunnerIssue> warnings,
        List<String> requiredNextSteps,
        String traceId
) {

    public RiskPreflightSnapshot {
        if (allowed && blocked) {
            throw new IllegalArgumentException("risk preflight cannot be both allowed and blocked");
        }
        severity = StrategyDecisionTrace.requireText(severity, "severity");
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (!allowed && !blocked && warnings.isEmpty()) {
            throw new IllegalArgumentException("risk preflight must express allow, block, or warn");
        }
        requiredNextSteps = StrategyDecisionTrace.copyTextList(requiredNextSteps, "requiredNextSteps");
        traceId = StrategyDecisionTrace.requireText(traceId, "traceId");
    }

    List<ShadowRunRunnerIssue> effectiveBlockers() {
        if (!blocked) {
            return blockers;
        }
        if (!blockers.isEmpty()) {
            return blockers;
        }
        return List.of(new ShadowRunRunnerIssue(
                "RISK_PREFLIGHT_BLOCKED",
                "Risk preflight blocked the local Shadow Run preview."
        ));
    }
}
