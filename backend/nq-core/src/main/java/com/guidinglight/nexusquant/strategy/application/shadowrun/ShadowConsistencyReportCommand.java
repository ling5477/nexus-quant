package com.guidinglight.nexusquant.strategy.application.shadowrun;

import java.util.Objects;
import java.util.UUID;

/**
 * Shadow consistency report 生成命令。
 *
 * <p>职责：聚合一次 Paper vs Shadow 只读比较所需输入。Why：GateR-5 service 只消费调用方
 * 提供的本地摘要，不外联、不读取 credential、不查询真实账户或真实订单；缺失输入由 service
 * 写入 NOT_COMPARABLE / PARTIAL / FAILED report，而不是补造成功态。
 *
 * @param shadowRunId              要写入 report 的本地 Shadow Run id
 * @param paperInput               Paper 侧只读摘要；可空，缺失时 NOT_COMPARABLE
 * @param shadowInput              Shadow 侧只读摘要；可空，缺失时 NOT_COMPARABLE
 * @param threshold                比较阈值；为空时使用严格阈值
 * @param requestId                调用方 request id；仅用于本地 audit event
 * @param traceId                  全链路 trace id；写入 report 和 audit event
 * @param comparisonFailureCode    可选：调用方明确报告比较过程失败时使用
 * @param comparisonFailureMessage 可选：失败说明；只写脱敏本地原因
 */
public record ShadowConsistencyReportCommand(
        UUID shadowRunId,
        PaperRunComparisonInput paperInput,
        ShadowRunComparisonInput shadowInput,
        ConsistencyThreshold threshold,
        String requestId,
        String traceId,
        String comparisonFailureCode,
        String comparisonFailureMessage
) {

    public ShadowConsistencyReportCommand {
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        threshold = threshold == null ? ConsistencyThreshold.strict() : threshold;
        requestId = StrategyDecisionTrace.requireText(requestId, "requestId");
        traceId = StrategyDecisionTrace.requireText(traceId, "traceId");
        comparisonFailureCode = PaperRunComparisonInput.trimToNull(comparisonFailureCode);
        comparisonFailureMessage = PaperRunComparisonInput.trimToNull(comparisonFailureMessage);
    }

    /**
     * 判断调用方是否明确报告本地比较失败；该路径会生成 FAILED report，不吞掉持久化异常。
     */
    boolean hasComparisonFailure() {
        return comparisonFailureCode != null;
    }
}
