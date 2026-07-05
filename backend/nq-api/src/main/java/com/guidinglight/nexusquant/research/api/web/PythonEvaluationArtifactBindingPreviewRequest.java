package com.guidinglight.nexusquant.research.api.web;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * PythonEvaluationArtifactBindingPreviewRequest 是 GateQ-4 binding preview 的 HTTP request DTO。
 *
 * <p>Why: request 只携带 artifact JSON 与 Java 侧 expected anchors。它不接受本地文件路径，
 * 不表示 upload / import / persist，也不会触发策略发布、Paper run 或 Shadow run。
 *
 * @param artifact Python offline evaluation artifact JSON；为空时 service 返回 fail-closed preview
 * @param expectedDatasetId 预期 datasetId
 * @param expectedStrategyVersionId 预期 Java strategyVersionId，仅用于 binding target / traceability
 * @param expectedStrategyVersion 预期 Python strategy_version
 * @param expectedEvaluationVersion 预期 evaluation_version
 * @param expectedChecksum 预期 artifact checksum
 * @param expectedParametersHash 预期 parameters_hash
 * @param source 允许 PYTHON_OFFLINE；其他来源 fail-closed
 * @param dryRun false fail-closed；null 视为 preview endpoint 固有 dry-run
 */
public record PythonEvaluationArtifactBindingPreviewRequest(
        JsonNode artifact,
        String expectedDatasetId,
        String expectedStrategyVersionId,
        String expectedStrategyVersion,
        String expectedEvaluationVersion,
        String expectedChecksum,
        String expectedParametersHash,
        String source,
        Boolean dryRun
) {
}
